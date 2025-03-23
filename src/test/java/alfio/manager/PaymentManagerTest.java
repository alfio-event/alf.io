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

import alfio.manager.payment.MollieWebhookPaymentManager;
import alfio.manager.payment.StripeWebhookPaymentManager;
import alfio.model.transaction.PaymentMethod;
import alfio.model.transaction.PaymentProxy;
import alfio.model.transaction.StaticPaymentMethods;
import alfio.model.transaction.webhook.MollieWebhookPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
        assertSame(entries.get(0).getKey(), StaticPaymentMethods.CREDIT_CARD);
        assertEquals(entries.get(0).getValue(), EnumSet.of(PaymentProxy.STRIPE, PaymentProxy.MOLLIE));
    }
}