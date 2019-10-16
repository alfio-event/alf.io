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
import alfio.manager.user.UserManager;
import alfio.model.*;
import alfio.model.modification.*;
import alfio.model.transaction.PaymentProxy;
import alfio.repository.*;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.BaseIntegrationTest;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static alfio.test.util.IntegrationTestUtil.AVAILABLE_SEATS;
import static alfio.test.util.IntegrationTestUtil.initEvent;
import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
@Transactional
public class TicketReservationManagerIntegrationTest extends BaseIntegrationTest {

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
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Before
    public void ensureConfiguration() {
        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);
    }

    @Test
    public void testPriceIsOverridden() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        DESCRIPTION, BigDecimal.TEN, false, "", false, null,
                    null, null, null, null, null));
        Event event = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository).getKey();

        TicketReservationModification tr = new TicketReservationModification();
        tr.setAmount(2);
        TicketCategory category = ticketCategoryRepository.findByEventId(event.getId()).get(0);
        tr.setTicketCategoryId(category.getId());
        TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());
        ticketReservationManager.createTicketReservation(event, Collections.singletonList(mod), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Optional.empty(), Locale.ENGLISH, false);
        List<Ticket> pendingTickets = ticketRepository.findPendingTicketsInCategories(Collections.singletonList(category.getId()));
        assertEquals(2, pendingTickets.size());
        pendingTickets.forEach(t -> assertEquals(1000, t.getFinalPriceCts()));
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertEquals(18, tickets.size());
        assertTrue(tickets.stream().allMatch(t -> t.getCategoryId() == null));
    }

    @Test
    public void testTicketSelection() {
        List<TicketCategoryModification> categories = List.of(
                new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, null),
                new TicketCategoryModification(null, "default", 10,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, null));
        Pair<Event, String> eventAndUsername = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = eventAndUsername.getKey();

        TicketCategory bounded = ticketCategoryRepository.findByEventId(event.getId()).stream().filter(TicketCategory::isBounded).findFirst().orElseThrow(IllegalStateException::new);
        TicketCategory unbounded = ticketCategoryRepository.findByEventId(event.getId()).stream().filter(t -> !t.isBounded()).findFirst().orElseThrow(IllegalStateException::new);

        assertEquals(0, eventStatisticsManager.loadModifiedTickets(event.getId(), bounded.getId(), 0, null).size());
        assertEquals(Integer.valueOf(0), eventStatisticsManager.countModifiedTicket(event.getId(), bounded.getId(), null));
        assertEquals(0, eventStatisticsManager.loadModifiedTickets(event.getId(), unbounded.getId(), 0, null).size());

        TicketReservationModification tr = new TicketReservationModification();
        tr.setAmount(10);
        tr.setTicketCategoryId(bounded.getId());

        TicketReservationModification tr2 = new TicketReservationModification();
        tr2.setAmount(9);
        tr2.setTicketCategoryId(unbounded.getId());

        TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());
        TicketReservationWithOptionalCodeModification mod2 = new TicketReservationWithOptionalCodeModification(tr2, Optional.empty());
        String reservationId = ticketReservationManager.createTicketReservation(event, List.of(mod, mod2), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Optional.empty(), Locale.ENGLISH, false);

        List<TicketReservation> reservations = ticketReservationManager.findAllReservationsInEvent(event.getId(), 0, null, null).getKey();
        assertEquals(1, reservations.size());
        assertEquals(reservationId, reservations.get(0).getId());

        List<Ticket> pendingTickets = ticketRepository.findPendingTicketsInCategories(List.of(bounded.getId(), unbounded.getId()));
        assertEquals(19, pendingTickets.size());
        pendingTickets.forEach(t -> assertEquals(1000, t.getFinalPriceCts()));
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertEquals(1, tickets.size());
        assertTrue(tickets.stream().allMatch(t -> t.getCategoryId() == null));

        TotalPrice totalPrice = ticketReservationManager.totalReservationCostWithVAT(reservationId);


        assertEquals(0, ticketReservationManager.getPendingPayments(event.getShortName()).size());

        PaymentSpecification specification = new PaymentSpecification(reservationId, null, totalPrice.getPriceWithVAT(),
            event, "email@example.com", new CustomerName("full name", "full", "name", event.mustUseFirstAndLastName()),
            "billing address", null, Locale.ENGLISH, true, false, null, "IT", "123456", PriceContainer.VatStatus.INCLUDED, true, false);

        PaymentResult confirm = ticketReservationManager.performPayment(specification, totalPrice, Optional.empty(), Optional.of(PaymentProxy.OFFLINE));
        assertTrue(confirm.isSuccessful());

        assertEquals(TicketReservation.TicketReservationStatus.OFFLINE_PAYMENT, ticketReservationManager.findById(reservationId).get().getStatus());

        assertEquals(1, ticketReservationManager.getPendingPayments(event.getShortName()).size());

        Date now = new Date();
        Date from = DateUtils.addDays(now, -1);
        Date to = DateUtils.addDays(now, 1);

        assertTrue(ticketReservationRepository.getSoldStatistic(event.getId(), from, to).isEmpty()); // -> no reservations
        ticketReservationManager.validateAndConfirmOfflinePayment(reservationId, event, new BigDecimal("190.00"), eventAndUsername.getValue());

        assertEquals(19, ticketReservationRepository.getSoldStatistic(event.getId(), from, to).get(0).getCount()); // -> 19 tickets reserved

        assertEquals(10, eventStatisticsManager.loadModifiedTickets(event.getId(), bounded.getId(), 0, null).size());
        assertEquals(Integer.valueOf(10), eventStatisticsManager.countModifiedTicket(event.getId(), bounded.getId(), null));
        assertEquals(9, eventStatisticsManager.loadModifiedTickets(event.getId(), unbounded.getId(), 0, null).size());
        assertEquals(Integer.valueOf(9), eventStatisticsManager.countModifiedTicket(event.getId(), unbounded.getId(), null));

        assertEquals(TicketReservation.TicketReservationStatus.COMPLETE, ticketReservationManager.findById(reservationId).get().getStatus());


        //-------------------

        TicketReservationModification trForDelete = new TicketReservationModification();
        trForDelete.setAmount(1);
        trForDelete.setTicketCategoryId(unbounded.getId());
        TicketReservationWithOptionalCodeModification modForDelete = new TicketReservationWithOptionalCodeModification(trForDelete, Optional.empty());
        String reservationId2 = ticketReservationManager.createTicketReservation(event, Collections.singletonList(modForDelete), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Optional.empty(), Locale.ENGLISH, false);

        PaymentSpecification specification2 = new PaymentSpecification(reservationId2, null, totalPrice.getPriceWithVAT(),
            event, "email@example.com", new CustomerName("full name", "full", "name", event.mustUseFirstAndLastName()),
                "billing address", null, Locale.ENGLISH, true, false, null, "IT", "123456", PriceContainer.VatStatus.INCLUDED, true, false);

        PaymentResult confirm2 = ticketReservationManager.performPayment(specification2, totalPrice, Optional.empty(), Optional.of(PaymentProxy.OFFLINE));
        assertTrue(confirm2.isSuccessful());

        ticketReservationManager.deleteOfflinePayment(event, reservationId2, false, false, null);

        Assert.assertFalse(ticketReservationManager.findById(reservationId2).isPresent());
    }

    @Test
    public void testTicketWithDiscount() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, null));
        Event event = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository).getKey();

        TicketCategory unbounded = ticketCategoryRepository.findByEventId(event.getId()).stream().filter(t -> !t.isBounded()).findFirst().orElseThrow(IllegalStateException::new);

        //promo code at event level
        eventManager.addPromoCode("MYPROMOCODE", event.getId(), null, event.getBegin(), event.getEnd(), 10, PromoCodeDiscount.DiscountType.PERCENTAGE, null, 3, "description", "email@reference.ch", PromoCodeDiscount.CodeType.DISCOUNT, null);

        //promo code at organization level
        eventManager.addPromoCode("MYFIXEDPROMO", null, event.getOrganizationId(), event.getBegin(), event.getEnd(), 5, PromoCodeDiscount.DiscountType.FIXED_AMOUNT, null, null,"description", "email@reference.ch", PromoCodeDiscount.CodeType.DISCOUNT, null);

        TicketReservationModification tr = new TicketReservationModification();
        tr.setAmount(3);
        tr.setTicketCategoryId(unbounded.getId());
        TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());

        String reservationId = ticketReservationManager.createTicketReservation(event, Collections.singletonList(mod), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Optional.of("MYPROMOCODE"), Locale.ENGLISH, false);

        TotalPrice totalPrice = ticketReservationManager.totalReservationCostWithVAT(reservationId);

        // 3 * 10 chf is the normal price, 10% discount -> 300 discount
        Assert.assertEquals(2700, totalPrice.getPriceWithVAT());
        Assert.assertEquals(27, totalPrice.getVAT());
        Assert.assertEquals(-300, totalPrice.getDiscount());
        Assert.assertEquals(1, totalPrice.getDiscountAppliedCount());

        OrderSummary orderSummary = ticketReservationManager.orderSummaryForReservationId(reservationId, event);
        Assert.assertEquals("27.00", orderSummary.getTotalPrice());
        Assert.assertEquals("0.27", orderSummary.getTotalVAT());
        Assert.assertEquals(3, orderSummary.getTicketAmount());


        TicketReservationModification trFixed = new TicketReservationModification();
        trFixed.setAmount(3);
        trFixed.setTicketCategoryId(unbounded.getId());
        TicketReservationWithOptionalCodeModification modFixed = new TicketReservationWithOptionalCodeModification(trFixed, Optional.empty());

        String reservationIdFixed = ticketReservationManager.createTicketReservation(event, Collections.singletonList(modFixed), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Optional.of("MYFIXEDPROMO"), Locale.ENGLISH, false);

        TotalPrice totalPriceFixed = ticketReservationManager.totalReservationCostWithVAT(reservationIdFixed);

        // 3 * 10 chf is the normal price, 3 * 5 is the discount
        Assert.assertEquals(2985, totalPriceFixed.getPriceWithVAT());
        Assert.assertEquals(30, totalPriceFixed.getVAT());
        Assert.assertEquals(-15, totalPriceFixed.getDiscount());
        Assert.assertEquals(3, totalPriceFixed.getDiscountAppliedCount());

        OrderSummary orderSummaryFixed = ticketReservationManager.orderSummaryForReservationId(reservationIdFixed, event);
        Assert.assertEquals("29.85", orderSummaryFixed.getTotalPrice());
        Assert.assertEquals("0.30", orderSummaryFixed.getTotalVAT());
        Assert.assertEquals(3, orderSummaryFixed.getTicketAmount());


        //check if we try to fetch more than the limit

        TicketReservationModification trTooMuch = new TicketReservationModification();
        trTooMuch.setAmount(4);
        trTooMuch.setTicketCategoryId(unbounded.getId());
        TicketReservationWithOptionalCodeModification modTooMuch = new TicketReservationWithOptionalCodeModification(trTooMuch, Optional.empty());
        try {
            ticketReservationManager.createTicketReservation(event, Collections.singletonList(modTooMuch ), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Optional.of("MYPROMOCODE"), Locale.ENGLISH, false);
            Assert.fail("must not enter here");
        } catch (TicketReservationManager.TooManyTicketsForDiscountCodeException e) {
        }

    }

    @Test
    public void testAdditionalServiceWithDiscount() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, null));
        Event event = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository).getKey();

        var firstAsKey = additionalServiceRepository.insert(event.getId(), 1000, true, 1, 100, 1, ZonedDateTime.now().minusHours(1), ZonedDateTime.now().plusHours(1), BigDecimal.TEN, AdditionalService.VatType.INHERITED, AdditionalService.AdditionalServiceType.SUPPLEMENT, AdditionalService.SupplementPolicy.OPTIONAL_UNLIMITED_AMOUNT);
        var secondAsKey = additionalServiceRepository.insert(event.getId(), 500, true, 2, 100, 1, ZonedDateTime.now().minusHours(1), ZonedDateTime.now().plusHours(1), BigDecimal.TEN, AdditionalService.VatType.INHERITED, AdditionalService.AdditionalServiceType.DONATION, AdditionalService.SupplementPolicy.OPTIONAL_UNLIMITED_AMOUNT);

        TicketCategory unbounded = ticketCategoryRepository.findByEventId(event.getId()).stream().filter(t -> !t.isBounded()).findFirst().orElseThrow(IllegalStateException::new);

        //promo code at event level
        eventManager.addPromoCode("MYPROMOCODE", event.getId(), null, event.getBegin(), event.getEnd(), 10, PromoCodeDiscount.DiscountType.PERCENTAGE, null, 3, "description", "email@reference.ch", PromoCodeDiscount.CodeType.DISCOUNT, null);

        TicketReservationModification tr = new TicketReservationModification();
        tr.setAmount(3);
        tr.setTicketCategoryId(unbounded.getId());
        TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());

        var firstAsModification = new AdditionalServiceReservationModification();
        firstAsModification.setAdditionalServiceId(firstAsKey.getKey());
        firstAsModification.setQuantity(1);

        var secondAsModification = new AdditionalServiceReservationModification();
        secondAsModification.setAdditionalServiceId(secondAsKey.getKey());
        secondAsModification.setQuantity(1);

        var additionalServices = List.of(new ASReservationWithOptionalCodeModification(firstAsModification, Optional.empty()), new ASReservationWithOptionalCodeModification(secondAsModification, Optional.empty()));

        String reservationId = ticketReservationManager.createTicketReservation(event, Collections.singletonList(mod), additionalServices, DateUtils.addDays(new Date(), 1), Optional.empty(), Optional.of("MYPROMOCODE"), Locale.ENGLISH, false);

        TotalPrice totalPrice = ticketReservationManager.totalReservationCostWithVAT(reservationId);

        // totalPrice is (ticketPrice * 3) + (asPrice) + (donationPrice) - (discount * 4)

        assertEquals(4100, totalPrice.getPriceWithVAT());
        assertEquals(-400, totalPrice.getDiscount());
    }

    @Test
    public void testAccessCode() {
        testTicketsWithAccessCode();
    }

    private Triple<Event, TicketCategory, String> testTicketsWithAccessCode() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, true, "", true, null, null, null, null, null, null));
        Event event = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository).getKey();

        TicketCategory category = ticketCategoryRepository.findByEventId(event.getId()).stream().filter(TicketCategory::isAccessRestricted).findFirst().orElseThrow();

        specialPriceTokenGenerator.generatePendingCodesForCategory(category.getId());

        //promo code at event level
        String accessCode = ACCESS_CODE;
        eventManager.addPromoCode(accessCode, event.getId(), null, event.getBegin(), event.getEnd(), 0, null, null, 3, "description", "email@reference.ch", PromoCodeDiscount.CodeType.ACCESS, category.getId());

        TicketReservationModification tr = new TicketReservationModification();
        tr.setAmount(3);
        tr.setTicketCategoryId(category.getId());
        TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());

        String reservationId = ticketReservationManager.createTicketReservation(event, Collections.singletonList(mod), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Optional.of(accessCode), Locale.ENGLISH, false);

        TotalPrice totalPrice = ticketReservationManager.totalReservationCostWithVAT(reservationId);

        // 3 * 10 chf is the normal price, 10% discount -> 300 discount
        Assert.assertEquals(3000, totalPrice.getPriceWithVAT());
        Assert.assertEquals(30, totalPrice.getVAT());
        Assert.assertEquals(0, totalPrice.getDiscount());
        Assert.assertEquals(0, totalPrice.getDiscountAppliedCount());

        OrderSummary orderSummary = ticketReservationManager.orderSummaryForReservationId(reservationId, event);
        Assert.assertEquals("30.00", orderSummary.getTotalPrice());
        Assert.assertEquals("0.30", orderSummary.getTotalVAT());
        Assert.assertEquals(3, orderSummary.getTicketAmount());

        return Triple.of(event, category, reservationId);

    }

    @Test
    public void testAccessCodeLimit() {
        var triple = testTicketsWithAccessCode();
        TicketReservationModification trTooMuch = new TicketReservationModification();
        trTooMuch.setAmount(1);
        trTooMuch.setTicketCategoryId(triple.getMiddle().getId());
        TicketReservationWithOptionalCodeModification modTooMuch = new TicketReservationWithOptionalCodeModification(trTooMuch, Optional.empty());
        try {
            ticketReservationManager.createTicketReservation(triple.getLeft(), List.of(modTooMuch), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Optional.of(ACCESS_CODE), Locale.ENGLISH, false);
            Assert.fail("trigger is not working!");
        } catch (TicketReservationManager.TooManyTicketsForDiscountCodeException e) {
        }
    }

    @Test
    public void testAccessCodeReleaseTickets() {
        var triple = testTicketsWithAccessCode();
        TicketReservationModification trTooMuch = new TicketReservationModification();
        trTooMuch.setAmount(1);
        trTooMuch.setTicketCategoryId(triple.getMiddle().getId());
        TicketReservationWithOptionalCodeModification modTooMuch = new TicketReservationWithOptionalCodeModification(trTooMuch, Optional.empty());
        ticketReservationManager.cancelPendingReservation(triple.getRight(), true, null);
        waitingQueueSubscriptionProcessor.handleWaitingTickets();

        var newReservationId = ticketReservationManager.createTicketReservation(triple.getLeft(), List.of(modTooMuch), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Optional.of(ACCESS_CODE), Locale.ENGLISH, false);
        assertNotNull(newReservationId);

    }

    @Test
    public void testWithAdditionalServices() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, null));

        List<EventModification.AdditionalService> additionalServices = Collections.singletonList(new EventModification.AdditionalService(null, BigDecimal.TEN, true, 1, 100, 5,
            DateTimeModification.fromZonedDateTime(ZonedDateTime.now().minusDays(1L)), DateTimeModification.fromZonedDateTime(ZonedDateTime.now().plusDays(1L)),
            BigDecimal.TEN, AdditionalService.VatType.INHERITED, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), AdditionalService.AdditionalServiceType.SUPPLEMENT, AdditionalService.SupplementPolicy.OPTIONAL_UNLIMITED_AMOUNT));
        Event event = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository, additionalServices).getKey();

        TicketCategory unbounded = ticketCategoryRepository.findByEventId(event.getId()).stream().filter(t -> !t.isBounded()).findFirst().orElseThrow(IllegalStateException::new);

        TicketReservationModification tr = new TicketReservationModification();
        tr.setAmount(3);
        tr.setTicketCategoryId(unbounded.getId());
        TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());

        AdditionalServiceReservationModification asrm = new AdditionalServiceReservationModification();
        asrm.setAdditionalServiceId(additionalServiceRepository.loadAllForEvent(event.getId()).get(0).getId());
        asrm.setQuantity(1);

        ASReservationWithOptionalCodeModification asMod = new ASReservationWithOptionalCodeModification(asrm, Optional.empty());

        String reservationId = ticketReservationManager.createTicketReservation(event, Collections.singletonList(mod), Collections.singletonList(asMod), DateUtils.addDays(new Date(), 1), Optional.empty(), Optional.of("MYPROMOCODE"), Locale.ENGLISH, false);

        TotalPrice totalPrice = ticketReservationManager.totalReservationCostWithVAT(reservationId);

        Assert.assertEquals(4000, totalPrice.getPriceWithVAT());//3 tickets + 1 AS
        Assert.assertEquals(40, totalPrice.getVAT());
        Assert.assertEquals(0, totalPrice.getDiscount());
        Assert.assertEquals(0, totalPrice.getDiscountAppliedCount());

        OrderSummary orderSummary = ticketReservationManager.orderSummaryForReservationId(reservationId, event);
        Assert.assertEquals("40.00", orderSummary.getTotalPrice());
        Assert.assertEquals("0.40", orderSummary.getTotalVAT());
        Assert.assertEquals(3, orderSummary.getTicketAmount());
        List<SummaryRow> asRows = orderSummary.getSummary().stream().filter(s -> s.getType() == SummaryRow.SummaryType.ADDITIONAL_SERVICE).collect(Collectors.toList());
        Assert.assertEquals(1, asRows.size());
        Assert.assertEquals("9.90", asRows.get(0).getPriceBeforeVat());
        Assert.assertEquals("9.90", asRows.get(0).getSubTotalBeforeVat());
        Assert.assertEquals("10.00", asRows.get(0).getSubTotal());
    }

    @Test(expected = TicketReservationManager.NotEnoughTicketsException.class)
    public void testTicketSelectionNotEnoughTicketsAvailable() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, null));
        Event event = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository).getKey();

        TicketCategory unbounded = ticketCategoryRepository.findByEventId(event.getId()).stream().filter(t -> !t.isBounded()).findFirst().orElseThrow(IllegalStateException::new);

        TicketReservationModification tr = new TicketReservationModification();
        tr.setAmount(AVAILABLE_SEATS + 1);
        tr.setTicketCategoryId(unbounded.getId());
        TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());

        ticketReservationManager.createTicketReservation(event, Collections.singletonList(mod), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Optional.empty(), Locale.ENGLISH, false);
    }

    @Test
    public void testDeletePendingPaymentUnboundedCategory() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, null));
        Event event = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository).getKey();

        TicketCategory unbounded = ticketCategoryRepository.findByEventId(event.getId()).get(0);

        TicketReservationModification tr = new TicketReservationModification();
        tr.setAmount(AVAILABLE_SEATS / 2 + 1);
        tr.setTicketCategoryId(unbounded.getId());

        TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());
        String reservationId = ticketReservationManager.createTicketReservation(event, Collections.singletonList(mod), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Optional.empty(), Locale.ENGLISH, false);
        TotalPrice reservationCost = ticketReservationManager.totalReservationCostWithVAT(reservationId);
        PaymentSpecification specification = new PaymentSpecification(reservationId, null, reservationCost.getPriceWithVAT(),
            event, "email@example.com", new CustomerName("full name", "full", "name", event.mustUseFirstAndLastName()),
            "billing address", null, Locale.ENGLISH, true, false, null, "IT", "123456", PriceContainer.VatStatus.INCLUDED, true, false);
        PaymentResult result = ticketReservationManager.performPayment(specification, reservationCost, Optional.empty(), Optional.of(PaymentProxy.OFFLINE));
        assertTrue(result.isSuccessful());
        ticketReservationManager.deleteOfflinePayment(event, reservationId, false, false, null);
        waitingQueueManager.distributeSeats(event);

        mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());
        reservationId = ticketReservationManager.createTicketReservation(event, Collections.singletonList(mod), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Optional.empty(), Locale.ENGLISH, false);
        reservationCost = ticketReservationManager.totalReservationCostWithVAT(reservationId);
        PaymentSpecification specification2 = new PaymentSpecification(reservationId, null, reservationCost.getPriceWithVAT(),
            event, "email@example.com", new CustomerName("full name", "full", "name", event.mustUseFirstAndLastName()),
            "billing address", null, Locale.ENGLISH, true, false, null, "IT", "123456", PriceContainer.VatStatus.INCLUDED, true, false);
        result = ticketReservationManager.performPayment(specification2, reservationCost, Optional.empty(), Optional.of(PaymentProxy.OFFLINE));
        assertTrue(result.isSuccessful());
    }


    @Test
    public void testCleanupExpiredReservations() {
        List<TicketCategoryModification> categories = List.of(
            new TicketCategoryModification(null, "default", 10,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, null));
        Pair<Event, String> eventAndUsername = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = eventAndUsername.getKey();

        TicketCategory bounded = ticketCategoryRepository.findByEventId(event.getId()).stream().filter(TicketCategory::isBounded).findFirst().orElseThrow(IllegalStateException::new);


        TicketReservationModification tr = new TicketReservationModification();
        tr.setAmount(10);
        tr.setTicketCategoryId(bounded.getId());

        TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());



        Date now = new Date();

        final Supplier<List<String>> idsPendingQuery = () -> jdbcTemplate.queryForList("select id from tickets_reservation where validity < :date and status = 'PENDING'", Collections.singletonMap("date", now), String.class);

        Assert.assertTrue(idsPendingQuery.get().isEmpty());

        String reservationId = ticketReservationManager.createTicketReservation(event, List.of(mod), Collections.emptyList(), DateUtils.addDays(new Date(), -2), Optional.empty(), Optional.empty(), Locale.ENGLISH, false);

        List<String> reservationIdPending = idsPendingQuery.get();
        Assert.assertEquals(1, reservationIdPending.size());
        Assert.assertEquals(reservationId, reservationIdPending.get(0));

        ticketReservationManager.cleanupExpiredReservations(now);

        Assert.assertTrue(idsPendingQuery.get().isEmpty());
    }

    @Test
    public void testCleanupOfflineExpiredReservations() {
        List<TicketCategoryModification> categories = List.of(
            new TicketCategoryModification(null, "default", 10,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, null));
        Pair<Event, String> eventAndUsername = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = eventAndUsername.getKey();

        TicketCategory bounded = ticketCategoryRepository.findByEventId(event.getId()).stream().filter(TicketCategory::isBounded).findFirst().orElseThrow(IllegalStateException::new);


        TicketReservationModification tr = new TicketReservationModification();
        tr.setAmount(10);
        tr.setTicketCategoryId(bounded.getId());

        TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());

        Date past = DateUtils.addDays(new Date(), -2);
        Date now = new Date();

        String reservationId = ticketReservationManager.createTicketReservation(event, List.of(mod), Collections.emptyList(), past, Optional.empty(), Optional.empty(), Locale.ENGLISH, false);

        final Supplier<List<String>> idsOfflinePayment = () -> jdbcTemplate.queryForList("select id from tickets_reservation where validity < :date and status = 'OFFLINE_PAYMENT'", Collections.singletonMap("date", now), String.class);

        Assert.assertTrue(idsOfflinePayment.get().isEmpty());

        TotalPrice reservationCost = ticketReservationManager.totalReservationCostWithVAT(reservationId);
        PaymentSpecification specification = new PaymentSpecification(reservationId, null, reservationCost.getPriceWithVAT(),
            event, "email@example.com", new CustomerName("full name", "full", "name", event.mustUseFirstAndLastName()),
            "billing address", null, Locale.ENGLISH, true, false, null, "IT", "123456", PriceContainer.VatStatus.INCLUDED, true, false);
        PaymentResult result = ticketReservationManager.performPayment(specification, reservationCost, Optional.empty(), Optional.of(PaymentProxy.OFFLINE));
        assertTrue(result.isSuccessful());


        //
        Assert.assertEquals(1, jdbcTemplate.update("update tickets_reservation set validity = :date where id = :id", Map.of("date", past, "id", reservationId)));

        //
        List<String> idsOffline = idsOfflinePayment.get();

        Assert.assertEquals(1, idsOffline.size());
        Assert.assertEquals(reservationId, idsOffline.get(0));

        ticketReservationManager.cleanupExpiredOfflineReservations(now);

        Assert.assertTrue(idsOfflinePayment.get().isEmpty());

    }
}