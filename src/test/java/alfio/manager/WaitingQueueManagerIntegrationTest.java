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
import alfio.config.WebSecurityConfig;
import alfio.manager.support.PaymentResult;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.TicketCategory;
import alfio.model.WaitingQueueSubscription;
import alfio.model.modification.*;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentProxy;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.WebIntegrationTest;
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
@ContextConfiguration(classes = {DataSourceConfiguration.class, WebSecurityConfig.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS})
@WebIntegrationTest("server.port:9000")
@Transactional
public class WaitingQueueManagerIntegrationTest {

    private static final Map<String, String> DESCRIPTION = Collections.singletonMap("en", "desc");

    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserManager userManager;
    @Autowired
    private EventManager eventManager;
    @Autowired
    private WaitingQueueManager waitingQueueManager;
    @Autowired
    private ConfigurationManager configurationManager;
    @Autowired
    private EventStatisticsManager eventStatisticsManager;
    @Autowired
    private TicketReservationManager ticketReservationManager;
    @Autowired
    private ConfigurationRepository configurationRepository;

    @BeforeClass
    public static void initEnv() {
        initSystemProperties();
    }

    @Before
    public void init() {
        ensureMinimalConfiguration(configurationRepository);
    }

    @Test
    public void testDistributeSeatsFirstCategoryIsUnbounded() throws Exception {
        List<TicketCategoryModification> categories = getTicketCategoryModifications(false, AVAILABLE_SEATS, true, 10);
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager);
        Event event = pair.getKey();
        TicketCategory firstCategory = eventManager.loadTicketCategories(event).stream().filter(t->t.getName().equals("defaultFirst")).findFirst().orElseThrow(IllegalStateException::new);
        configurationManager.saveCategoryConfiguration(firstCategory.getId(), event.getId(), Collections.singletonList(new ConfigurationModification(null, ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION.getValue(), "1")), pair.getRight()+"_owner");
        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_PRE_REGISTRATION, "true");
        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_WAITING_QUEUE, "true");
        boolean result = waitingQueueManager.subscribe(event, "John Doe", "john@doe.com", null, Locale.ENGLISH);
        assertTrue(result);
        List<Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime>> subscriptions = waitingQueueManager.distributeSeats(event).collect(Collectors.toList());
        assertEquals(1, subscriptions.size());
        Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime> subscriptionDetail = subscriptions.get(0);
        assertEquals("john@doe.com", subscriptionDetail.getLeft().getEmailAddress());
        TicketReservationWithOptionalCodeModification reservation = subscriptionDetail.getMiddle();
        assertEquals(Integer.valueOf(firstCategory.getId()), reservation.getTicketCategoryId());
        assertEquals(Integer.valueOf(1), reservation.getAmount());
        assertTrue(subscriptionDetail.getRight().isAfter(ZonedDateTime.now()));

    }

    @Test
    public void testDistributeSeatsFirstCategoryIsBounded() throws Exception {
        List<TicketCategoryModification> categories = getTicketCategoryModifications(true, 10, true, 10);
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager);
        Event event = pair.getKey();
        TicketCategory firstCategory = eventManager.loadTicketCategories(event).stream().filter(t->t.getName().equals("defaultFirst")).findFirst().orElseThrow(IllegalStateException::new);
        configurationManager.saveCategoryConfiguration(firstCategory.getId(), event.getId(), Collections.singletonList(new ConfigurationModification(null, ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION.getValue(), "1")), pair.getRight()+"_owner");
        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_PRE_REGISTRATION, "true");
        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_WAITING_QUEUE, "true");
        boolean result = waitingQueueManager.subscribe(event, "John Doe", "john@doe.com", null, Locale.ENGLISH);
        assertTrue(result);
        List<Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime>> subscriptions = waitingQueueManager.distributeSeats(event).collect(Collectors.toList());
        assertEquals(1, subscriptions.size());
        Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime> subscriptionDetail = subscriptions.get(0);
        assertEquals("john@doe.com", subscriptionDetail.getLeft().getEmailAddress());
        TicketReservationWithOptionalCodeModification reservation = subscriptionDetail.getMiddle();
        assertEquals(Integer.valueOf(firstCategory.getId()), reservation.getTicketCategoryId());
        assertEquals(Integer.valueOf(1), reservation.getAmount());
        assertTrue(subscriptionDetail.getRight().isAfter(ZonedDateTime.now()));

    }

    @Test
    public void testWaitingQueueForUnboundedCategory() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false));
        EventWithStatistics event = eventStatisticsManager.fillWithStatistics(initEvent(categories, organizationRepository, userManager, eventManager).getKey());

        TicketCategoryWithStatistic unbounded = event.getTicketCategories().get(0);

        TicketReservationModification tr = new TicketReservationModification();
        tr.setAmount(AVAILABLE_SEATS);
        tr.setTicketCategoryId(unbounded.getId());

        TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());
        String reservationId = ticketReservationManager.createTicketReservation(event.getEvent(), Collections.singletonList(mod), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.<String>empty(), Optional.<String>empty(), Locale.ENGLISH, false);
        TicketReservationManager.TotalPrice reservationCost = ticketReservationManager.totalReservationCostWithVAT(reservationId);
        PaymentResult result = ticketReservationManager.confirm("", null, event.getEvent(), reservationId, "test@test.ch", "Full Name", Locale.ENGLISH, "", reservationCost, Optional.empty(), Optional.of(PaymentProxy.OFFLINE), true);
        assertTrue(result.isSuccessful());
        event = eventStatisticsManager.fillWithStatistics(event.getEvent());
        assertEquals(0, event.getDynamicAllocation());
    }

    @Test
    public void testAssignTicketToWaitingQueueUnboundedCategory() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(), LocalTime.now().minusMinutes(1)),
                new DateTimeModification(LocalDate.now(), LocalTime.now().plusMinutes(20)),
                DESCRIPTION, BigDecimal.TEN, false, "", false));

        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_WAITING_QUEUE, "true");

        EventWithStatistics event = eventStatisticsManager.fillWithStatistics(initEvent(categories, organizationRepository, userManager, eventManager).getKey());

        TicketCategoryWithStatistic unbounded = event.getTicketCategories().get(0);

        TicketReservationModification tr = new TicketReservationModification();
        tr.setAmount(AVAILABLE_SEATS - 1);
        tr.setTicketCategoryId(unbounded.getId());

        TicketReservationModification tr2 = new TicketReservationModification();
        tr2.setAmount(1);
        tr2.setTicketCategoryId(unbounded.getId());

        TicketReservationWithOptionalCodeModification multi = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());
        TicketReservationWithOptionalCodeModification single = new TicketReservationWithOptionalCodeModification(tr2, Optional.empty());

        String reservationId = ticketReservationManager.createTicketReservation(event.getEvent(), Collections.singletonList(multi), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Optional.empty(), Locale.ENGLISH, false);
        TicketReservationManager.TotalPrice reservationCost = ticketReservationManager.totalReservationCostWithVAT(reservationId);
        PaymentResult result = ticketReservationManager.confirm("", null, event.getEvent(), reservationId, "test@test.ch", "Full Name", Locale.ENGLISH, "", reservationCost, Optional.empty(), Optional.of(PaymentProxy.OFFLINE), true);
        assertTrue(result.isSuccessful());

        String reservationIdSingle = ticketReservationManager.createTicketReservation(event.getEvent(), Collections.singletonList(single), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Optional.empty(), Locale.ENGLISH, false);
        TicketReservationManager.TotalPrice reservationCostSingle = ticketReservationManager.totalReservationCostWithVAT(reservationIdSingle);
        PaymentResult resultSingle = ticketReservationManager.confirm("", null, event.getEvent(), reservationIdSingle, "test@test.ch", "Full Name", Locale.ENGLISH, "", reservationCostSingle, Optional.empty(), Optional.of(PaymentProxy.OFFLINE), true);
        assertTrue(resultSingle.isSuccessful());

        event = eventStatisticsManager.fillWithStatistics(event.getEvent());
        assertEquals(0, event.getDynamicAllocation());

        assertTrue(waitingQueueManager.subscribe(event.getEvent(), "John Doe", "john@doe.com", null, Locale.ENGLISH));

        ticketReservationManager.deleteOfflinePayment(event.getEvent(), reservationIdSingle, false);

        List<Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime>> subscriptions = waitingQueueManager.distributeSeats(event.getEvent()).collect(Collectors.toList());
        assertEquals(1, subscriptions.size());
        Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime> subscriptionDetail = subscriptions.get(0);
        assertEquals("john@doe.com", subscriptionDetail.getLeft().getEmailAddress());
        TicketReservationWithOptionalCodeModification reservation = subscriptionDetail.getMiddle();
        assertEquals(Integer.valueOf(unbounded.getId()), reservation.getTicketCategoryId());
        assertEquals(Integer.valueOf(1), reservation.getAmount());
        assertTrue(subscriptionDetail.getRight().isAfter(ZonedDateTime.now()));
    }

    @Test
    public void testAssignTicketToWaitingQueueBoundedCategory() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(), LocalTime.now().minusMinutes(3)),
                new DateTimeModification(LocalDate.now(), LocalTime.now().plusMinutes(20)),
                DESCRIPTION, BigDecimal.TEN, false, "", true));

        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_WAITING_QUEUE, "true");

        EventWithStatistics event = eventStatisticsManager.fillWithStatistics(initEvent(categories, organizationRepository, userManager, eventManager).getKey());

        TicketCategoryWithStatistic bounded = event.getTicketCategories().get(0);

        TicketReservationModification tr = new TicketReservationModification();
        tr.setAmount(AVAILABLE_SEATS - 1);
        tr.setTicketCategoryId(bounded.getId());

        TicketReservationModification tr2 = new TicketReservationModification();
        tr2.setAmount(1);
        tr2.setTicketCategoryId(bounded.getId());

        TicketReservationWithOptionalCodeModification multi = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());
        TicketReservationWithOptionalCodeModification single = new TicketReservationWithOptionalCodeModification(tr2, Optional.empty());

        String reservationId = ticketReservationManager.createTicketReservation(event.getEvent(), Collections.singletonList(multi), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Optional.empty(), Locale.ENGLISH, false);
        TicketReservationManager.TotalPrice reservationCost = ticketReservationManager.totalReservationCostWithVAT(reservationId);
        PaymentResult result = ticketReservationManager.confirm("", null, event.getEvent(), reservationId, "test@test.ch", "Full Name", Locale.ENGLISH, "", reservationCost, Optional.empty(), Optional.of(PaymentProxy.OFFLINE), true);
        assertTrue(result.isSuccessful());

        String reservationIdSingle = ticketReservationManager.createTicketReservation(event.getEvent(), Collections.singletonList(single), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Optional.empty(), Locale.ENGLISH, false);
        TicketReservationManager.TotalPrice reservationCostSingle = ticketReservationManager.totalReservationCostWithVAT(reservationIdSingle);
        PaymentResult resultSingle = ticketReservationManager.confirm("", null, event.getEvent(), reservationIdSingle, "test@test.ch", "Full Name", Locale.ENGLISH, "", reservationCostSingle, Optional.empty(), Optional.of(PaymentProxy.OFFLINE), true);
        assertTrue(resultSingle.isSuccessful());

        event = eventStatisticsManager.fillWithStatistics(event.getEvent());
        assertEquals(0, event.getDynamicAllocation());

        assertTrue(waitingQueueManager.subscribe(event.getEvent(), "John Doe", "john@doe.com", null, Locale.ENGLISH));

        ticketReservationManager.deleteOfflinePayment(event.getEvent(), reservationIdSingle, false);

        List<Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime>> subscriptions = waitingQueueManager.distributeSeats(event.getEvent()).collect(Collectors.toList());
        assertEquals(1, subscriptions.size());
        Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime> subscriptionDetail = subscriptions.get(0);
        assertEquals("john@doe.com", subscriptionDetail.getLeft().getEmailAddress());
        TicketReservationWithOptionalCodeModification reservation = subscriptionDetail.getMiddle();
        assertEquals(Integer.valueOf(bounded.getId()), reservation.getTicketCategoryId());
        assertEquals(Integer.valueOf(1), reservation.getAmount());
        assertTrue(subscriptionDetail.getRight().isAfter(ZonedDateTime.now()));
    }

    @Test
    public void testAssignTicketToWaitingQueueUnboundedCategorySelected() {
        List<TicketCategoryModification> categories = Arrays.asList(
            new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(), LocalTime.now().minusHours(1)),
                new DateTimeModification(LocalDate.now(), LocalTime.now().plusHours(1)),
                DESCRIPTION, BigDecimal.TEN, false, "", false),
            new TicketCategoryModification(null, "default2", AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(), LocalTime.now().minusHours(1)),
                new DateTimeModification(LocalDate.now(), LocalTime.now().plusHours(1)),
                DESCRIPTION, BigDecimal.TEN, false, "", false));

        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_WAITING_QUEUE, "true");

        EventWithStatistics event = eventStatisticsManager.fillWithStatistics(initEvent(categories, organizationRepository, userManager, eventManager).getKey());

        TicketCategoryWithStatistic first = event.getTicketCategories().get(0);
        TicketCategoryWithStatistic second = event.getTicketCategories().get(1);

        TicketReservationModification tr = new TicketReservationModification();
        tr.setAmount(AVAILABLE_SEATS - 2);
        tr.setTicketCategoryId(first.getId());

        TicketReservationModification tr2 = new TicketReservationModification();
        tr2.setAmount(1);
        tr2.setTicketCategoryId(second.getId());

        TicketReservationModification tr3 = new TicketReservationModification();
        tr3.setAmount(1);
        tr3.setTicketCategoryId(first.getId());

        TicketReservationWithOptionalCodeModification multi = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());
        TicketReservationWithOptionalCodeModification single = new TicketReservationWithOptionalCodeModification(tr2, Optional.empty());

        String reservationId = ticketReservationManager.createTicketReservation(event.getEvent(), Collections.singletonList(multi), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Optional.empty(), Locale.ENGLISH, false);
        TicketReservationManager.TotalPrice reservationCost = ticketReservationManager.totalReservationCostWithVAT(reservationId);
        PaymentResult result = ticketReservationManager.confirm("", null, event.getEvent(), reservationId, "test@test.ch", "Full Name", Locale.ENGLISH, "", reservationCost, Optional.empty(), Optional.of(PaymentProxy.OFFLINE), true);
        assertTrue(result.isSuccessful());

        String reservationIdSingleFirst = ticketReservationManager.createTicketReservation(event.getEvent(), Collections.singletonList(single), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Optional.empty(), Locale.ENGLISH, false);
        TicketReservationManager.TotalPrice reservationCostSingle = ticketReservationManager.totalReservationCostWithVAT(reservationIdSingleFirst);
        PaymentResult resultSingleFirst = ticketReservationManager.confirm("", null, event.getEvent(), reservationIdSingleFirst, "test@test.ch", "Full Name", Locale.ENGLISH, "", reservationCostSingle, Optional.empty(), Optional.of(PaymentProxy.OFFLINE), true);
        assertTrue(resultSingleFirst.isSuccessful());

        String reservationIdSingleSecond = ticketReservationManager.createTicketReservation(event.getEvent(), Collections.singletonList(single), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Optional.empty(), Locale.ENGLISH, false);
        TicketReservationManager.TotalPrice reservationCostSingleSecond = ticketReservationManager.totalReservationCostWithVAT(reservationIdSingleSecond);
        PaymentResult resultSingleSecond = ticketReservationManager.confirm("", null, event.getEvent(), reservationIdSingleSecond, "test@test.ch", "Full Name", Locale.ENGLISH, "", reservationCostSingleSecond, Optional.empty(), Optional.of(PaymentProxy.OFFLINE), true);
        assertTrue(resultSingleSecond.isSuccessful());

        event = eventStatisticsManager.fillWithStatistics(event.getEvent());
        assertEquals(0, event.getDynamicAllocation());

        assertTrue(waitingQueueManager.subscribe(event.getEvent(), "John Doe", "john@doe.com", first.getId(), Locale.ENGLISH));
        assertTrue(waitingQueueManager.subscribe(event.getEvent(), "John Doe 2", "john@doe2.com", second.getId(), Locale.ENGLISH));

        ticketReservationManager.deleteOfflinePayment(event.getEvent(), reservationIdSingleFirst, false);
        ticketReservationManager.deleteOfflinePayment(event.getEvent(), reservationIdSingleSecond, false);

        List<Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime>> subscriptions = waitingQueueManager.distributeSeats(event.getEvent()).collect(Collectors.toList());
        assertEquals(2, subscriptions.size());
        Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime> subscriptionDetail = subscriptions.get(0);
        assertEquals("john@doe.com", subscriptionDetail.getLeft().getEmailAddress());
        TicketReservationWithOptionalCodeModification reservation = subscriptionDetail.getMiddle();
        assertEquals(Integer.valueOf(first.getId()), reservation.getTicketCategoryId());
        assertEquals(Integer.valueOf(1), reservation.getAmount());
        assertTrue(subscriptionDetail.getRight().isAfter(ZonedDateTime.now()));

        subscriptionDetail = subscriptions.get(1);
        assertEquals("john@doe2.com", subscriptionDetail.getLeft().getEmailAddress());
        reservation = subscriptionDetail.getMiddle();
        assertEquals(Integer.valueOf(second.getId()), reservation.getTicketCategoryId());
        assertEquals(Integer.valueOf(1), reservation.getAmount());
        assertTrue(subscriptionDetail.getRight().isAfter(ZonedDateTime.now()));
    }

    private List<TicketCategoryModification> getTicketCategoryModifications(boolean firstBounded, int firstSeats, boolean lastBounded, int lastSeats) {
        return Arrays.asList(
            new TicketCategoryModification(null, "defaultFirst", firstSeats,
                new DateTimeModification(LocalDate.now(), LocalTime.now().plusMinutes(4)),
                new DateTimeModification(LocalDate.now().plusDays(1), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", firstBounded),
            new TicketCategoryModification(null, "defaultLast", lastSeats,
                new DateTimeModification(LocalDate.now().plusDays(1), LocalTime.now()),
                new DateTimeModification(LocalDate.now().plusDays(2), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", lastBounded));
    }
}