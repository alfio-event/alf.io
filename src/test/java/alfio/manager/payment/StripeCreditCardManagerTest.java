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

import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.repository.TicketRepository;
import com.stripe.exception.*;
import com.stripe.net.RequestOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.function.Function;

import static alfio.model.system.ConfigurationKeys.PLATFORM_MODE_ENABLED;
import static alfio.model.system.ConfigurationKeys.STRIPE_CONNECTED_ID;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StripeCreditCardManagerTest {

    private ConfigurationManager configurationManager;
    private Event event;

    private BaseStripeManager stripeCreditCardManager;

    @BeforeEach
    void init() {
        configurationManager = mock(ConfigurationManager.class);
        TicketRepository ticketRepository = mock(TicketRepository.class);
        event = mock(Event.class);
        stripeCreditCardManager = new BaseStripeManager(configurationManager, null, ticketRepository,  null);
    }

    @Test
    void testCardExceptionHandler() {
        assertEquals("error.STEP2_STRIPE_houston_we_ve_a_problem", stripeCreditCardManager.handleException(new CardException("", "abcd", "houston_we_ve_a_problem", "param", null, null, null, null)));
    }

    @Test
    void testInvalidRequestExceptionHandler() {
        assertEquals("error.STEP2_STRIPE_invalid_param", stripeCreditCardManager.handleException(new InvalidRequestException("abcd", "param", null, null, null, null)));
    }

    @Test
    void testAuthenticationExceptionHandler() {
        assertEquals("error.STEP2_STRIPE_abort", stripeCreditCardManager.handleException(new AuthenticationException("abcd", null, "401", 401)));
    }

    @Test
    void testApiConnectionException() {
        assertEquals("error.STEP2_STRIPE_abort", stripeCreditCardManager.handleException(new ApiConnectionException("abcd")));
    }

    @Test
    void testUnexpectedError() {
        assertEquals("error.STEP2_STRIPE_unexpected", stripeCreditCardManager.handleException( new StripeException("", null, "42", 42) {}));
    }

    @Test
    void testMissingStripeConnectedId() {
        Function<ConfigurationKeys, Configuration.ConfigurationPathKey> partial = Configuration.from(event);
        when(configurationManager.getBooleanConfigValue(partial.apply(PLATFORM_MODE_ENABLED), false)).thenReturn(true);
        when(configurationManager.getStringConfigValue(partial.apply(STRIPE_CONNECTED_ID))).thenReturn(Optional.empty());
        Optional<RequestOptions> options = stripeCreditCardManager.options(event);
        assertNotNull(options);
        assertFalse(options.isPresent());
    }
}