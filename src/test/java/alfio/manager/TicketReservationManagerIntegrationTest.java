/**
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.manager;

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.manager.payment.PaymentSpecification;
import alfio.manager.support.PaymentResult;
import alfio.manager.support.reservation.NotEnoughTicketsException;
import alfio.manager.support.reservation.TooManyTicketsForDiscountCodeException;
import alfio.manager.user.UserManager;
import alfio.model.*;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.*;
import alfio.model.transaction.PaymentMethod;
import alfio.model.transaction.PaymentProxy;
import alfio.repository.*;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.AlfioIntegrationTest;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.BaseIntegrationTest;
import alfio.util.ClockProvider;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static alfio.model.system.ConfigurationKeys.AUTOMATIC_REMOVAL_EXPIRED_OFFLINE_PAYMENT;
import static alfio.model.system.ConfigurationKeys.DEFERRED_BANK_TRANSFER_ENABLED;
import static alfio.test.util.IntegrationTestUtil.AVAILABLE_SEATS;
import static alfio.test.util.IntegrationTestUtil.initEvent;
import static org.junit.jupiter.api.Assertions.*;

@AlfioIntegrationTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
class TicketReservationManagerIntegrationTest extends BaseIntegrationTest {

    static final Map<String, String> DESCRIPTION = Collections.singletonMap("en", "desc");
    private static final String ACCESS_CODE = "MYACCESSCODE";

    @Autowired
    private EventManager eventManager;

    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserManager userManager;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;
    @Autowired
    private TicketReservationManager ticketReservationManager;
    @Autowired
    private TicketReservationRepository ticketReservationRepository;
    @Autowired
    private EventStatisticsManager eventStatisticsManager;

    @Autowired
    private ConfigurationRepository configurationRepository;
    @Autowired
    private WaitingQueueManager waitingQueueManager;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private AdditionalServiceRepository additionalServiceRepository;
    @Autowired
    private SpecialPriceTokenGenerator specialPriceTokenGenerator;
    @Autowired
    private WaitingQueueSubscriptionProcessor waitingQueueSubscriptionProcessor;
    @Autowired
    private PurchaseContextSearchManager purchaseContextSearchManager;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void ensureConfiguration() {
        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);
    }

    @Test
    void testPriceIsOverridden() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                        new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                        new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                        DESCRIPTION, BigDecimal.TEN, false, "", false, null,
                    null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Event event = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository).getKey();

        TicketReservationModification tr = new TicketReservationModification();
        tr.setQuantity(2);
        TicketCategory category = ticketCategoryRepository.findAllTicketCategories(event.getId()).get(0);
        tr.setTicketCategoryId(category.getId());
        TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());
        ticketReservationManager.createTicketReservation(event, Collections.singletonList(mod), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Locale.ENGLISH, false, null);
        List<Ticket> pendingTickets = ticketRepository.findPendingTicketsInCategories(Collections.singletonList(category.getId()));
        assertEquals(2, pendingTickets.size());
        pendingTickets.forEach(t -> assertEquals(1000, t.getFinalPriceCts()));
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertEquals(18, tickets.size());
        assertTrue(tickets.stream().allMatch(t -> t.getCategoryId() == null));
    }

    @Test
    void testTicketSelection() {
        List<TicketCategoryModification> categories = List.of(
                new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                        new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                        new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                        DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()),
                new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 10,
                        new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                        new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                        DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> eventAndUsername = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = eventAndUsername.getKey();

        TicketCategory bounded = ticketCategoryRepository.findAllTicketCategories(event.getId()).stream().filter(TicketCategory::isBounded).findFirst().orElseThrow(IllegalStateException::new);
        TicketCategory unbounded = ticketCategoryRepository.findAllTicketCategories(event.getId()).stream().filter(t -> !t.isBounded()).findFirst().orElseThrow(IllegalStateException::new);

        assertEquals(0, eventStatisticsManager.loadModifiedTickets(event.getId(), bounded.getId(), 0, null).size());
        assertEquals(Integer.valueOf(0), eventStatisticsManager.countModifiedTicket(event.getId(), bounded.getId(), null));
        assertEquals(0, eventStatisticsManager.loadModifiedTickets(event.getId(), unbounded.getId(), 0, null).size());

        TicketReservationModification tr = new TicketReservationModification();
        tr.setQuantity(10);
        tr.setTicketCategoryId(bounded.getId());

        TicketReservationModification tr2 = new TicketReservationModification();
        tr2.setQuantity(9);
        tr2.setTicketCategoryId(unbounded.getId());

        TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());
        TicketReservationWithOptionalCodeModification mod2 = new TicketReservationWithOptionalCodeModification(tr2, Optional.empty());
        String reservationId = ticketReservationManager.createTicketReservation(event, List.of(mod, mod2), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Locale.ENGLISH, false, null);

        List<TicketReservation> reservations = purchaseContextSearchManager.findAllReservationsFor(event, 0, null, null).getKey();
        assertEquals(1, reservations.size());
        assertEquals(reservationId, reservations.get(0).getId());

        List<Ticket> pendingTickets = ticketRepository.findPendingTicketsInCategories(List.of(bounded.getId(), unbounded.getId()));
        assertEquals(19, pendingTickets.size());
        pendingTickets.forEach(t -> assertEquals(1000, t.getFinalPriceCts()));
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertEquals(1, tickets.size());
        assertTrue(tickets.stream().allMatch(t -> t.getCategoryId() == null));

        Pair<TotalPrice, Optional<PromoCodeDiscount>> priceAndDiscount = ticketReservationManager.totalReservationCostWithVAT(reservationId);
        TotalPrice totalPrice = priceAndDiscount.getLeft();
        assertTrue(priceAndDiscount.getRight().isEmpty());


        assertEquals(0, ticketReservationManager.getPendingPayments(event.getShortName()).size());

        PaymentSpecification specification = new PaymentSpecification(reservationId, null, totalPrice.priceWithVAT(),
            event, "email@example.com", new CustomerName("full name", "full", "name", event.mustUseFirstAndLastName()),
            "billing address", null, Locale.ENGLISH, true, false, null, "IT", "123456", PriceContainer.VatStatus.INCLUDED, true, false);

        PaymentResult confirm = ticketReservationManager.performPayment(specification, totalPrice, PaymentProxy.OFFLINE, PaymentMethod.BANK_TRANSFER, null);
        assertTrue(confirm.isSuccessful());

        assertEquals(TicketReservation.TicketReservationStatus.OFFLINE_PAYMENT, ticketReservationManager.findById(reservationId).get().getStatus());

        assertEquals(1, ticketReservationManager.getPendingPayments(event.getShortName()).size());

        var from = ZonedDateTime.now(Clock.systemUTC()).minusDays(1).with(d -> d.with(ChronoField.HOUR_OF_DAY, 0));
        var to = ZonedDateTime.now(Clock.systemUTC()).plusDays(1).with(ChronoField.HOUR_OF_DAY, 23);

        assertTrue(ticketReservationRepository.getSoldStatistic(event.getId(), from, to, "day").stream().allMatch(tds -> tds.getCount() == 0L)); // -> no reservations
        ticketReservationManager.validateAndConfirmOfflinePayment(reservationId, event, new BigDecimal("190.00"), eventAndUsername.getValue());

        var soldStatisticsList = ticketReservationRepository.getSoldStatistic(event.getId(), from, to, "day");
        assertEquals(3, soldStatisticsList.size());
        assertEquals(LocalDate.now(ClockProvider.clock()).toString(), soldStatisticsList.get(1).getDate());
        assertEquals(19L, soldStatisticsList.get(1).getCount()); // -> 19 tickets reserved
        assertEquals(19L, soldStatisticsList.stream().mapToLong(TicketsByDateStatistic::getCount).sum());

        assertEquals(10, eventStatisticsManager.loadModifiedTickets(event.getId(), bounded.getId(), 0, null).size());
        assertEquals(Integer.valueOf(10), eventStatisticsManager.countModifiedTicket(event.getId(), bounded.getId(), null));
        assertEquals(9, eventStatisticsManager.loadModifiedTickets(event.getId(), unbounded.getId(), 0, null).size());
        assertEquals(Integer.valueOf(9), eventStatisticsManager.countModifiedTicket(event.getId(), unbounded.getId(), null));

        assertEquals(TicketReservation.TicketReservationStatus.COMPLETE, ticketReservationManager.findById(reservationId).get().getStatus());


        //-------------------

        TicketReservationModification trForDelete = new TicketReservationModification();
        trForDelete.setQuantity(1);
        trForDelete.setTicketCategoryId(unbounded.getId());
        TicketReservationWithOptionalCodeModification modForDelete = new TicketReservationWithOptionalCodeModification(trForDelete, Optional.empty());
        String reservationId2 = ticketReservationManager.createTicketReservation(event, Collections.singletonList(modForDelete), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Locale.ENGLISH, false, null);

        PaymentSpecification specification2 = new PaymentSpecification(reservationId2, null, totalPrice.priceWithVAT(),
            event, "email@example.com", new CustomerName("full name", "full", "name", event.mustUseFirstAndLastName()),
                "billing address", null, Locale.ENGLISH, true, false, null, "IT", "123456", PriceContainer.VatStatus.INCLUDED, true, false);

        PaymentResult confirm2 = ticketReservationManager.performPayment(specification2, totalPrice, PaymentProxy.OFFLINE, PaymentMethod.BANK_TRANSFER, null);
        assertTrue(confirm2.isSuccessful());

        ticketReservationManager.deleteOfflinePayment(event, reservationId2, false, false, false, null);

        assertFalse(ticketReservationManager.findById(reservationId2).isPresent());
    }

    @Test
    void deferredOfflinePayment() {
        // enable deferred payment
        configurationRepository.insert(DEFERRED_BANK_TRANSFER_ENABLED.name(), "true", "");

        List<TicketCategoryModification> categories = List.of(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> eventAndUsername = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = eventAndUsername.getKey();

        TicketCategory unbounded = ticketCategoryRepository.findAllTicketCategories(event.getId()).get(0);


        TicketReservationModification trForDeferred = new TicketReservationModification();
        trForDeferred.setQuantity(1);
        trForDeferred.setTicketCategoryId(unbounded.getId());
        TicketReservationWithOptionalCodeModification modForDeferred = new TicketReservationWithOptionalCodeModification(trForDeferred, Optional.empty());
        String reservationId = ticketReservationManager.createTicketReservation(event, Collections.singletonList(modForDeferred), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Locale.ENGLISH, false, null);

        Pair<TotalPrice, Optional<PromoCodeDiscount>> priceAndDiscount = ticketReservationManager.totalReservationCostWithVAT(reservationId);
        TotalPrice totalPrice = priceAndDiscount.getLeft();
        assertTrue(priceAndDiscount.getRight().isEmpty());

        PaymentSpecification specificationDeferred = new PaymentSpecification(reservationId, null, totalPrice.priceWithVAT(),
            event, "email@example.com", new CustomerName("full name", "full", "name", event.mustUseFirstAndLastName()),
            "billing address", null, Locale.ENGLISH, true, false, null, "IT", "123456", PriceContainer.VatStatus.INCLUDED, true, false);

        PaymentResult confirm = ticketReservationManager.performPayment(specificationDeferred, totalPrice, PaymentProxy.OFFLINE, PaymentMethod.BANK_TRANSFER, null);
        assertTrue(confirm.isSuccessful());

        var status = ticketReservationRepository.findOptionalStatusAndValidationById(reservationId).orElseThrow().getStatus();
        assertEquals(TicketReservation.TicketReservationStatus.DEFERRED_OFFLINE_PAYMENT, status);

        // confirm deferred payment
        ticketReservationManager.confirmOfflinePayment(event, reservationId, null, null);

        reservationId = ticketReservationManager.createTicketReservation(event, Collections.singletonList(modForDeferred), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Locale.ENGLISH, false, null);

        specificationDeferred = new PaymentSpecification(reservationId, null, totalPrice.priceWithVAT(),
            event, "email@example.com", new CustomerName("full name", "full", "name", event.mustUseFirstAndLastName()),
            "billing address", null, Locale.ENGLISH, true, false, null, "IT", "123456", PriceContainer.VatStatus.INCLUDED, true, false);

        confirm = ticketReservationManager.performPayment(specificationDeferred, totalPrice, PaymentProxy.OFFLINE, PaymentMethod.BANK_TRANSFER, null);
        assertTrue(confirm.isSuccessful());

        try {
            ticketReservationManager.deleteOfflinePayment(event, reservationId, false, true, false, null);
            fail("Credit should not be enabled for deferred payments");
        } catch (IllegalArgumentException ex) {
            // do nothing, because this is the expected behavior
        }

        ticketReservationManager.deleteOfflinePayment(event, reservationId, false, false, false, null);
        assertFalse(ticketReservationManager.findById(reservationId).isPresent());
    }

    @Test
    void testTicketWithDiscount() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Event event = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository).getKey();

        TicketCategory unbounded = ticketCategoryRepository.findAllTicketCategories(event.getId()).stream().filter(t -> !t.isBounded()).findFirst().orElseThrow(IllegalStateException::new);

        //promo code at event level
        eventManager.addPromoCode("MYPROMOCODE", event.getId(), null, event.getBegin(), event.getEnd(), 10, PromoCodeDiscount.DiscountType.PERCENTAGE, null, 3, "description", "email@reference.ch", PromoCodeDiscount.CodeType.DISCOUNT, null, null);

        //promo code at organization level
        eventManager.addPromoCode("MYFIXEDPROMO", null, event.getOrganizationId(), event.getBegin(), event.getEnd(), 5, PromoCodeDiscount.DiscountType.FIXED_AMOUNT, null, null,"description", "email@reference.ch", PromoCodeDiscount.CodeType.DISCOUNT, null, "CHF");

        TicketReservationModification tr = new TicketReservationModification();
        tr.setQuantity(3);
        tr.setTicketCategoryId(unbounded.getId());
        TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());

        String reservationId = ticketReservationManager.createTicketReservation(event, Collections.singletonList(mod), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.of("MYPROMOCODE"), Locale.ENGLISH, false, null);

        Pair<TotalPrice, Optional<PromoCodeDiscount>> priceAndDiscount = ticketReservationManager.totalReservationCostWithVAT(reservationId);
        TotalPrice totalPrice = priceAndDiscount.getLeft();
        assertFalse(priceAndDiscount.getRight().isEmpty());
        assertEquals("MYPROMOCODE", priceAndDiscount.getRight().get().getPromoCode());

        // 3 * 10 chf is the normal price, 10% discount -> 300 discount
        assertEquals(2700, totalPrice.priceWithVAT());
        assertEquals(27, totalPrice.VAT());
        assertEquals(-300, totalPrice.discount());
        assertEquals(1, totalPrice.discountAppliedCount());

        OrderSummary orderSummary = ticketReservationManager.orderSummaryForReservationId(reservationId, event);
        assertEquals("27.00", orderSummary.getTotalPrice());
        assertEquals("0.27", orderSummary.getTotalVAT());
        assertEquals(3, orderSummary.getTicketAmount());


        TicketReservationModification trFixed = new TicketReservationModification();
        trFixed.setQuantity(3);
        trFixed.setTicketCategoryId(unbounded.getId());
        TicketReservationWithOptionalCodeModification modFixed = new TicketReservationWithOptionalCodeModification(trFixed, Optional.empty());

        String reservationIdFixed = ticketReservationManager.createTicketReservation(event, Collections.singletonList(modFixed), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.of("MYFIXEDPROMO"), Locale.ENGLISH, false, null);

        Pair<TotalPrice, Optional<PromoCodeDiscount>> priceAndDiscountFixed = ticketReservationManager.totalReservationCostWithVAT(reservationIdFixed);
        TotalPrice totalPriceFixed = priceAndDiscountFixed.getLeft();
        assertFalse(priceAndDiscountFixed.getRight().isEmpty());
        assertEquals("MYFIXEDPROMO", priceAndDiscountFixed.getRight().get().getPromoCode());

        // 3 * 10 chf is the normal price, 3 * 5 is the discount
        assertEquals(2985, totalPriceFixed.priceWithVAT());
        assertEquals(30, totalPriceFixed.VAT());
        assertEquals(-15, totalPriceFixed.discount());
        assertEquals(3, totalPriceFixed.discountAppliedCount());

        OrderSummary orderSummaryFixed = ticketReservationManager.orderSummaryForReservationId(reservationIdFixed, event);
        assertEquals("29.85", orderSummaryFixed.getTotalPrice());
        assertEquals("0.30", orderSummaryFixed.getTotalVAT());
        assertEquals(3, orderSummaryFixed.getTicketAmount());


        //check if we try to fetch more than the limit

        TicketReservationModification trTooMuch = new TicketReservationModification();
        trTooMuch.setQuantity(4);
        trTooMuch.setTicketCategoryId(unbounded.getId());
        TicketReservationWithOptionalCodeModification modTooMuch = new TicketReservationWithOptionalCodeModification(trTooMuch, Optional.empty());
        assertThrows(TooManyTicketsForDiscountCodeException.class,
            () -> ticketReservationManager.createTicketReservation(event, Collections.singletonList(modTooMuch ), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.of("MYPROMOCODE"), Locale.ENGLISH, false, null));
    }

    @Test
    void testAdditionalServiceWithDiscount() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Event event = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository).getKey();

        var firstAsKey = additionalServiceRepository.insert(event.getId(), 1000, true, 1, 100, 1, ZonedDateTime.now(ClockProvider.clock()).minusHours(1), ZonedDateTime.now(ClockProvider.clock()).plusHours(1), BigDecimal.TEN, AdditionalService.VatType.INHERITED, AdditionalService.AdditionalServiceType.SUPPLEMENT, AdditionalService.SupplementPolicy.OPTIONAL_UNLIMITED_AMOUNT);
        var secondAsKey = additionalServiceRepository.insert(event.getId(), 500, true, 2, 100, 1, ZonedDateTime.now(ClockProvider.clock()).minusHours(1), ZonedDateTime.now(ClockProvider.clock()).plusHours(1), BigDecimal.TEN, AdditionalService.VatType.INHERITED, AdditionalService.AdditionalServiceType.DONATION, AdditionalService.SupplementPolicy.OPTIONAL_UNLIMITED_AMOUNT);

        TicketCategory unbounded = ticketCategoryRepository.findAllTicketCategories(event.getId()).stream().filter(t -> !t.isBounded()).findFirst().orElseThrow(IllegalStateException::new);

        //promo code at event level
        eventManager.addPromoCode("MYPROMOCODE", event.getId(), null, event.getBegin(), event.getEnd(), 10, PromoCodeDiscount.DiscountType.PERCENTAGE, null, 3, "description", "email@reference.ch", PromoCodeDiscount.CodeType.DISCOUNT, null, null);

        TicketReservationModification tr = new TicketReservationModification();
        tr.setQuantity(3);
        tr.setTicketCategoryId(unbounded.getId());
        TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());

        var firstAsModification = new AdditionalServiceReservationModification();
        firstAsModification.setAdditionalServiceId(firstAsKey.getKey());
        firstAsModification.setQuantity(1);

        var secondAsModification = new AdditionalServiceReservationModification();
        secondAsModification.setAdditionalServiceId(secondAsKey.getKey());
        secondAsModification.setQuantity(1);

        var additionalServices = List.of(new ASReservationWithOptionalCodeModification(firstAsModification, Optional.empty()), new ASReservationWithOptionalCodeModification(secondAsModification, Optional.empty()));

        String reservationId = ticketReservationManager.createTicketReservation(event, Collections.singletonList(mod), additionalServices, DateUtils.addDays(new Date(), 1), Optional.of("MYPROMOCODE"), Locale.ENGLISH, false, null);

        Pair<TotalPrice, Optional<PromoCodeDiscount>> priceAndDiscount = ticketReservationManager.totalReservationCostWithVAT(reservationId);
        TotalPrice totalPrice = priceAndDiscount.getLeft();
        assertFalse(priceAndDiscount.getRight().isEmpty());
        assertEquals("MYPROMOCODE", priceAndDiscount.getRight().get().getPromoCode());

        // totalPrice is (ticketPrice * 3) + (asPrice) + (donationPrice) - (discount * 4)

        assertEquals(4100, totalPrice.priceWithVAT());
        assertEquals(-400, totalPrice.discount());
    }

    @Test
    void testAccessCode() {
        testTicketsWithAccessCode();
    }

    private Triple<Event, TicketCategory, String> testTicketsWithAccessCode() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, true, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Event event = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository).getKey();

        TicketCategory category = ticketCategoryRepository.findAllTicketCategories(event.getId()).stream().filter(TicketCategory::isAccessRestricted).findFirst().orElseThrow();

        specialPriceTokenGenerator.generatePendingCodesForCategory(category.getId());

        //promo code at event level
        String accessCode = ACCESS_CODE;
        eventManager.addPromoCode(accessCode, event.getId(), null, event.getBegin(), event.getEnd(), 0, null, null, 3, "description", "email@reference.ch", PromoCodeDiscount.CodeType.ACCESS, category.getId(), null);

        TicketReservationModification tr = new TicketReservationModification();
        tr.setQuantity(3);
        tr.setTicketCategoryId(category.getId());
        TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());

        String reservationId = ticketReservationManager.createTicketReservation(event, Collections.singletonList(mod), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.of(accessCode), Locale.ENGLISH, false, null);

        Pair<TotalPrice, Optional<PromoCodeDiscount>> priceAndDiscount = ticketReservationManager.totalReservationCostWithVAT(reservationId);
        TotalPrice totalPrice = priceAndDiscount.getLeft();
        assertFalse(priceAndDiscount.getRight().isEmpty());
        assertEquals(ACCESS_CODE, priceAndDiscount.getRight().get().getPromoCode());


        // 3 * 10 chf is the normal price, 10% discount -> 300 discount
        assertEquals(3000, totalPrice.priceWithVAT());
        assertEquals(30, totalPrice.VAT());
        assertEquals(0, totalPrice.discount());
        assertEquals(0, totalPrice.discountAppliedCount());

        OrderSummary orderSummary = ticketReservationManager.orderSummaryForReservationId(reservationId, event);
        assertEquals("30.00", orderSummary.getTotalPrice());
        assertEquals("0.30", orderSummary.getTotalVAT());
        assertEquals(3, orderSummary.getTicketAmount());

        return Triple.of(event, category, reservationId);

    }

    @Test
    void testAccessCodeLimit() {
        var triple = testTicketsWithAccessCode();
        TicketReservationModification trTooMuch = new TicketReservationModification();
        trTooMuch.setQuantity(1);
        trTooMuch.setTicketCategoryId(triple.getMiddle().getId());
        TicketReservationWithOptionalCodeModification modTooMuch = new TicketReservationWithOptionalCodeModification(trTooMuch, Optional.empty());
        assertThrows(TooManyTicketsForDiscountCodeException.class,
            () -> ticketReservationManager.createTicketReservation(triple.getLeft(), List.of(modTooMuch), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.of(ACCESS_CODE), Locale.ENGLISH, false, null));
    }

    @Test
    void testAccessCodeReleaseTickets() {
        var triple = testTicketsWithAccessCode();
        TicketReservationModification trTooMuch = new TicketReservationModification();
        trTooMuch.setQuantity(1);
        trTooMuch.setTicketCategoryId(triple.getMiddle().getId());
        TicketReservationWithOptionalCodeModification modTooMuch = new TicketReservationWithOptionalCodeModification(trTooMuch, Optional.empty());
        ticketReservationManager.cancelPendingReservation(triple.getRight(), true, null);
        waitingQueueSubscriptionProcessor.handleWaitingTickets();

        var newReservationId = ticketReservationManager.createTicketReservation(triple.getLeft(), List.of(modTooMuch), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.of(ACCESS_CODE), Locale.ENGLISH, false, null);
        assertNotNull(newReservationId);

    }

    @Test
    void testWithAdditionalServices() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));

        List<EventModification.AdditionalService> additionalServices = Collections.singletonList(new EventModification.AdditionalService(null, BigDecimal.TEN, true, 1, 100, 5,
            DateTimeModification.fromZonedDateTime(ZonedDateTime.now(ClockProvider.clock()).minusDays(1L)), DateTimeModification.fromZonedDateTime(ZonedDateTime.now(ClockProvider.clock()).plusDays(1L)),
            BigDecimal.TEN, AdditionalService.VatType.INHERITED, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), AdditionalService.AdditionalServiceType.SUPPLEMENT, AdditionalService.SupplementPolicy.OPTIONAL_UNLIMITED_AMOUNT));
        Event event = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository, additionalServices, Event.EventFormat.IN_PERSON).getKey();

        TicketCategory unbounded = ticketCategoryRepository.findAllTicketCategories(event.getId()).stream().filter(t -> !t.isBounded()).findFirst().orElseThrow(IllegalStateException::new);

        TicketReservationModification tr = new TicketReservationModification();
        tr.setQuantity(3);
        tr.setTicketCategoryId(unbounded.getId());
        TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());

        AdditionalServiceReservationModification asrm = new AdditionalServiceReservationModification();
        asrm.setAdditionalServiceId(additionalServiceRepository.loadAllForEvent(event.getId()).get(0).getId());
        asrm.setQuantity(1);

        ASReservationWithOptionalCodeModification asMod = new ASReservationWithOptionalCodeModification(asrm, Optional.empty());

        String reservationId = ticketReservationManager.createTicketReservation(event, Collections.singletonList(mod), Collections.singletonList(asMod), DateUtils.addDays(new Date(), 1), Optional.of("MYPROMOCODE"), Locale.ENGLISH, false, null);

        Pair<TotalPrice, Optional<PromoCodeDiscount>> priceAndDiscount = ticketReservationManager.totalReservationCostWithVAT(reservationId);
        TotalPrice totalPrice = priceAndDiscount.getLeft();
        assertTrue(priceAndDiscount.getRight().isEmpty());

        assertEquals(4000, totalPrice.priceWithVAT());//3 tickets + 1 AS
        assertEquals(40, totalPrice.VAT());
        assertEquals(0, totalPrice.discount());
        assertEquals(0, totalPrice.discountAppliedCount());

        OrderSummary orderSummary = ticketReservationManager.orderSummaryForReservationId(reservationId, event);
        assertEquals("40.00", orderSummary.getTotalPrice());
        assertEquals("0.40", orderSummary.getTotalVAT());
        assertEquals(3, orderSummary.getTicketAmount());
        List<SummaryRow> asRows = orderSummary.getSummary().stream().filter(s -> s.type() == SummaryRow.SummaryType.ADDITIONAL_SERVICE).toList();
        assertEquals(1, asRows.size());
        assertEquals("9.90", asRows.get(0).priceBeforeVat());
        assertEquals("9.90", asRows.get(0).subTotalBeforeVat());
        assertEquals("10.00", asRows.get(0).subTotal());
    }

    @Test
    void testTicketSelectionNotEnoughTicketsAvailable() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Event event = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository).getKey();

        TicketCategory unbounded = ticketCategoryRepository.findAllTicketCategories(event.getId()).stream().filter(t -> !t.isBounded()).findFirst().orElseThrow(IllegalStateException::new);

        TicketReservationModification tr = new TicketReservationModification();
        tr.setQuantity(AVAILABLE_SEATS + 1);
        tr.setTicketCategoryId(unbounded.getId());
        TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());

        assertThrows(NotEnoughTicketsException.class, () -> ticketReservationManager.createTicketReservation(event, Collections.singletonList(mod), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Locale.ENGLISH, false, null));
    }

    @Test
    void testDeletePendingPaymentUnboundedCategory() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Event event = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository).getKey();

        TicketCategory unbounded = ticketCategoryRepository.findAllTicketCategories(event.getId()).get(0);

        TicketReservationModification tr = new TicketReservationModification();
        tr.setQuantity(AVAILABLE_SEATS / 2 + 1);
        tr.setTicketCategoryId(unbounded.getId());

        TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());
        String reservationId = ticketReservationManager.createTicketReservation(event, Collections.singletonList(mod), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Locale.ENGLISH, false, null);
        Pair<TotalPrice, Optional<PromoCodeDiscount>> priceAndDiscount = ticketReservationManager.totalReservationCostWithVAT(reservationId);
        TotalPrice reservationCost = priceAndDiscount.getLeft();
        assertTrue(priceAndDiscount.getRight().isEmpty());
        PaymentSpecification specification = new PaymentSpecification(reservationId, null, reservationCost.priceWithVAT(),
            event, "email@example.com", new CustomerName("full name", "full", "name", event.mustUseFirstAndLastName()),
            "billing address", null, Locale.ENGLISH, true, false, null, "IT", "123456", PriceContainer.VatStatus.INCLUDED, true, false);
        PaymentResult result = ticketReservationManager.performPayment(specification, reservationCost, PaymentProxy.OFFLINE, PaymentMethod.BANK_TRANSFER, null);
        assertTrue(result.isSuccessful());
        ticketReservationManager.deleteOfflinePayment(event, reservationId, false, false, false, null);
        waitingQueueManager.distributeSeats(event);

        mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());
        reservationId = ticketReservationManager.createTicketReservation(event, Collections.singletonList(mod), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Locale.ENGLISH, false, null);
        priceAndDiscount = ticketReservationManager.totalReservationCostWithVAT(reservationId);
        reservationCost = priceAndDiscount.getLeft();
        assertTrue(priceAndDiscount.getRight().isEmpty());
        PaymentSpecification specification2 = new PaymentSpecification(reservationId, null, reservationCost.priceWithVAT(),
            event, "email@example.com", new CustomerName("full name", "full", "name", event.mustUseFirstAndLastName()),
            "billing address", null, Locale.ENGLISH, true, false, null, "IT", "123456", PriceContainer.VatStatus.INCLUDED, true, false);
        result = ticketReservationManager.performPayment(specification2, reservationCost, PaymentProxy.OFFLINE, PaymentMethod.BANK_TRANSFER, null);
        assertTrue(result.isSuccessful());
    }


    @Test
    void testCleanupExpiredReservations() {

        var testCases = List.of(
            // 1st test case: bounded category, max 10 tickets
            List.of(
                new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 10,
                    new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                    new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                    DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty())),
            // 2nd test case: unbounded category
            List.of(
                new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, -1,
                    new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                    new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                    DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()))
        );

        for (List<TicketCategoryModification> categories : testCases) {
            Pair<Event, String> eventAndUsername = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
            Event event = eventAndUsername.getKey();

            TicketCategory category = ticketCategoryRepository.findAllTicketCategories(event.getId()).stream().findFirst().orElseThrow(IllegalStateException::new);
            boolean bounded = category.isBounded();

            TicketReservationModification tr = new TicketReservationModification();
            tr.setQuantity(10);
            tr.setTicketCategoryId(category.getId());

            TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());

            Date now = new Date();

            final Supplier<List<String>> idsPendingQuery = () -> jdbcTemplate.queryForList("select id from tickets_reservation where validity < :date and status = 'PENDING'", Collections.singletonMap("date", now), String.class);

            assertTrue(idsPendingQuery.get().isEmpty());

            String reservationId = ticketReservationManager.createTicketReservation(event, List.of(mod), Collections.emptyList(), DateUtils.addDays(new Date(), -2), Optional.empty(), Locale.ENGLISH, false, null);
            List<String> reservationIdPending = idsPendingQuery.get();
            assertEquals(1, reservationIdPending.size());
            assertEquals(reservationId, reservationIdPending.get(0));

            // check tickets
            var tickets = ticketRepository.findTicketsInReservation(reservationId);
            assertEquals(10, tickets.size());
            tickets.forEach(ticket -> assertEquals(category.getId(), ticket.getCategoryId()));
            var ticketIds = tickets.stream().map(Ticket::getId).collect(Collectors.toList());

            ticketReservationManager.cleanupExpiredReservations(now);
            assertTrue(idsPendingQuery.get().isEmpty());

            // check that category ID has been handled correctly
            tickets = ticketRepository.findByIds(ticketIds);
            assertEquals(10, tickets.size());
            if(bounded) {
                tickets.forEach(ticket -> assertEquals(category.getId(), ticket.getCategoryId()));
            } else {
                tickets.forEach(ticket -> assertNull(ticket.getCategoryId()));
            }
        }

    }

    @Test
    void testCleanupOfflineExpiredReservations() {
        List<TicketCategoryModification> categories = List.of(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 10,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> eventAndUsername = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = eventAndUsername.getKey();

        TicketCategory bounded = ticketCategoryRepository.findAllTicketCategories(event.getId()).stream().filter(TicketCategory::isBounded).findFirst().orElseThrow(IllegalStateException::new);


        TicketReservationModification tr = new TicketReservationModification();
        tr.setQuantity(10);
        tr.setTicketCategoryId(bounded.getId());

        TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());

        Date past = DateUtils.addDays(new Date(), -2);
        Date now = new Date();

        String reservationId = ticketReservationManager.createTicketReservation(event, List.of(mod), Collections.emptyList(), past, Optional.empty(), Locale.ENGLISH, false, null);

        final Supplier<List<String>> idsOfflinePayment = () -> jdbcTemplate.queryForList("select id from tickets_reservation where validity < :date and status = 'OFFLINE_PAYMENT'", Collections.singletonMap("date", now), String.class);

        assertTrue(idsOfflinePayment.get().isEmpty());

        Pair<TotalPrice, Optional<PromoCodeDiscount>> priceAndDiscount = ticketReservationManager.totalReservationCostWithVAT(reservationId);
        TotalPrice reservationCost = priceAndDiscount.getLeft();
        assertTrue(priceAndDiscount.getRight().isEmpty());
        PaymentSpecification specification = new PaymentSpecification(reservationId, null, reservationCost.priceWithVAT(),
            event, "email@example.com", new CustomerName("full name", "full", "name", event.mustUseFirstAndLastName()),
            "billing address", null, Locale.ENGLISH, true, false, null, "IT", "123456", PriceContainer.VatStatus.INCLUDED, true, false);
        PaymentResult result = ticketReservationManager.performPayment(specification, reservationCost, PaymentProxy.OFFLINE, PaymentMethod.BANK_TRANSFER, null);
        assertTrue(result.isSuccessful());


        //
        assertEquals(1, jdbcTemplate.update("update tickets_reservation set validity = :date where id = :id", Map.of("date", past, "id", reservationId)));

        //
        List<String> idsOffline = idsOfflinePayment.get();

        assertEquals(1, idsOffline.size());
        assertEquals(reservationId, idsOffline.get(0));

        ticketReservationManager.cleanupExpiredOfflineReservations(now);
        assertFalse(idsOfflinePayment.get().isEmpty());

        configurationRepository.insert(AUTOMATIC_REMOVAL_EXPIRED_OFFLINE_PAYMENT.name(), "true", "");

        ticketReservationManager.cleanupExpiredOfflineReservations(now);
        assertTrue(idsOfflinePayment.get().isEmpty());

    }
}