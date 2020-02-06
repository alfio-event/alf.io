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
package alfio.manager.payment;

import alfio.manager.ExtensionManager;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.model.system.ConfigurationKeys;
import alfio.repository.TicketRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.util.oauth2.AuthorizationRequestDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StripeConnectManagerTest {

    private StripeConnectManager stripeConnectManager;
    private ExtensionManager extensionManager;
    private ConfigurationManager configurationManager;

    @BeforeEach
    void setUp() {
        extensionManager = mock(ExtensionManager.class);
        configurationManager = mock(ConfigurationManager.class);
        stripeConnectManager = new StripeConnectManager(extensionManager, configurationManager, mock(ConfigurationRepository.class), mock(TicketRepository.class), mock(Environment.class));
    }

    @Test
    void getConnectURLWithCustomState() {
        String state = "blablastate";
        @SuppressWarnings("unchecked")
        Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> map = mock(Map.class);
        ConfigurationManager.MaybeConfiguration maybeConfiguration = mock(ConfigurationManager.MaybeConfiguration.class);
        when(map.get(any(ConfigurationKeys.class))).thenReturn(maybeConfiguration);
        when(maybeConfiguration.getRequiredValue()).thenReturn("");
        when(configurationManager.getFor(anyCollection(), any(ConfigurationLevel.class))).thenReturn(map);
        when(extensionManager.generateOAuth2StateParam(anyInt())).thenReturn(Optional.of(state));
        AuthorizationRequestDetails connectURL = stripeConnectManager.getConnectURL(1);
        assertEquals(state, connectURL.getState());
    }

    @Test
    void getConnectURLWithStandardState() {
        String state = "blablastate";
        @SuppressWarnings("unchecked")
        Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> map = mock(Map.class);
        ConfigurationManager.MaybeConfiguration maybeConfiguration = mock(ConfigurationManager.MaybeConfiguration.class);
        when(map.get(any(ConfigurationKeys.class))).thenReturn(maybeConfiguration);
        when(maybeConfiguration.getRequiredValue()).thenReturn("");
        when(configurationManager.getFor(anyCollection(), any(ConfigurationLevel.class))).thenReturn(map);
        when(extensionManager.generateOAuth2StateParam(anyInt())).thenReturn(Optional.empty());
        AuthorizationRequestDetails connectURL = stripeConnectManager.getConnectURL(1);
        assertNotEquals(state, connectURL.getState());
    }
}