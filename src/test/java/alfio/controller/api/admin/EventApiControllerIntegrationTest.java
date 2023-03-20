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
package alfio.controller.api.admin;

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.controller.api.ControllerConfiguration;
import alfio.manager.EventManager;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.TicketCategory;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.repository.EventDeleterRepository;
import alfio.repository.EventRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.AlfioIntegrationTest;
import alfio.test.util.IntegrationTestUtil;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static alfio.test.util.IntegrationTestUtil.*;
import static alfio.test.util.TestUtil.clockProvider;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@AlfioIntegrationTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, ControllerConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
class EventApiControllerIntegrationTest {

    @Autowired
    private ConfigurationRepository configurationRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserManager userManager;
    @Autowired
    private EventManager eventManager;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private EventDeleterRepository eventDeleterRepository;
    @Autowired
    private EventApiController eventApiController;

    private Event event;

    @Test
    void getAllEventsForExternalInPerson() {
        var eventAndUser = createEvent(Event.EventFormat.IN_PERSON);
        event = eventAndUser.getKey();
        var principal = Mockito.mock(Principal.class);
        when(principal.getName()).thenReturn(eventAndUser.getValue());
        var events = eventApiController.getAllEventsForExternal(principal, new MockHttpServletRequest(), false);

        assertNotNull(events);
        assertEquals(1, events.size());
        assertEquals(event.getShortName(), events.get(0).getKey());
    }

    @Test
    void getAllEventsForExternalHybrid() {
        var eventAndUser = createEvent(Event.EventFormat.HYBRID);
        event = eventAndUser.getKey();
        var principal = Mockito.mock(Principal.class);
        when(principal.getName()).thenReturn(eventAndUser.getValue());
        var events = eventApiController.getAllEventsForExternal(principal, new MockHttpServletRequest(), false);

        assertNotNull(events);
        assertEquals(1, events.size());
        assertEquals(event.getShortName(), events.get(0).getKey());
    }

    @Test
    void getAllEventsForExternalOnline() {
        var eventAndUser = createEvent(Event.EventFormat.ONLINE);
        event = eventAndUser.getKey();
        var principal = Mockito.mock(Principal.class);
        when(principal.getName()).thenReturn(eventAndUser.getValue());
        var events = eventApiController.getAllEventsForExternal(principal, new MockHttpServletRequest(), false);

        assertNotNull(events);
        assertEquals(0, events.size());

        events = eventApiController.getAllEventsForExternal(principal, new MockHttpServletRequest(), true);
        assertNotNull(events);
        assertEquals(1, events.size());
        assertEquals(event.getShortName(), events.get(0).getKey());
    }

    private Pair<Event,String> createEvent(Event.EventFormat format) {
        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);
        List<TicketCategoryModification> categories = List.of(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(clockProvider().getClock()).minusDays(1), LocalTime.now(clockProvider().getClock())),
                new DateTimeModification(LocalDate.now(clockProvider().getClock()).plusDays(1), LocalTime.now(clockProvider().getClock())),
                DESCRIPTION, BigDecimal.ZERO, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty())
        );
        return initEvent(categories, organizationRepository, userManager, eventManager, eventRepository, List.of(), format);

    }

    @AfterEach
    void tearDown() {
        eventDeleterRepository.deleteAllForEvent(event.getId());
    }
}