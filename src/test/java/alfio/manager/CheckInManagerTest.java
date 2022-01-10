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

import alfio.manager.support.CheckInStatistics;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.system.ConfigurationKeyValuePathLevel;
import alfio.model.user.Organization;
import alfio.repository.EventRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.TestUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Optional;

import static alfio.model.system.ConfigurationKeys.CHECK_IN_STATS;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class CheckInManagerTest {

    private EventRepository eventRepository;
    private ConfigurationManager configurationManager;
    private CheckInManager checkInManager;

    private static final String EVENT_NAME = "eventName";
    private static final String USERNAME = "username";
    private static final int EVENT_ID = 0;
    private static final int ORG_ID = 1;


    @BeforeEach
    public void setUp() {
        eventRepository = mock(EventRepository.class);
        configurationManager = mock(ConfigurationManager.class);
        OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
        Event event = mock(Event.class);
        Organization organization = mock(Organization.class);
        ConfigurationLevel cl = ConfigurationLevel.event(event);
        when(event.getConfigurationLevel()).thenReturn(cl);
        when(eventRepository.findOptionalByShortName(EVENT_NAME)).thenReturn(Optional.of(event));
        when(event.getId()).thenReturn(EVENT_ID);
        when(event.getOrganizationId()).thenReturn(ORG_ID);
        when(organizationRepository.findOrganizationForUser(USERNAME, ORG_ID)).thenReturn(Optional.of(organization));
        when(organization.getId()).thenReturn(ORG_ID);
        when(eventRepository.retrieveCheckInStatisticsForEvent(EVENT_ID)).thenReturn(new CheckInStatistics(0, 0, new Date()));
        checkInManager = new CheckInManager(null, eventRepository, null, null, null, null,
            null, configurationManager, organizationRepository, null, null, null, null, null, TestUtil.clockProvider());
    }

    @Test
    void getStatistics() {
        when(configurationManager.getFor(eq(CHECK_IN_STATS), any(ConfigurationLevel.class)))
            .thenReturn(new ConfigurationManager.MaybeConfiguration(CHECK_IN_STATS, new ConfigurationKeyValuePathLevel(null, "true", null)));
        CheckInStatistics statistics = checkInManager.getStatistics(EVENT_NAME, USERNAME);
        assertNotNull(statistics);
        verify(eventRepository).retrieveCheckInStatisticsForEvent(EVENT_ID);
    }

    @Test
    void getStatisticsDisabled() {
        when(configurationManager.getFor(eq(CHECK_IN_STATS), any(ConfigurationLevel.class)))
            .thenReturn(new ConfigurationManager.MaybeConfiguration(CHECK_IN_STATS, new ConfigurationKeyValuePathLevel(null, "false", null)));
        CheckInStatistics statistics = checkInManager.getStatistics(EVENT_NAME, USERNAME);
        assertNull(statistics);
        verify(eventRepository, never()).retrieveCheckInStatisticsForEvent(EVENT_ID);
    }


}