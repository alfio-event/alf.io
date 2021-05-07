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

import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.system.ConfigurationKeyValuePathLevel;
import alfio.model.transaction.PaymentContext;
import alfio.model.transaction.PaymentMethod;
import alfio.model.transaction.TransactionRequest;
import alfio.repository.TicketRepository;
import alfio.test.util.TestUtil;
import com.stripe.exception.*;
import com.stripe.net.RequestOptions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Optional;

import static alfio.manager.testSupport.StripeUtils.completeStripeConfiguration;
import static alfio.model.system.ConfigurationKeys.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StripeCreditCardManagerTest {

    private ConfigurationManager configurationManager;
    private Event event;

    private BaseStripeManager baseStripeManager;
    private StripeCreditCardManager stripeCreditCardManager;

    @BeforeEach
    void init() {
        configurationManager = mock(ConfigurationManager.class);
        TicketRepository ticketRepository = mock(TicketRepository.class);
        event = mock(Event.class);
        baseStripeManager = new BaseStripeManager(configurationManager, null, ticketRepository,  null);
        stripeCreditCardManager = new StripeCreditCardManager(null, baseStripeManager, TestUtil.clockProvider());
    }

    @Test
    void testCardExceptionHandler() {
        assertEquals("error.STEP2_STRIPE_houston_we_ve_a_problem", baseStripeManager.handleException(new CardException("", "abcd", "houston_we_ve_a_problem", "param", null, null, null, null)));
    }

    @Test
    void testInvalidRequestExceptionHandler() {
        assertEquals("error.STEP2_STRIPE_invalid_param", baseStripeManager.handleException(new InvalidRequestException("abcd", "param", null, null, null, null)));
    }

    @Test
    void testAuthenticationExceptionHandler() {
        assertEquals("error.STEP2_STRIPE_abort", baseStripeManager.handleException(new AuthenticationException("abcd", null, "401", 401)));
    }

    @Test
    void testApiConnectionException() {
        assertEquals("error.STEP2_STRIPE_abort", baseStripeManager.handleException(new ApiConnectionException("abcd")));
    }

    @Test
    void testUnexpectedError() {
        assertEquals("error.STEP2_STRIPE_unexpected", baseStripeManager.handleException(new StripeException("", null, "42", 42) {}));
    }

    @Test
    void testMissingStripeConnectedId() {
        when(configurationManager.getFor(eq(PLATFORM_MODE_ENABLED), any()))
            .thenReturn(new ConfigurationManager.MaybeConfiguration(PLATFORM_MODE_ENABLED, new ConfigurationKeyValuePathLevel(null, "true", null)));
        when(configurationManager.getFor(eq(STRIPE_CONNECTED_ID), any())).thenReturn(new ConfigurationManager.MaybeConfiguration(STRIPE_CONNECTED_ID));
        Optional<RequestOptions> options = baseStripeManager.options(event);
        Assertions.assertNotNull(options);
        assertFalse(options.isPresent());
    }

    @Test
    void stripeConfigurationIncompletePlatformModeOff() {
        var configuration = new HashMap<>(completeStripeConfiguration(false));
        configuration.put(STRIPE_CONNECTED_ID, new ConfigurationManager.MaybeConfiguration(STRIPE_CONNECTED_ID));// missing config
        configuration.put(PLATFORM_MODE_ENABLED, new ConfigurationManager.MaybeConfiguration(PLATFORM_MODE_ENABLED));
        configuration.put(STRIPE_SECRET_KEY, new ConfigurationManager.MaybeConfiguration(STRIPE_SECRET_KEY));

        var configurationLevel = ConfigurationLevel.organization(1);
        when(configurationManager.getFor(EnumSet.of(PLATFORM_MODE_ENABLED, STRIPE_CC_ENABLED, STRIPE_CONNECTED_ID, STRIPE_ENABLE_SCA, STRIPE_SECRET_KEY, STRIPE_PUBLIC_KEY), configurationLevel))
            .thenReturn(configuration);
        assertFalse(stripeCreditCardManager.accept(PaymentMethod.CREDIT_CARD, new PaymentContext(null, configurationLevel), TransactionRequest.empty()));
    }

    @Test
    void stripeConfigurationCompletePlatformModeOff() {
        var configuration = new HashMap<>(completeStripeConfiguration(false));
        configuration.put(STRIPE_CONNECTED_ID, new ConfigurationManager.MaybeConfiguration(STRIPE_CONNECTED_ID));// missing config
        configuration.put(PLATFORM_MODE_ENABLED, new ConfigurationManager.MaybeConfiguration(PLATFORM_MODE_ENABLED));

        var configurationLevel = ConfigurationLevel.organization(1);
        when(configurationManager.getFor(EnumSet.of(PLATFORM_MODE_ENABLED, STRIPE_CC_ENABLED, STRIPE_CONNECTED_ID, STRIPE_ENABLE_SCA, STRIPE_SECRET_KEY, STRIPE_PUBLIC_KEY), configurationLevel))
            .thenReturn(configuration);
        assertTrue(stripeCreditCardManager.accept(PaymentMethod.CREDIT_CARD, new PaymentContext(null, configurationLevel), TransactionRequest.empty()));
    }

    @Test
    void stripeConfigurationIncompletePlatformModeOn() {
        var configuration = new HashMap<>(completeStripeConfiguration(false));
        configuration.put(STRIPE_CONNECTED_ID, new ConfigurationManager.MaybeConfiguration(STRIPE_CONNECTED_ID));// missing config

        var configurationLevel = ConfigurationLevel.organization(1);
        when(configurationManager.getFor(EnumSet.of(PLATFORM_MODE_ENABLED, STRIPE_CC_ENABLED, STRIPE_CONNECTED_ID, STRIPE_ENABLE_SCA, STRIPE_SECRET_KEY, STRIPE_PUBLIC_KEY), configurationLevel))
            .thenReturn(configuration);
        assertFalse(stripeCreditCardManager.accept(PaymentMethod.CREDIT_CARD, new PaymentContext(null, configurationLevel), TransactionRequest.empty()));
    }

    @Test
    void stripeConfigurationCompletePlatformModeOn() {
        var configurationLevel = ConfigurationLevel.organization(1);
        when(configurationManager.getFor(EnumSet.of(PLATFORM_MODE_ENABLED, STRIPE_CC_ENABLED, STRIPE_CONNECTED_ID, STRIPE_ENABLE_SCA, STRIPE_SECRET_KEY, STRIPE_PUBLIC_KEY), configurationLevel))
            .thenReturn(completeStripeConfiguration(false));
        assertTrue(stripeCreditCardManager.accept(PaymentMethod.CREDIT_CARD, new PaymentContext(null, configurationLevel), TransactionRequest.empty()));
    }
}