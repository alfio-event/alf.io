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
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.Ticket;
import alfio.model.TicketCategory;
import alfio.model.attendeelist.AttendeeList;
import alfio.model.attendeelist.AttendeeListConfiguration;
import alfio.model.modification.*;
import alfio.repository.AttendeeListRepository;
import alfio.repository.EventRepository;
import alfio.repository.TicketRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.test.util.IntegrationTestUtil;
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
import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {RepositoryConfiguration.class, DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS})
@Transactional
public class AttendeeListManagerIntegrationTest {

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
    private ConfigurationRepository configurationRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private AttendeeListRepository attendeeListRepository;
    @Autowired
    private AttendeeListManager attendeeListManager;
    @Autowired
    private TicketReservationManager ticketReservationManager;

    @BeforeClass
    public static void initEnv() {
        initSystemProperties();
    }

    @Before
    public void setup() {
        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);
        initAdminUser(userRepository, authorityRepository);
    }

    @Test
    public void testLinkToEvent() {

        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", 10,
                new DateTimeModification(LocalDate.now().plusDays(1), LocalTime.now()),
                new DateTimeModification(LocalDate.now().plusDays(2), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = pair.getKey();
        AttendeeList attendeeList = attendeeListManager.createNew("test", "This is a test", event.getOrganizationId());
        assertNotNull(attendeeList);
        AttendeeListConfigurationModification modification = new AttendeeListConfigurationModification(null, attendeeList.getId(), event.getId(), null, AttendeeListConfiguration.Type.ONCE_PER_VALUE, AttendeeListConfiguration.MatchType.FULL, null);
        AttendeeListConfiguration configuration = attendeeListManager.createConfiguration(attendeeList.getId(), event.getId(), modification);
        assertNotNull(configuration);
        List<TicketCategory> ticketCategories = eventManager.loadTicketCategories(event);
        int categoryId = ticketCategories.get(0).getId();
        assertTrue(attendeeListManager.isAttendeeListConfiguredFor(event.getId(), categoryId));
        List<AttendeeListConfiguration> activeConfigurations = attendeeListRepository.findActiveConfigurationsFor(event.getId(), categoryId);
        assertFalse(activeConfigurations.isEmpty());
        assertEquals(1, activeConfigurations.size());
        assertEquals(configuration.getId(), activeConfigurations.get(0).getId());
        assertFalse("AttendeeList is empty, therefore no value is allowed", attendeeListManager.isAllowed("test@test.ch", event.getId(), categoryId));
        int items = attendeeListManager.insertItems(attendeeList.getId(), Collections.singletonList(new AttendeeListItemModification(null,"test@test.ch", "description")));
        assertEquals(1, items);
        assertTrue("Value should be allowed", attendeeListManager.isAllowed("test@test.ch", event.getId(), categoryId));

        TicketReservationModification ticketReservation = new TicketReservationModification();
        ticketReservation.setAmount(1);
        ticketReservation.setTicketCategoryId(categoryId);

        String reservationId = ticketReservationManager.createTicketReservation(event, Collections.singletonList(new TicketReservationWithOptionalCodeModification(ticketReservation, Optional.empty())),
            Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Optional.empty(), Locale.ENGLISH, false);

        Ticket ticket = ticketRepository.findFirstTicketInReservation(reservationId).orElseThrow(NullPointerException::new);
        ticketRepository.updateTicketOwnerById(ticket.getId(), "test@test.ch", "This is a Test", "This is", "a Test");

        ticket = ticketRepository.findFirstTicketInReservation(reservationId).orElseThrow(NullPointerException::new);
        assertTrue("cannot confirm ticket", attendeeListManager.acquireItemForTicket(ticket, "not-valid"));

        reservationId = ticketReservationManager.createTicketReservation(event, Collections.singletonList(new TicketReservationWithOptionalCodeModification(ticketReservation, Optional.empty())),
            Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Optional.empty(), Locale.ENGLISH, false);

        ticket = ticketRepository.findFirstTicketInReservation(reservationId).orElseThrow(NullPointerException::new);
        assertFalse("shouldn't be allowed", attendeeListManager.acquireItemForTicket(ticket, "not-valid"));

    }
}