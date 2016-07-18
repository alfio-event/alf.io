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
import alfio.manager.support.OrderSummary;
import alfio.manager.support.PaymentResult;
import alfio.manager.user.UserManager;
import alfio.model.PromoCodeDiscount;
import alfio.model.SpecialPrice;
import alfio.model.Ticket;
import alfio.model.TicketReservation;
import alfio.model.modification.*;
import alfio.model.transaction.PaymentProxy;
import alfio.repository.TicketRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.IntegrationTestUtil;
import org.apache.commons.lang3.time.DateUtils;
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
import java.util.*;

import static alfio.test.util.IntegrationTestUtil.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
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
    private EventStatisticsManager eventStatisticsManager;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserManager userManager;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private TicketReservationManager ticketReservationManager;

    @Autowired
    private ConfigurationRepository configurationRepository;

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
                        DESCRIPTION, BigDecimal.TEN, false, "", false));
        EventWithStatistics event = eventStatisticsManager.fillWithStatistics(initEvent(categories, organizationRepository, userManager, eventManager).getKey());

        TicketReservationModification tr = new TicketReservationModification();
        tr.setAmount(2);
        TicketCategoryWithStatistic category = event.getTicketCategories().get(0);
        tr.setTicketCategoryId(category.getId());
        TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.<SpecialPrice>empty());
        ticketReservationManager.createTicketReservation(event.getId(), Collections.singletonList(mod), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.<String>empty(), Optional.<String>empty(), Locale.ENGLISH, false);
        List<Ticket> pendingTickets = ticketRepository.findPendingTicketsInCategories(Collections.singletonList(category.getId()));
        assertEquals(2, pendingTickets.size());
        pendingTickets.forEach(t -> assertEquals(new BigDecimal("9.90"), t.getOriginalPrice()));
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
                        DESCRIPTION, BigDecimal.TEN, false, "", false),
                new TicketCategoryModification(null, "default", 10,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        DESCRIPTION, BigDecimal.TEN, false, "", true));
        EventWithStatistics event = eventStatisticsManager.fillWithStatistics(initEvent(categories, organizationRepository, userManager, eventManager).getKey());

        TicketCategoryWithStatistic bounded = event.getTicketCategories().stream().filter(TicketCategoryWithStatistic::isBounded).findFirst().orElseThrow(IllegalStateException::new);
        TicketCategoryWithStatistic unbounded = event.getTicketCategories().stream().filter(t -> !t.isBounded()).findFirst().orElseThrow(IllegalStateException::new);

        TicketReservationModification tr = new TicketReservationModification();
        tr.setAmount(10);
        tr.setTicketCategoryId(bounded.getId());

        TicketReservationModification tr2 = new TicketReservationModification();
        tr2.setAmount(9);
        tr2.setTicketCategoryId(unbounded.getId());

        TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.<SpecialPrice>empty());
        TicketReservationWithOptionalCodeModification mod2 = new TicketReservationWithOptionalCodeModification(tr2, Optional.<SpecialPrice>empty());
        String reservationId = ticketReservationManager.createTicketReservation(event.getId(), Arrays.asList(mod, mod2), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.<String>empty(), Optional.<String>empty(), Locale.ENGLISH, false);
        List<Ticket> pendingTickets = ticketRepository.findPendingTicketsInCategories(Arrays.asList(bounded.getId(), unbounded.getId()));
        assertEquals(19, pendingTickets.size());
        pendingTickets.forEach(t -> assertEquals(new BigDecimal("9.90"), t.getOriginalPrice()));
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertEquals(1, tickets.size());
        assertTrue(tickets.stream().allMatch(t -> t.getCategoryId() == null));

        TicketReservationManager.TotalPrice totalPrice = ticketReservationManager.totalReservationCostWithVAT(reservationId);


        assertEquals(0, ticketReservationManager.getPendingPayments(event).size());

        PaymentResult confirm = ticketReservationManager.confirm(null, null, event.getEvent(), reservationId, "email@example.com", "full name", Locale.ENGLISH, "billing address",
            totalPrice, Optional.empty(), Optional.of(PaymentProxy.OFFLINE), false);


        assertTrue(confirm.isSuccessful());

        assertEquals(TicketReservation.TicketReservationStatus.OFFLINE_PAYMENT, ticketReservationManager.findById(reservationId).get().getStatus());

        assertEquals(1, ticketReservationManager.getPendingPayments(event).size());

        ticketReservationManager.validateAndConfirmOfflinePayment(reservationId, event.getEvent(), new BigDecimal("190.00"));

        assertEquals(TicketReservation.TicketReservationStatus.COMPLETE, ticketReservationManager.findById(reservationId).get().getStatus());


        //-------------------

        TicketReservationModification trForDelete = new TicketReservationModification();
        trForDelete.setAmount(1);
        trForDelete.setTicketCategoryId(unbounded.getId());
        TicketReservationWithOptionalCodeModification modForDelete = new TicketReservationWithOptionalCodeModification(trForDelete, Optional.<SpecialPrice>empty());
        String reservationId2 = ticketReservationManager.createTicketReservation(event.getId(), Collections.singletonList(modForDelete), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.<String>empty(), Optional.<String>empty(), Locale.ENGLISH, false);

        ticketReservationManager.confirm(null, null, event.getEvent(), reservationId2, "email@example.com", "full name", Locale.ENGLISH, "billing address",
            totalPrice, Optional.empty(), Optional.of(PaymentProxy.OFFLINE), false);

        assertTrue(ticketReservationManager.findById(reservationId2).isPresent());

        ticketReservationManager.deleteOfflinePayment(event.getEvent(), reservationId2, false);

        Assert.assertFalse(ticketReservationManager.findById(reservationId2).isPresent());

    }

    @Test
    public void testTicketWithDiscount() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false));
        EventWithStatistics event = eventStatisticsManager.fillWithStatistics(initEvent(categories, organizationRepository, userManager, eventManager).getKey());

        TicketCategoryWithStatistic unbounded = event.getTicketCategories().stream().filter(t -> !t.isBounded()).findFirst().orElseThrow(IllegalStateException::new);

        eventManager.addPromoCode("MYPROMOCODE", event.getId(), event.getBegin(), event.getEnd(), 10, PromoCodeDiscount.DiscountType.PERCENTAGE, null);
        eventManager.addPromoCode("MYFIXEDPROMO", event.getId(), event.getBegin(), event.getEnd(), 5, PromoCodeDiscount.DiscountType.FIXED_AMOUNT, null);

        TicketReservationModification tr = new TicketReservationModification();
        tr.setAmount(3);
        tr.setTicketCategoryId(unbounded.getId());
        TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());

        String reservationId = ticketReservationManager.createTicketReservation(event.getId(), Collections.singletonList(mod), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.<String>empty(), Optional.of("MYPROMOCODE"), Locale.ENGLISH, false);

        TicketReservationManager.TotalPrice totalPrice = ticketReservationManager.totalReservationCostWithVAT(reservationId);

        // 3 * 10 chf is the normal price, 10% discount -> 300 discount
        Assert.assertEquals(2700, totalPrice.getPriceWithVAT());
        Assert.assertEquals(27, totalPrice.getVAT());
        Assert.assertEquals(-300, totalPrice.getDiscount());
        Assert.assertEquals(1, totalPrice.getDiscountAppliedCount());

        OrderSummary orderSummary = ticketReservationManager.orderSummaryForReservationId(reservationId, event.getEvent(), Locale.ENGLISH);
        Assert.assertEquals("27.00", orderSummary.getTotalPrice());
        Assert.assertEquals("0.27", orderSummary.getTotalVAT());
        Assert.assertEquals(3, orderSummary.getTicketAmount());


        TicketReservationModification trFixed = new TicketReservationModification();
        trFixed.setAmount(3);
        trFixed.setTicketCategoryId(unbounded.getId());
        TicketReservationWithOptionalCodeModification modFixed = new TicketReservationWithOptionalCodeModification(trFixed, Optional.empty());

        String reservationIdFixed = ticketReservationManager.createTicketReservation(event.getId(), Collections.singletonList(modFixed), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.<String>empty(), Optional.of("MYFIXEDPROMO"), Locale.ENGLISH, false);

        TicketReservationManager.TotalPrice totalPriceFixed = ticketReservationManager.totalReservationCostWithVAT(reservationIdFixed);

        // 3 * 10 chf is the normal price, 3 * 5 is the discount
        Assert.assertEquals(2985, totalPriceFixed.getPriceWithVAT());
        Assert.assertEquals(30, totalPriceFixed.getVAT());
        Assert.assertEquals(-15, totalPriceFixed.getDiscount());
        Assert.assertEquals(3, totalPriceFixed.getDiscountAppliedCount());

        OrderSummary orderSummaryFixed = ticketReservationManager.orderSummaryForReservationId(reservationIdFixed, event.getEvent(), Locale.ENGLISH);
        Assert.assertEquals("29.85", orderSummaryFixed.getTotalPrice());
        Assert.assertEquals("0.30", orderSummaryFixed.getTotalVAT());
        Assert.assertEquals(3, orderSummaryFixed.getTicketAmount());

    }

    @Test(expected = TicketReservationManager.NotEnoughTicketsException.class)
    public void testTicketSelectionNotEnoughTicketsAvailable() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false));
        EventWithStatistics event = eventStatisticsManager.fillWithStatistics(initEvent(categories, organizationRepository, userManager, eventManager).getKey());

        TicketCategoryWithStatistic unbounded = event.getTicketCategories().stream().filter(t -> !t.isBounded()).findFirst().orElseThrow(IllegalStateException::new);

        TicketReservationModification tr = new TicketReservationModification();
        tr.setAmount(AVAILABLE_SEATS + 1);
        tr.setTicketCategoryId(unbounded.getId());
        TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());

        ticketReservationManager.createTicketReservation(event.getId(), Collections.singletonList(mod), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.<String>empty(), Optional.<String>empty(), Locale.ENGLISH, false);
    }
}