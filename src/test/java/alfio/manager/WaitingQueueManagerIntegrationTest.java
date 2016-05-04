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
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.TicketCategory;
import alfio.model.WaitingQueueSubscription;
import alfio.model.modification.*;
import alfio.model.system.ConfigurationKeys;
import alfio.repository.user.OrganizationRepository;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
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

import static alfio.test.util.IntegrationTestUtil.AVAILABLE_SEATS;
import static alfio.test.util.IntegrationTestUtil.initEvent;
import static alfio.test.util.IntegrationTestUtil.initSystemProperties;
import static org.junit.Assert.*;

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

    @BeforeClass
    public static void initEnv() {
        initSystemProperties();
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
        boolean result = waitingQueueManager.subscribe(event, "John Doe", "john@doe.com", Locale.ENGLISH);
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
        boolean result = waitingQueueManager.subscribe(event, "John Doe", "john@doe.com", Locale.ENGLISH);
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