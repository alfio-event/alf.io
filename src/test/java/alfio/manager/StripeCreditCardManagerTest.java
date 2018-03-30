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

import java.util.Optional;
import java.util.function.Function;

import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.repository.TicketRepository;
import com.stripe.exception.APIConnectionException;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.CardException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.net.RequestOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static alfio.model.system.ConfigurationKeys.PLATFORM_MODE_ENABLED;
import static alfio.model.system.ConfigurationKeys.STRIPE_CONNECTED_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StripeCreditCardManagerTest {

    private ConfigurationManager configurationManager;
    private TicketRepository ticketRepository;
    private Event event;

    private StripeCreditCardManager stripeCreditCardManager;

    @BeforeEach
    public void init() {
        configurationManager = mock(ConfigurationManager.class);
        ticketRepository = mock(TicketRepository.class);
        event = mock(Event.class);
        stripeCreditCardManager = new StripeCreditCardManager(configurationManager, ticketRepository, null, null, null);
    }

    @Test
    public void testCardExceptionHandler() {
        assertEquals("error.STEP2_STRIPE_houston_we_ve_a_problem", stripeCreditCardManager.handleException(new CardException("", "abcd", "houston_we_ve_a_problem", "param", null, null, null, null)));
    }

    @Test
    public void testInvalidRequestExceptionHandler() {
        assertEquals("error.STEP2_STRIPE_invalid_param", stripeCreditCardManager.handleException(new InvalidRequestException("abcd", "param", null, null, null)));
    }

    @Test
    public void testAuthenticationExceptionHandler() {
        assertEquals("error.STEP2_STRIPE_abort", stripeCreditCardManager.handleException(new AuthenticationException("abcd", null, 401)));
    }

    @Test
    public void testApiConnectionException() {
        assertEquals("error.STEP2_STRIPE_abort", stripeCreditCardManager.handleException(new APIConnectionException("abcd")));
    }

    @Test
    public void testUnexpectedError() {
        assertEquals("error.STEP2_STRIPE_unexpected", stripeCreditCardManager.handleException( new StripeException("", null, 42) {}));
    }

    @Test
    public void testMissingStripeConnectedId() {
        Function<ConfigurationKeys, Configuration.ConfigurationPathKey> partial = Configuration.from(event.getOrganizationId(), event.getId());
        when(configurationManager.getBooleanConfigValue(partial.apply(PLATFORM_MODE_ENABLED), false)).thenReturn(true);
        when(configurationManager.getStringConfigValue(partial.apply(STRIPE_CONNECTED_ID))).thenReturn(Optional.empty());
        Optional<RequestOptions> options = stripeCreditCardManager.options(event);
        assertNotNull(options);
        assertFalse(options.isPresent());
    }
}