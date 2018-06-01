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
import alfio.config.WebSecurityConfig;
import alfio.manager.support.PaymentResult;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.*;
import alfio.model.modification.*;
import alfio.model.result.Result;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentProxy;
import alfio.repository.EventRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.TicketRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import static alfio.model.modification.DateTimeModification.fromZonedDateTime;
import static alfio.test.util.IntegrationTestUtil.*;
import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {RepositoryConfiguration.class, DataSourceConfiguration.class, WebSecurityConfig.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS})
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
    private TicketCategoryRepository ticketCategoryRepository;
    @Autowired
    private ConfigurationRepository configurationRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private TicketRepository ticketRepository;

    @BeforeClass
    public static void initEnv() {
        initSystemProperties();
    }

    @Before
    public void init() {
        ensureMinimalConfiguration(configurationRepository);
    }

    private static CustomerName customerJohnDoe(Event event) {
        return new CustomerName("John Doe", "John", "Doe", event);
    }

    @Test
    public void testSubscribeDenied() {
        List<TicketCategoryModification> categories = getPreSalesTicketCategoryModifications(false, AVAILABLE_SEATS, true, 10);
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = pair.getKey();
        TicketCategory firstCategory = eventManager.loadTicketCategories(event).stream().filter(t->t.getName().equals("defaultFirst")).findFirst().orElseThrow(IllegalStateException::new);
        configurationManager.saveCategoryConfiguration(firstCategory.getId(), event.getId(), Collections.singletonList(new ConfigurationModification(null, ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION.getValue(), "1")), pair.getRight()+"_owner");
        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_PRE_REGISTRATION, "true");
        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_WAITING_QUEUE, "true");
        configurationRepository.insertEventLevel(event.getOrganizationId(), event.getId(), ConfigurationKeys.STOP_WAITING_QUEUE_SUBSCRIPTIONS.name(), "true", "");
        //subscription should now be denied
        boolean result = waitingQueueManager.subscribe(event, customerJohnDoe(event), "john@doe.com", null, Locale.ENGLISH);
        assertFalse(result);
    }

    @Test
    public void testDistributeSeatsFirstCategoryIsUnbounded() throws Exception {
        List<TicketCategoryModification> categories = getPreSalesTicketCategoryModifications(false, AVAILABLE_SEATS, true, 10);
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = pair.getKey();
        TicketCategory firstCategory = eventManager.loadTicketCategories(event).stream().filter(t->t.getName().equals("defaultFirst")).findFirst().orElseThrow(IllegalStateException::new);
        configurationManager.saveCategoryConfiguration(firstCategory.getId(), event.getId(), Collections.singletonList(new ConfigurationModification(null, ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION.getValue(), "1")), pair.getRight()+"_owner");
        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_PRE_REGISTRATION, "true");
        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_WAITING_QUEUE, "true");
        boolean result = waitingQueueManager.subscribe(event, customerJohnDoe(event), "john@doe.com", null, Locale.ENGLISH);
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
        List<TicketCategoryModification> categories = getPreSalesTicketCategoryModifications(true, 10, true, 10);
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = pair.getKey();
        TicketCategory firstCategory = eventManager.loadTicketCategories(event).stream().filter(t->t.getName().equals("defaultFirst")).findFirst().orElseThrow(IllegalStateException::new);
        configurationManager.saveCategoryConfiguration(firstCategory.getId(), event.getId(), Collections.singletonList(new ConfigurationModification(null, ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION.getValue(), "1")), pair.getRight()+"_owner");
        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_PRE_REGISTRATION, "true");
        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_WAITING_QUEUE, "true");
        boolean result = waitingQueueManager.subscribe(event, customerJohnDoe(event), "john@doe.com", null, Locale.ENGLISH);
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
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null));
        Event event = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository).getKey();
        TicketCategory unbounded = ticketCategoryRepository.findByEventId(event.getId()).get(0);

        TicketReservationModification tr = new TicketReservationModification();
        tr.setAmount(AVAILABLE_SEATS);
        tr.setTicketCategoryId(unbounded.getId());

        TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());
        String reservationId = ticketReservationManager.createTicketReservation(event, Collections.singletonList(mod), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.<String>empty(), Optional.<String>empty(), Locale.ENGLISH, false);
        TotalPrice reservationCost = ticketReservationManager.totalReservationCostWithVAT(reservationId);
        PaymentResult result = ticketReservationManager.confirm("", null, event, reservationId, "test@test.ch", new CustomerName("Full Name", "Full", "Name", event), Locale.ENGLISH, "", "", reservationCost, Optional.empty(), Optional.of(PaymentProxy.OFFLINE), false, null, null, null, false, false);
        assertTrue(result.isSuccessful());

        assertEquals(0, eventRepository.findStatisticsFor(event.getId()).getDynamicAllocation());
    }

    @Test
    public void testAssignTicketToWaitingQueueUnboundedCategory() {
        LocalDateTime start = LocalDateTime.now().minusMinutes(1);
        LocalDateTime end = LocalDateTime.now().plusMinutes(20);
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                new DateTimeModification(start.toLocalDate(), start.toLocalTime()),
                new DateTimeModification(end.toLocalDate(), end.toLocalTime()),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null));

        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_WAITING_QUEUE, "true");

        Event event = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository).getKey();

        TicketCategory unbounded = ticketCategoryRepository.findByEventId(event.getId()).get(0);

        TicketReservationModification tr = new TicketReservationModification();
        tr.setAmount(AVAILABLE_SEATS - 1);
        tr.setTicketCategoryId(unbounded.getId());

        TicketReservationModification tr2 = new TicketReservationModification();
        tr2.setAmount(1);
        tr2.setTicketCategoryId(unbounded.getId());

        TicketReservationWithOptionalCodeModification multi = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());
        TicketReservationWithOptionalCodeModification single = new TicketReservationWithOptionalCodeModification(tr2, Optional.empty());

        String reservationId = ticketReservationManager.createTicketReservation(event, Collections.singletonList(multi), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Optional.empty(), Locale.ENGLISH, false);
        TotalPrice reservationCost = ticketReservationManager.totalReservationCostWithVAT(reservationId);
        PaymentResult result = ticketReservationManager.confirm("", null, event, reservationId, "test@test.ch", new CustomerName("Full Name", "Full", "Name", event), Locale.ENGLISH, "", "", reservationCost, Optional.empty(), Optional.of(PaymentProxy.OFFLINE), false, null, null, null, false, false);
        assertTrue(result.isSuccessful());

        String reservationIdSingle = ticketReservationManager.createTicketReservation(event, Collections.singletonList(single), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Optional.empty(), Locale.ENGLISH, false);
        TotalPrice reservationCostSingle = ticketReservationManager.totalReservationCostWithVAT(reservationIdSingle);
        PaymentResult resultSingle = ticketReservationManager.confirm("", null, event, reservationIdSingle, "test@test.ch", new CustomerName("Full Name", "Full", "Name", event), Locale.ENGLISH, "", "", reservationCostSingle, Optional.empty(), Optional.of(PaymentProxy.OFFLINE), false, null, null, null, false, false);
        assertTrue(resultSingle.isSuccessful());


        assertEquals(0, eventRepository.findStatisticsFor(event.getId()).getDynamicAllocation());

        assertTrue(waitingQueueManager.subscribe(event, customerJohnDoe(event), "john@doe.com", null, Locale.ENGLISH));

        ticketReservationManager.deleteOfflinePayment(event, reservationIdSingle, false);

        List<Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime>> subscriptions = waitingQueueManager.distributeSeats(event).collect(Collectors.toList());
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
        LocalDateTime start = LocalDateTime.now().minusMinutes(2);
        LocalDateTime end = LocalDateTime.now().plusMinutes(20);
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                new DateTimeModification(start.toLocalDate(), start.toLocalTime()),
                new DateTimeModification(end.toLocalDate(), end.toLocalTime()),
                DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null));

        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_WAITING_QUEUE, "true");

        Event event = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository).getKey();

        TicketCategory bounded = ticketCategoryRepository.findByEventId(event.getId()).get(0);

        TicketReservationModification tr = new TicketReservationModification();
        tr.setAmount(AVAILABLE_SEATS - 1);
        tr.setTicketCategoryId(bounded.getId());

        TicketReservationModification tr2 = new TicketReservationModification();
        tr2.setAmount(1);
        tr2.setTicketCategoryId(bounded.getId());

        TicketReservationWithOptionalCodeModification multi = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());
        TicketReservationWithOptionalCodeModification single = new TicketReservationWithOptionalCodeModification(tr2, Optional.empty());

        String reservationId = ticketReservationManager.createTicketReservation(event, Collections.singletonList(multi), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Optional.empty(), Locale.ENGLISH, false);
        TotalPrice reservationCost = ticketReservationManager.totalReservationCostWithVAT(reservationId);
        PaymentResult result = ticketReservationManager.confirm("", null, event, reservationId, "test@test.ch", new CustomerName("Full Name", "Full", "Name", event), Locale.ENGLISH, "", "", reservationCost, Optional.empty(), Optional.of(PaymentProxy.OFFLINE), false, null, null, null, false, false);
        assertTrue(result.isSuccessful());

        String reservationIdSingle = ticketReservationManager.createTicketReservation(event, Collections.singletonList(single), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Optional.empty(), Locale.ENGLISH, false);
        TotalPrice reservationCostSingle = ticketReservationManager.totalReservationCostWithVAT(reservationIdSingle);
        PaymentResult resultSingle = ticketReservationManager.confirm("", null, event, reservationIdSingle, "test@test.ch", new CustomerName("Full Name", "Full", "Name", event), Locale.ENGLISH, "", "", reservationCostSingle, Optional.empty(), Optional.of(PaymentProxy.OFFLINE), false, null, null, null, false, false);
        assertTrue(resultSingle.isSuccessful());


        assertEquals(0, eventRepository.findStatisticsFor(event.getId()).getDynamicAllocation());

        assertTrue(waitingQueueManager.subscribe(event, customerJohnDoe(event), "john@doe.com", null, Locale.ENGLISH));

        ticketReservationManager.deleteOfflinePayment(event, reservationIdSingle, false);

        List<Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime>> subscriptions = waitingQueueManager.distributeSeats(event).collect(Collectors.toList());
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
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now().plusHours(1);

        List<TicketCategoryModification> categories = Arrays.asList(
            new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                new DateTimeModification(start.toLocalDate(), start.toLocalTime()),
                new DateTimeModification(end.toLocalDate(), end.toLocalTime()),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null),
            new TicketCategoryModification(null, "default2", AVAILABLE_SEATS,
                new DateTimeModification(start.toLocalDate(), start.toLocalTime()),
                new DateTimeModification(end.toLocalDate(), end.toLocalTime()),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null));

        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_WAITING_QUEUE, "true");

        Event event = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository).getKey();

        List<TicketCategory> ticketCategories = ticketCategoryRepository.findByEventId(event.getId());
        TicketCategory first = ticketCategories.get(0);
        TicketCategory second = ticketCategories.get(1);

        TicketReservationModification tr2 = new TicketReservationModification();
        tr2.setAmount(1);
        tr2.setTicketCategoryId(second.getId());

        TicketReservationModification tr3 = new TicketReservationModification();
        tr3.setAmount(1);
        tr3.setTicketCategoryId(first.getId());

        reserveTickets(event, first, AVAILABLE_SEATS - 2);

        String reservationIdSingleFirst = reserveTickets(event, second, 1);
        String reservationIdSingleSecond = reserveTickets(event, second, 1);

        assertEquals(0, eventRepository.findStatisticsFor(event.getId()).getDynamicAllocation());

        assertTrue(waitingQueueManager.subscribe(event, customerJohnDoe(event), "john@doe.com", first.getId(), Locale.ENGLISH));
        assertTrue(waitingQueueManager.subscribe(event, new CustomerName("John Doe 2", "John", "Doe 2", event), "john@doe2.com", second.getId(), Locale.ENGLISH));

        ticketReservationManager.deleteOfflinePayment(event, reservationIdSingleFirst, false);
        ticketReservationManager.deleteOfflinePayment(event, reservationIdSingleSecond, false);

        List<Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime>> subscriptions = waitingQueueManager.distributeSeats(event).collect(Collectors.toList());
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

    private String reserveTickets(Event event, TicketCategory category, int num) {
        TicketReservationModification tr = new TicketReservationModification();
        tr.setAmount(num);
        tr.setTicketCategoryId(category.getId());
        TicketReservationWithOptionalCodeModification tcm = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());
        String reservationId = ticketReservationManager.createTicketReservation(event, Collections.singletonList(tcm), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Optional.empty(), Locale.ENGLISH, false);
        TotalPrice reservationCost = ticketReservationManager.totalReservationCostWithVAT(reservationId);
        PaymentResult result = ticketReservationManager.confirm("", null, event, reservationId, "test@test.ch", new CustomerName("Full Name", "Full", "Name", event), Locale.ENGLISH, "", "", reservationCost, Optional.empty(), Optional.of(PaymentProxy.OFFLINE), false, null, null, null, false, false);
        assertTrue(result.isSuccessful());
        return reservationId;
    }

    @Test
    public void testNoPublicCategoryAvailable() {
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now().plusHours(1);

        List<TicketCategoryModification> categories = Arrays.asList(
            new TicketCategoryModification(null, "default", 2,
                new DateTimeModification(start.toLocalDate(), start.toLocalTime()),
                new DateTimeModification(end.toLocalDate(), end.toLocalTime()),
                DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null),
            new TicketCategoryModification(null, "default2", 10,
                new DateTimeModification(start.toLocalDate(), start.toLocalTime()),
                new DateTimeModification(end.toLocalDate(), end.toLocalTime()),
                DESCRIPTION, BigDecimal.TEN, true, "", true, null, null, null, null, null));

        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_WAITING_QUEUE, "true");
        Pair<Event, String> eventAndUsername = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = eventAndUsername.getKey();
        List<TicketCategory> ticketCategories = ticketCategoryRepository.findByEventId(event.getId());
        TicketCategory first = ticketCategories.stream().filter(tc -> !tc.isAccessRestricted()).findFirst().orElseThrow(IllegalStateException::new);
        reserveTickets(event, first, 2);
        assertTrue(waitingQueueManager.subscribe(event, customerJohnDoe(event), "john@doe.com", null, Locale.ENGLISH));
        assertTrue(waitingQueueManager.subscribe(event, new CustomerName("John Doe 2", "John", "Doe 2", event), "john@doe2.com", null, Locale.ENGLISH));

        ZoneId zoneId = event.getZoneId();
        Result<TicketCategory> ticketCategoryResult = eventManager.updateCategory(first.getId(), event, new TicketCategoryModification(first.getId(), first.getName(), 3,
            fromZonedDateTime(first.getInception(zoneId)), fromZonedDateTime(first.getExpiration(zoneId)), Collections.emptyMap(),
            first.getPrice(), false, "", true, null, null, null, null, null), eventAndUsername.getValue());
        assertTrue(ticketCategoryResult.isSuccess());
        assertEquals(1, ticketRepository.countReleasedTicketInCategory(event.getId(), first.getId()).intValue());
        //now we should have an extra available ticket
        List<Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime>> subscriptions = waitingQueueManager.distributeSeats(event).collect(Collectors.toList());
        assertEquals(1, subscriptions.size());
    }

    private List<TicketCategoryModification> getPreSalesTicketCategoryModifications(boolean firstBounded, int firstSeats, boolean lastBounded, int lastSeats) {
        LocalDateTime start = LocalDateTime.now().plusMinutes(4);
        return Arrays.asList(
            new TicketCategoryModification(null, "defaultFirst", firstSeats,
                new DateTimeModification(start.toLocalDate(), start.toLocalTime()),
                new DateTimeModification(LocalDate.now().plusDays(1), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", firstBounded, null, null, null, null, null),
            new TicketCategoryModification(null, "defaultLast", lastSeats,
                new DateTimeModification(LocalDate.now().plusDays(1), LocalTime.now()),
                new DateTimeModification(LocalDate.now().plusDays(2), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", lastBounded, null, null, null, null, null));
    }
}