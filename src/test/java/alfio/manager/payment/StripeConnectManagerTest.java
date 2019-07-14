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
import alfio.manager.payment.stripe.StripeConnectURL;
import alfio.manager.system.ConfigurationManager;
import alfio.repository.TicketRepository;
import alfio.repository.system.ConfigurationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
        when(configurationManager.getRequiredValue(any())).thenReturn("");
        when(extensionManager.generateStripeConnectStateParam(anyInt())).thenReturn(Optional.of(state));
        StripeConnectURL connectURL = stripeConnectManager.getConnectURL(1);
        assertEquals(state, connectURL.getState());
    }

    @Test
    void getConnectURLWithStandardState() {
        String state = "blablastate";
        when(configurationManager.getRequiredValue(any())).thenReturn("");
        when(extensionManager.generateStripeConnectStateParam(anyInt())).thenReturn(Optional.empty());
        StripeConnectURL connectURL = stripeConnectManager.getConnectURL(1);
        assertNotEquals(state, connectURL.getState());
    }
}