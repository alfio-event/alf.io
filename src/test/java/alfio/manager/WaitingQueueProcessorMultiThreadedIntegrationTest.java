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
import alfio.model.CustomerName;
import alfio.model.Event;
import alfio.model.TicketCategory;
import alfio.model.WaitingQueueSubscription;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.system.ConfigurationKeys;
import alfio.repository.EventRepository;
import alfio.repository.TicketRepository;
import alfio.repository.WaitingQueueRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.ClockProvider;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static alfio.test.util.IntegrationTestUtil.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
public class WaitingQueueProcessorMultiThreadedIntegrationTest {

    private static final Map<String, String> DESCRIPTION = Collections.singletonMap("en", "desc");

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
    private ConfigurationRepository configurationRepository;
    @Autowired
    private EventRepository eventRepository;

    @Test
    public void testPreRegistration() throws InterruptedException {

        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);
        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_PRE_REGISTRATION, "true");
        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_WAITING_QUEUE, "true");
        initAdminUser(userRepository, authorityRepository);
        Event event = null;
        try {

            List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 10,
                    new DateTimeModification(LocalDate.now(ClockProvider.clock()).plusDays(1), LocalTime.now(ClockProvider.clock())),
                    new DateTimeModification(LocalDate.now(ClockProvider.clock()).plusDays(2), LocalTime.now(ClockProvider.clock())),
                    DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
            Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
            event = pair.getKey();
            waitingQueueManager.subscribe(event, new CustomerName("Giuseppe Garibaldi", "Giuseppe", "Garibaldi", event.mustUseFirstAndLastName()), "peppino@garibaldi.com", null, Locale.ENGLISH);
            waitingQueueManager.subscribe(event, new CustomerName("Nino Bixio", "Nino", "Bixio", event.mustUseFirstAndLastName()), "bixio@mille.org", null, Locale.ITALIAN);
            assertEquals(2, (int) waitingQueueRepository.countWaitingPeople(event.getId()));


            final int parallelism = 10;
            List<Callable<Void>> calls = new ArrayList<>(parallelism);
            ExecutorService executor = Executors.newFixedThreadPool(parallelism);
            final Event eventF = event;
            for (int i = 0; i < parallelism; i++) {
                calls.add(() -> {
                    waitingQueueSubscriptionProcessor.distributeAvailableSeats(eventF);
                    return null;
                });
            }

            executor.invokeAll(calls);
            executor.shutdown();
            while(!executor.awaitTermination(10, TimeUnit.MILLISECONDS)) {
            }
            assertEquals(18, ticketRepository.findFreeByEventId(event.getId()).size());

            TicketCategoryModification tcm = new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 10,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()).minusDays(1), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()).plusDays(5), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty());
            eventManager.insertCategory(event.getId(), tcm, pair.getValue());


            //System.err.println("------------------------------------------------");

            executor = Executors.newFixedThreadPool(parallelism);
            calls.clear();
            for(int i = 0; i < parallelism; i++) {
                calls.add(() -> {
                    waitingQueueSubscriptionProcessor.distributeAvailableSeats(eventF);
                    return null;
                });
            }
            executor.invokeAll(calls);
            executor.shutdown();
            while(!executor.awaitTermination(10, TimeUnit.MILLISECONDS)) {
            }


            List<WaitingQueueSubscription> subscriptions = waitingQueueRepository.loadAll(event.getId());
            assertEquals(2, subscriptions.stream().filter(w -> StringUtils.isNotBlank(w.getReservationId())).count());
            assertTrue(subscriptions.stream().allMatch(w -> w.getStatus().equals(WaitingQueueSubscription.Status.PENDING)));
            assertTrue(subscriptions.stream().allMatch(w -> w.getSubscriptionType().equals(WaitingQueueSubscription.Type.PRE_SALES)));

        } finally {
            if(event != null) {
                eventManager.deleteEvent(event.getId(), UserManager.ADMIN_USERNAME);
            }
            configurationManager.deleteKey(ConfigurationKeys.ENABLE_PRE_REGISTRATION.name());
            configurationManager.deleteKey(ConfigurationKeys.ENABLE_WAITING_QUEUE.name());
            removeAdminUser(userRepository, authorityRepository);
        }

    }
}
