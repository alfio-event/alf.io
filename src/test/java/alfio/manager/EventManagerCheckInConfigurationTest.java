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

import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.repository.SubscriptionRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.test.util.TestUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.Optional;

import static alfio.model.system.ConfigurationKeys.CHECK_IN_COLOR_CONFIGURATION;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("EventManager: handle category check-in configuration")
class EventManagerCheckInConfigurationTest {

    private Event event;
    private EventManager eventManager;
    private ConfigurationManager configurationManager;
    private ConfigurationRepository configurationRepository;
    private ConfigurationManager.MaybeConfiguration configuration;
    private final int eventId = 0;


    @BeforeEach
    void init() {
        event = mock(Event.class);
        when(event.getId()).thenReturn(eventId);
        when(event.getOrganizationId()).thenReturn(1);
        configurationManager = mock(ConfigurationManager.class);
        configurationRepository = mock(ConfigurationRepository.class);
        eventManager = new EventManager(null, null, null, null, null, null, null, null, configurationManager, null, null, null, null, null, null, null, null, null, null, null, configurationRepository, null, TestUtil.clockProvider(), mock(SubscriptionRepository.class));
        when(event.getZoneId()).thenReturn(ZoneId.systemDefault());
        configuration = mock(ConfigurationManager.MaybeConfiguration.class);
        when(configurationManager.getFor(eq(CHECK_IN_COLOR_CONFIGURATION), any())).thenReturn(configuration);
    }

    @Test
    void insertConfiguration() {
        when(configuration.getValue()).thenReturn(Optional.empty());
        eventManager.saveBadgeColorConfiguration("warning", event, 1);
        verify(configurationRepository).insertEventLevel(eq(1), eq(eventId), eq(CHECK_IN_COLOR_CONFIGURATION.name()), eq("{\"defaultColorName\":\"success\",\"configurations\":[{\"colorName\":\"warning\",\"categories\":[1]}]}"), isNull());
    }

    @Test
    void saveConfigurationNewColor() {
        var json = "{\"defaultColorName\":\"success\",\"configurations\":[{\"colorName\":\"info\",\"categories\":[5]}]}";
        when(configuration.getValue()).thenReturn(Optional.of(json));
        eventManager.saveBadgeColorConfiguration("warning", event, 1);
        verify(configurationRepository).updateEventLevel(eq(eventId), eq(1), eq(CHECK_IN_COLOR_CONFIGURATION.name()), eq("{\"defaultColorName\":\"success\",\"configurations\":[{\"colorName\":\"info\",\"categories\":[5]},{\"colorName\":\"warning\",\"categories\":[1]}]}"));
    }

    @Test
    void saveConfigurationExistingColor() {
        var json = "{\"defaultColorName\":\"success\",\"configurations\":[{\"colorName\":\"info\",\"categories\":[5]}]}";
        when(configuration.getValue()).thenReturn(Optional.of(json));
        eventManager.saveBadgeColorConfiguration("info", event, 1);
        verify(configurationRepository).updateEventLevel(eq(eventId), eq(1), eq(CHECK_IN_COLOR_CONFIGURATION.name()), eq("{\"defaultColorName\":\"success\",\"configurations\":[{\"colorName\":\"info\",\"categories\":[5,1]}]}"));
    }

    @Test
    void saveConfigurationModifyColor() {
        var json = "{\"defaultColorName\":\"success\",\"configurations\":[{\"colorName\":\"info\",\"categories\":[1]}]}";
        when(configuration.getValue()).thenReturn(Optional.of(json));
        eventManager.saveBadgeColorConfiguration("warning", event, 1);
        verify(configurationRepository).updateEventLevel(eq(eventId), eq(1), eq(CHECK_IN_COLOR_CONFIGURATION.name()), eq("{\"defaultColorName\":\"success\",\"configurations\":[{\"colorName\":\"warning\",\"categories\":[1]}]}"));
    }
}