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

import alfio.manager.payment.BankTransferManager;
import alfio.manager.payment.CustomOfflinePaymentManager;
import alfio.manager.payment.MollieWebhookPaymentManager;
import alfio.manager.payment.StripeWebhookPaymentManager;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.testSupport.MaybeConfigurationBuilder;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentMethod;
import alfio.model.transaction.PaymentProxy;
import alfio.model.transaction.StaticPaymentMethods;
import alfio.model.transaction.UserDefinedOfflinePaymentMethod;
import alfio.model.transaction.webhook.MollieWebhookPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentManagerTest {

    private PaymentManager paymentManager;
    private StripeWebhookPaymentManager stripe;
    private MollieWebhookPaymentManager mollie;

    @BeforeEach
    void init() {
        stripe = mock(StripeWebhookPaymentManager.class);
        when(stripe.isActive(any())).thenReturn(true);
        when(stripe.getPaymentProxy()).thenCallRealMethod();
        mollie = mock(MollieWebhookPaymentManager.class);
        when(mollie.isActive(any())).thenReturn(true);
        when(mollie.getPaymentProxy()).thenCallRealMethod();
        paymentManager = new PaymentManager(null, null, null, null, null,
            List.of(stripe, mollie));
    }

    @Test
    void validSelection() {
        doReturn(
            EnumSet.of(StaticPaymentMethods.CREDIT_CARD)
                .stream()
                .map(paymentMethod -> (PaymentMethod) paymentMethod)
                .collect(Collectors.toSet())
        )
            .when(stripe)
            .getSupportedPaymentMethods(any(), any());

        doReturn(EnumSet.of(StaticPaymentMethods.IDEAL))
            .when(mollie)
            .getSupportedPaymentMethods(any(), any());
        assertTrue(paymentManager.validateSelection(List.of(PaymentProxy.STRIPE, PaymentProxy.MOLLIE), 1).isEmpty());
    }

    @Test
    void selectionConflict() {
        doReturn(EnumSet.of(StaticPaymentMethods.CREDIT_CARD))
            .when(stripe)
            .getSupportedPaymentMethods(any(), any());
        doReturn(EnumSet.of(StaticPaymentMethods.CREDIT_CARD, StaticPaymentMethods.IDEAL))
            .when(mollie)
            .getSupportedPaymentMethods(any(), any());
        List<Map.Entry<PaymentMethod, Set<PaymentProxy>>> entries = paymentManager.validateSelection(List.of(PaymentProxy.STRIPE, PaymentProxy.MOLLIE), 1);
        assertFalse(entries.isEmpty());
        assertEquals(1, entries.size());
        assertSame(StaticPaymentMethods.CREDIT_CARD, entries.get(0).getKey());
        assertEquals(EnumSet.of(PaymentProxy.STRIPE, PaymentProxy.MOLLIE), entries.get(0).getValue());
    }

    /**
     * Regression test to ensure we are filtering deny listed items
     * using a full text match, not a partial match (contains).
     */
    @Test
    void paymentProxyDeniedListMatchesFullText() {
        var orgId = 1;
        var inputCustomPaymentMethods = List.of(
            new UserDefinedOfflinePaymentMethod(
                "15146df3-2436-4d2e-90b9-0d6cb273e291",
                Map.of(
                    "en", new UserDefinedOfflinePaymentMethod.Localization(
                        "Interac E-Transfer",
                        "Instant bank transfer from any Canadian account.",
                        "Send the payment to `payments@example.com`."
                    )
                )
            )
        );

        var customOffline = mock(CustomOfflinePaymentManager.class);
        when(customOffline.isActive(any())).thenReturn(true);
        when(customOffline.getPaymentProxy()).thenCallRealMethod();
        doReturn(
            new HashSet<PaymentMethod>(inputCustomPaymentMethods)
        )
            .when(customOffline)
            .getSupportedPaymentMethods(any(), any());
        var bankTransfer = mock(BankTransferManager.class);
        when(bankTransfer.isActive(any())).thenReturn(true);
        when(bankTransfer.getPaymentProxy()).thenCallRealMethod();
        doReturn(
            EnumSet.of(StaticPaymentMethods.BANK_TRANSFER)
                .stream()
                .map(paymentMethod -> (PaymentMethod) paymentMethod)
                .collect(Collectors.toSet())
        )
            .when(bankTransfer)
            .getSupportedPaymentMethods(any(), any());


        var configurationManager = mock(ConfigurationManager.class);
        var maybeConfig = MaybeConfigurationBuilder.existing(
            ConfigurationKeys.PAYMENT_METHODS_BLACKLIST,
            PaymentProxy.CUSTOM_OFFLINE.name()
        );

        when(configurationManager.getFor(eq(ConfigurationKeys.PAYMENT_METHODS_BLACKLIST), any())).thenReturn(maybeConfig);
        var paymentManagerForCustomOffline = new PaymentManager(
            null,
            configurationManager,
            null,
            null,
            null,
            List.of(customOffline, bankTransfer)
        );

        var paymentMethods = paymentManagerForCustomOffline.getPaymentMethods(orgId);

        assertEquals(1, paymentMethods.size());
        assertEquals(PaymentProxy.OFFLINE, paymentMethods.get(0).getPaymentProxy());
    }
}