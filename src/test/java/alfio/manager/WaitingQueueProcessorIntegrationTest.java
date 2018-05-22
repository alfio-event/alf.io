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
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.*;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.EventModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.system.ConfigurationKeys;
import alfio.repository.EventRepository;
import alfio.repository.TicketRepository;
import alfio.repository.TicketReservationRepository;
import alfio.repository.WaitingQueueRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.test.util.IntegrationTestUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
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

import static alfio.model.modification.DateTimeModification.fromZonedDateTime;
import static alfio.test.util.IntegrationTestUtil.*;
import static java.util.Collections.emptyMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {RepositoryConfiguration.class, DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS})
@Transactional
public class WaitingQueueProcessorIntegrationTest {

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
    private UserRepository userRepository;
    @Autowired
    private AuthorityRepository authorityRepository;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private WaitingQueueSubscriptionProcessor waitingQueueSubscriptionProcessor;
    @Autowired
    private WaitingQueueManager waitingQueueManager;
    @Autowired
    private WaitingQueueRepository waitingQueueRepository;
    @Autowired
    private ConfigurationManager configurationManager;
    @Autowired
    private TicketReservationRepository ticketReservationRepository;
    @Autowired
    private ConfigurationRepository configurationRepository;
    @Autowired
    private EventRepository eventRepository;

    @Before
    public void setup() {
        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);
        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_PRE_REGISTRATION, "true");
        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_WAITING_QUEUE, "true");
        initAdminUser(userRepository, authorityRepository);
    }

    @Test
    public void testPreRegistration() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", 10,
                        new DateTimeModification(LocalDate.now().plusDays(1), LocalTime.now()),
                        new DateTimeModification(LocalDate.now().plusDays(2), LocalTime.now()),
                        DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = pair.getKey();
        waitingQueueManager.subscribe(event, new CustomerName("Giuseppe Garibaldi", "Giuseppe", "Garibaldi", event), "peppino@garibaldi.com", null, Locale.ENGLISH);
        waitingQueueManager.subscribe(event, new CustomerName("Nino Bixio", "Nino", "Bixio", event), "bixio@mille.org", null, Locale.ITALIAN);
        assertTrue(waitingQueueRepository.countWaitingPeople(event.getId()) == 2);

        waitingQueueSubscriptionProcessor.distributeAvailableSeats(event);
        assertEquals(18, ticketRepository.findFreeByEventId(event.getId()).size());

        TicketCategoryModification tcm = new TicketCategoryModification(null, "default", 10,
                new DateTimeModification(LocalDate.now().minusDays(1), LocalTime.now()),
                new DateTimeModification(LocalDate.now().plusDays(5), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null);
        eventManager.insertCategory(event.getId(), tcm, pair.getValue());

        waitingQueueSubscriptionProcessor.distributeAvailableSeats(event);

        List<WaitingQueueSubscription> subscriptions = waitingQueueRepository.loadAll(event.getId());
        assertEquals(2, subscriptions.stream().filter(w -> StringUtils.isNotBlank(w.getReservationId())).count());
        assertTrue(subscriptions.stream().allMatch(w -> w.getStatus().equals(WaitingQueueSubscription.Status.PENDING)));
        assertTrue(subscriptions.stream().allMatch(w -> w.getSubscriptionType().equals(WaitingQueueSubscription.Type.PRE_SALES)));

    }

    @Test
    public void testSoldOut() throws InterruptedException {
        Pair<String, Event> pair = initSoldOutEvent(true);
        Event event = pair.getRight();
        String reservationId = pair.getLeft();
        Ticket firstTicket = ticketRepository.findTicketsInReservation(reservationId).get(0);
        ticketRepository.releaseTicket(reservationId, UUID.randomUUID().toString(), event.getId(), firstTicket.getId());
        waitingQueueSubscriptionProcessor.distributeAvailableSeats(event);
        List<WaitingQueueSubscription> subscriptions =  waitingQueueRepository.loadAll(event.getId());
        assertEquals(1, subscriptions.stream().filter(w -> StringUtils.isNotBlank(w.getReservationId())).count());
        Optional<WaitingQueueSubscription> first = subscriptions.stream().filter(w -> w.getStatus().equals(WaitingQueueSubscription.Status.PENDING)).findFirst();
        assertTrue(first.isPresent());
        assertEquals("Giuseppe Garibaldi", first.get().getFullName());
    }

    @Test
    public void testAddSeatsAfterSoldOut() throws InterruptedException {
        Pair<String, Event> pair = initSoldOutEvent(true);
        Event event = pair.getRight();
        EventModification eventModification = new EventModification(event.getId(), event.getType(), event.getWebsiteUrl(),
            event.getExternalUrl(), event.getTermsAndConditionsUrl(), event.getPrivacyPolicyUrl(), event.getImageUrl(), event.getFileBlobId(), event.getShortName(), event.getDisplayName(),
            event.getOrganizationId(), event.getLocation(), event.getLatitude(), event.getLongitude(), event.getZoneId().getId(), emptyMap(), fromZonedDateTime(event.getBegin()), fromZonedDateTime(event.getEnd()),
            event.getRegularPrice(), event.getCurrency(), eventRepository.countExistingTickets(event.getId()) + 1, event.getVat(), event.isVatIncluded(), event.getAllowedPaymentProxies(),
            Collections.emptyList(), event.isFreeOfCharge(), null, event.getLocales(), Collections.emptyList(), Collections.emptyList());
        eventManager.updateEventPrices(event, eventModification, "admin");
        //that should create an additional "RELEASED" ticket
        waitingQueueSubscriptionProcessor.distributeAvailableSeats(event);
        List<WaitingQueueSubscription> subscriptions =  waitingQueueRepository.loadAll(event.getId());
        assertEquals(1, subscriptions.stream().filter(w -> StringUtils.isNotBlank(w.getReservationId())).count());
        Optional<WaitingQueueSubscription> first = subscriptions.stream().filter(w -> w.getStatus().equals(WaitingQueueSubscription.Status.PENDING)).findFirst();
        assertTrue(first.isPresent());
        assertEquals("Giuseppe Garibaldi", first.get().getFullName());
    }

    @Test
    public void testAddSeatsAfterSoldOutWithoutUnbounded() throws InterruptedException {
        Pair<String, Event> pair = initSoldOutEvent(false);
        Event event = pair.getRight();
        EventModification eventModification = new EventModification(event.getId(), event.getType(), event.getWebsiteUrl(),
            event.getExternalUrl(), event.getTermsAndConditionsUrl(), event.getPrivacyPolicyUrl(), event.getImageUrl(), event.getFileBlobId(), event.getShortName(), event.getDisplayName(),
            event.getOrganizationId(), event.getLocation(), event.getLatitude(), event.getLongitude(), event.getZoneId().getId(), emptyMap(), fromZonedDateTime(event.getBegin()), fromZonedDateTime(event.getEnd()),
            event.getRegularPrice(), event.getCurrency(), eventRepository.countExistingTickets(event.getId()) + 1, event.getVat(), event.isVatIncluded(), event.getAllowedPaymentProxies(),
            Collections.emptyList(), event.isFreeOfCharge(), null, event.getLocales(), Collections.emptyList(), Collections.emptyList());
        eventManager.updateEventPrices(event, eventModification, "admin");
        //that should create an additional "RELEASED" ticket, but it won't be linked to any category, so the following call won't have any effect
        waitingQueueSubscriptionProcessor.distributeAvailableSeats(event);
        List<WaitingQueueSubscription> subscriptions =  waitingQueueRepository.loadAll(event.getId());
        assertEquals(0, subscriptions.stream().filter(w -> StringUtils.isNotBlank(w.getReservationId())).count());

        //explicitly expand the category
        TicketCategory category = eventManager.loadTicketCategories(event).get(0);
        eventManager.updateCategory(category.getId(), event.getId(), new TicketCategoryModification(category.getId(), category.getName(), category.getMaxTickets() + 1,
            fromZonedDateTime(category.getInception(event.getZoneId())), fromZonedDateTime(category.getExpiration(event.getZoneId())), emptyMap(), category.getPrice(),
            category.isAccessRestricted(), "", category.isBounded(), null, null, null, null, null), "admin");

        //now the waiting queue processor should create the reservation for the first in line
        waitingQueueSubscriptionProcessor.distributeAvailableSeats(event);
        subscriptions =  waitingQueueRepository.loadAll(event.getId());
        assertEquals(1, subscriptions.stream().filter(w -> StringUtils.isNotBlank(w.getReservationId())).count());
        Optional<WaitingQueueSubscription> first = subscriptions.stream().filter(w -> w.getStatus().equals(WaitingQueueSubscription.Status.PENDING)).findFirst();
        assertTrue(first.isPresent());
        assertEquals("Giuseppe Garibaldi", first.get().getFullName());
    }

    private Pair<String, Event> initSoldOutEvent(boolean withUnboundedCategory) throws InterruptedException {
        int boundedCategorySize = AVAILABLE_SEATS - (withUnboundedCategory ? 1 : 0);
        List<TicketCategoryModification> categories = new ArrayList<>();
        categories.add(
            new TicketCategoryModification(null, "default", boundedCategorySize,
                new DateTimeModification(LocalDate.now().minusDays(1), LocalTime.now()),
                new DateTimeModification(LocalDate.now().plusDays(2), LocalTime.now()),
                DESCRIPTION, BigDecimal.ZERO, false, "", true, null, null, null, null, null));

        if(withUnboundedCategory) {
             categories.add(new TicketCategoryModification(null, "unbounded", 0,
                 new DateTimeModification(LocalDate.now().minusDays(1), LocalTime.now()),
                 new DateTimeModification(LocalDate.now().plusDays(2), LocalTime.now()),
                 DESCRIPTION, BigDecimal.ZERO, false, "", false, null, null, null, null, null));
        }

        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = pair.getKey();
        List<TicketCategory> ticketCategories = eventManager.loadTicketCategories(event);
        TicketCategory bounded = ticketCategories.stream().filter(t->t.getName().equals("default")).findFirst().orElseThrow(IllegalStateException::new);
        List<Integer> boundedReserved = ticketRepository.selectFreeTicketsForPreReservation(event.getId(), 20, bounded.getId());
        assertEquals(boundedCategorySize, boundedReserved.size());
        List<Integer> reserved = new ArrayList<>(boundedReserved);
        String reservationId = UUID.randomUUID().toString();
        ticketReservationRepository.createNewReservation(reservationId, ZonedDateTime.now(), DateUtils.addHours(new Date(), 1), null, Locale.ITALIAN.getLanguage(), event.getId(), event.getVat(), event.isVatIncluded());
        List<Integer> reservedForUpdate = withUnboundedCategory ? reserved.subList(0, 19) : reserved;
        ticketRepository.reserveTickets(reservationId, reservedForUpdate, bounded.getId(), Locale.ITALIAN.getLanguage(), 0);
        if(withUnboundedCategory) {
            TicketCategory unbounded = ticketCategories.stream().filter(t->t.getName().equals("unbounded")).findFirst().orElseThrow(IllegalStateException::new);
            List<Integer> unboundedReserved = ticketRepository.selectNotAllocatedFreeTicketsForPreReservation(event.getId(), 20);
            assertEquals(1, unboundedReserved.size());
            reserved.addAll(unboundedReserved);
            ticketRepository.reserveTickets(reservationId, reserved.subList(19, 20), unbounded.getId(), Locale.ITALIAN.getLanguage(), 0);
        }
        ticketRepository.updateTicketsStatusWithReservationId(reservationId, Ticket.TicketStatus.ACQUIRED.name());

        //sold-out
        waitingQueueManager.subscribe(event, new CustomerName("Giuseppe Garibaldi", "Giuseppe", "Garibaldi", event), "peppino@garibaldi.com", null, Locale.ENGLISH);
        Thread.sleep(100L);//we are testing ordering, not concurrency...
        waitingQueueManager.subscribe(event, new CustomerName("Nino Bixio", "Nino", "Bixio", event), "bixio@mille.org", null, Locale.ITALIAN);
        List<WaitingQueueSubscription> subscriptions = waitingQueueRepository.loadAll(event.getId());
        assertTrue(waitingQueueRepository.countWaitingPeople(event.getId()) == 2);
        assertTrue(subscriptions.stream().allMatch(w -> w.getSubscriptionType().equals(WaitingQueueSubscription.Type.SOLD_OUT)));

        //the following call shouldn't have any effect
        waitingQueueSubscriptionProcessor.distributeAvailableSeats(event);
        assertTrue(waitingQueueRepository.countWaitingPeople(event.getId()) == 2);
        return Pair.of(reservationId, event);
    }
}
