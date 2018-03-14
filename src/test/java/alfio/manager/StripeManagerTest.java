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
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.repository.TicketRepository;
import com.stripe.exception.*;
import com.stripe.net.RequestOptions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Optional;
import java.util.function.Function;

import static alfio.model.system.ConfigurationKeys.PLATFORM_MODE_ENABLED;
import static alfio.model.system.ConfigurationKeys.STRIPE_CONNECTED_ID;
import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class StripeManagerTest {

    @Mock
    private ConfigurationManager configurationManager;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private Event event;

    private StripeManager stripeManager;

    @Before
    public void init() {
        stripeManager = new StripeManager(configurationManager, ticketRepository, null, null);
    }

    @Test
    public void testCardExceptionHandler() {
        assertEquals("error.STEP2_STRIPE_houston_we_ve_a_problem", stripeManager.handleException(new CardException("", "abcd", "houston_we_ve_a_problem", "param", null, null, null, null)));
    }

    @Test
    public void testInvalidRequestExceptionHandler() {
        assertEquals("error.STEP2_STRIPE_invalid_param", stripeManager.handleException(new InvalidRequestException("abcd", "param", null, null, null)));
    }

    @Test
    public void testAuthenticationExceptionHandler() {
        assertEquals("error.STEP2_STRIPE_abort", stripeManager.handleException(new AuthenticationException("abcd", null, 401)));
    }

    @Test
    public void testApiConnectionException() {
        assertEquals("error.STEP2_STRIPE_abort", stripeManager.handleException(new APIConnectionException("abcd")));
    }

    @Test
    public void testUnexpectedError() {
        assertEquals("error.STEP2_STRIPE_unexpected", stripeManager.handleException(new StripeException("", null, 42) {}));
    }

    @Test
    public void testMissingStripeConnectedId() {
        Function<ConfigurationKeys, Configuration.ConfigurationPathKey> partial = Configuration.from(event.getOrganizationId(), event.getId());
        when(configurationManager.getBooleanConfigValue(partial.apply(PLATFORM_MODE_ENABLED), false)).thenReturn(true);
        when(configurationManager.getStringConfigValue(partial.apply(STRIPE_CONNECTED_ID))).thenReturn(Optional.empty());
        Optional<RequestOptions> options = stripeManager.options(event);
        assertNotNull(options);
        assertFalse(options.isPresent());
    }
}