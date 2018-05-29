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
import alfio.config.RepositoryConfiguration;
import alfio.manager.support.PaymentResult;
import alfio.manager.user.UserManager;
import alfio.model.*;
import alfio.model.modification.*;
import alfio.model.transaction.PaymentProxy;
import alfio.repository.*;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.IntegrationTestUtil;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static alfio.test.util.IntegrationTestUtil.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {RepositoryConfiguration.class, DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS})
@Transactional
public class TicketReservationManagerIntegrationTest {

    private static final Map<String, String> DESCRIPTION = Collections.singletonMap("en", "desc");

    @BeforeClass
    public static void initEnv() {
        initSystemProperties();
    }

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
                    null, null, null, null));
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
        List<TicketCategoryModification> categories = Arrays.asList(
                new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null),
                new TicketCategoryModification(null, "default", 10,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null));
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
        String reservationId = ticketReservationManager.createTicketReservation(event, Arrays.asList(mod, mod2), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Optional.empty(), Locale.ENGLISH, false);

        List<TicketReservation> reservations = ticketReservationManager.findAllReservationsInEvent(event.getId(), 0, null, null).getKey();
        assertTrue(reservations.size() == 1);
        assertEquals(reservationId, reservations.get(0).getId());

        List<Ticket> pendingTickets = ticketRepository.findPendingTicketsInCategories(Arrays.asList(bounded.getId(), unbounded.getId()));
        assertEquals(19, pendingTickets.size());
        pendingTickets.forEach(t -> assertEquals(1000, t.getFinalPriceCts()));
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertEquals(1, tickets.size());
        assertTrue(tickets.stream().allMatch(t -> t.getCategoryId() == null));

        TotalPrice totalPrice = ticketReservationManager.totalReservationCostWithVAT(reservationId);


        assertEquals(0, ticketReservationManager.getPendingPayments(event).size());

        PaymentResult confirm = ticketReservationManager.confirm(null, null, event, reservationId, "email@example.com", new CustomerName("full name", "full", "name", event), Locale.ENGLISH, "billing address", "reference",
            totalPrice, Optional.empty(), Optional.of(PaymentProxy.OFFLINE), false, null, null, null);


        assertTrue(confirm.isSuccessful());

        assertEquals(TicketReservation.TicketReservationStatus.OFFLINE_PAYMENT, ticketReservationManager.findById(reservationId).get().getStatus());

        assertEquals(1, ticketReservationManager.getPendingPayments(event).size());

        Date now = new Date();
        Date from = DateUtils.addDays(now, -1);
        Date to = DateUtils.addDays(now, 1);

        assertTrue(ticketReservationRepository.getSoldStatistic(event.getId(), from, to).isEmpty()); // -> no reservations
        ticketReservationManager.validateAndConfirmOfflinePayment(reservationId, event, new BigDecimal("190.00"), eventAndUsername.getValue());

        assertEquals(19, ticketReservationRepository.getSoldStatistic(event.getId(), from, to).get(0).getTicketSoldCount()); // -> 19 tickets reserved

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

        ticketReservationManager.confirm(null, null, event, reservationId2, "email@example.com", new CustomerName("full name", "full", "name", event), Locale.ENGLISH, "billing address", "reference",
            totalPrice, Optional.empty(), Optional.of(PaymentProxy.OFFLINE), false, null, null, null);

        assertTrue(ticketReservationManager.findById(reservationId2).isPresent());

        ticketReservationManager.deleteOfflinePayment(event, reservationId2, false);

        Assert.assertFalse(ticketReservationManager.findById(reservationId2).isPresent());
    }

    @Test
    public void testTicketWithDiscount() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null));
        Event event = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository).getKey();

        TicketCategory unbounded = ticketCategoryRepository.findByEventId(event.getId()).stream().filter(t -> !t.isBounded()).findFirst().orElseThrow(IllegalStateException::new);

        //promo code at event level
        eventManager.addPromoCode("MYPROMOCODE", event.getId(), null, event.getBegin(), event.getEnd(), 10, PromoCodeDiscount.DiscountType.PERCENTAGE, null);

        //promo code at organization level
        eventManager.addPromoCode("MYFIXEDPROMO", null, event.getOrganizationId(), event.getBegin(), event.getEnd(), 5, PromoCodeDiscount.DiscountType.FIXED_AMOUNT, null);

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

        OrderSummary orderSummary = ticketReservationManager.orderSummaryForReservationId(reservationId, event, Locale.ENGLISH);
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

        OrderSummary orderSummaryFixed = ticketReservationManager.orderSummaryForReservationId(reservationIdFixed, event, Locale.ENGLISH);
        Assert.assertEquals("29.85", orderSummaryFixed.getTotalPrice());
        Assert.assertEquals("0.30", orderSummaryFixed.getTotalVAT());
        Assert.assertEquals(3, orderSummaryFixed.getTicketAmount());

    }

    @Test
    public void testWithAdditionalServices() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null));

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

        OrderSummary orderSummary = ticketReservationManager.orderSummaryForReservationId(reservationId, event, Locale.ENGLISH);
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
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null));
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
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null));
        Event event = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository).getKey();

        TicketCategory unbounded = ticketCategoryRepository.findByEventId(event.getId()).get(0);

        TicketReservationModification tr = new TicketReservationModification();
        tr.setAmount(AVAILABLE_SEATS / 2 + 1);
        tr.setTicketCategoryId(unbounded.getId());

        TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());
        String reservationId = ticketReservationManager.createTicketReservation(event, Collections.singletonList(mod), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Optional.empty(), Locale.ENGLISH, false);
        TotalPrice reservationCost = ticketReservationManager.totalReservationCostWithVAT(reservationId);
        PaymentResult result = ticketReservationManager.confirm("", null, event, reservationId, "test@test.ch", new CustomerName("full name", "full", "name", event), Locale.ENGLISH, "", "", reservationCost, Optional.empty(), Optional.of(PaymentProxy.OFFLINE), false, null, null, null);
        assertTrue(result.isSuccessful());
        ticketReservationManager.deleteOfflinePayment(event, reservationId, false);
        waitingQueueManager.distributeSeats(event);

        mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());
        reservationId = ticketReservationManager.createTicketReservation(event, Collections.singletonList(mod), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Optional.empty(), Locale.ENGLISH, false);
        reservationCost = ticketReservationManager.totalReservationCostWithVAT(reservationId);
        result = ticketReservationManager.confirm("", null, event, reservationId, "test@test.ch", new CustomerName("full name", "full", "name", event), Locale.ENGLISH, "", "", reservationCost, Optional.empty(), Optional.of(PaymentProxy.OFFLINE), false, null, null, null);
        assertTrue(result.isSuccessful());
    }
}