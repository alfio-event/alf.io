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
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.Ticket;
import alfio.model.TicketCategory;
import alfio.model.WaitingQueueSubscription;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.system.ConfigurationKeys;
import alfio.repository.TicketRepository;
import alfio.repository.TicketReservationRepository;
import alfio.repository.WaitingQueueRepository;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
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
import java.util.*;

import static alfio.test.util.IntegrationTestUtil.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS})
@Transactional
public class WaitingQueueProcessorIntegrationTest {

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

    @Before
    public void setup() {
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
                        "desc", BigDecimal.TEN, false, "", false));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager);
        Event event = pair.getKey();
        waitingQueueManager.subscribe(event, "Giuseppe Garibaldi", "peppino@garibaldi.com", Locale.ENGLISH);
        waitingQueueManager.subscribe(event, "Nino Bixio", "bixio@mille.org", Locale.ITALIAN);
        assertTrue(waitingQueueRepository.countWaitingPeople(event.getId()) == 2);

        waitingQueueSubscriptionProcessor.distributeAvailableSeats(event);
        assertEquals(18, ticketRepository.findFreeByEventId(event.getId()).size());

        TicketCategoryModification tcm = new TicketCategoryModification(null, "default", 10,
                new DateTimeModification(LocalDate.now().minusDays(1), LocalTime.now()),
                new DateTimeModification(LocalDate.now().plusDays(5), LocalTime.now()),
                "desc", BigDecimal.TEN, false, "", true);
        eventManager.insertCategory(event.getId(), tcm, pair.getValue());

        waitingQueueSubscriptionProcessor.distributeAvailableSeats(event);

        List<WaitingQueueSubscription> subscriptions = waitingQueueRepository.loadAll(event.getId());
        assertEquals(2, subscriptions.stream().filter(w -> StringUtils.isNotBlank(w.getReservationId())).count());
        assertTrue(subscriptions.stream().allMatch(w -> w.getStatus().equals(WaitingQueueSubscription.Status.PENDING)));

    }

    @Test
    public void testSoldOut() throws InterruptedException {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", 0,
                        new DateTimeModification(LocalDate.now().minusDays(1), LocalTime.now()),
                        new DateTimeModification(LocalDate.now().plusDays(2), LocalTime.now()),
                        "desc", BigDecimal.ZERO, false, "", true));

        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager);
        Event event = pair.getKey();
        TicketCategory ticketCategory = eventManager.loadTicketCategories(event).get(0);
        List<Integer> reserved = ticketRepository.selectFreeTicketsForPreReservation(event.getId(), ticketCategory.getId(), 20);
        String reservationId = UUID.randomUUID().toString();
        ticketReservationRepository.createNewReservation(reservationId, DateUtils.addHours(new Date(), 1), null);
        ticketRepository.reserveTickets(reservationId, reserved, ticketCategory.getId(), Locale.ITALIAN.getLanguage());
        ticketRepository.updateTicketsStatusWithReservationId(reservationId, Ticket.TicketStatus.ACQUIRED.name());

        //sold-out
        waitingQueueManager.subscribe(event, "Giuseppe Garibaldi", "peppino@garibaldi.com", Locale.ENGLISH);
        Thread.sleep(1L);//we are testing ordering, not concurrency...
        waitingQueueManager.subscribe(event, "Nino Bixio", "bixio@mille.org", Locale.ITALIAN);
        assertTrue(waitingQueueRepository.countWaitingPeople(event.getId()) == 2);

        //the following call shouldn't have any effect
        waitingQueueSubscriptionProcessor.distributeAvailableSeats(event);
        assertTrue(waitingQueueRepository.countWaitingPeople(event.getId()) == 2);

        Ticket firstTicket = ticketRepository.findTicketsInReservation(reservationId).get(0);

        ticketRepository.releaseTicket(reservationId, event.getId(), firstTicket.getId());

        waitingQueueSubscriptionProcessor.distributeAvailableSeats(event);

        List<WaitingQueueSubscription> subscriptions = waitingQueueRepository.loadAll(event.getId());
        assertEquals(1, subscriptions.stream().filter(w -> StringUtils.isNotBlank(w.getReservationId())).count());
        Optional<WaitingQueueSubscription> first = subscriptions.stream().filter(w -> w.getStatus().equals(WaitingQueueSubscription.Status.PENDING)).findFirst();
        assertTrue(first.isPresent());
        assertEquals("Giuseppe Garibaldi", first.get().getFullName());

    }
}
