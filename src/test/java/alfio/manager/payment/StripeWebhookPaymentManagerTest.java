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

import alfio.manager.support.PaymentWebhookResult;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Audit;
import alfio.model.Event;
import alfio.model.TicketReservation;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeyValuePathLevel;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentContext;
import alfio.model.transaction.Transaction;
import alfio.model.transaction.TransactionWebhookPayload;
import alfio.repository.*;
import alfio.repository.system.ConfigurationRepository;
import com.stripe.model.Charge;
import com.stripe.model.ChargeCollection;
import com.stripe.model.PaymentIntent;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static alfio.model.system.ConfigurationKeys.STRIPE_SECRET_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StripeWebhookPaymentManagerTest {

    private static final String RESERVATION_ID = "abcdefg";
    private static final String PAYMENT_ID = "PAYMENT_ID";
    private static final int EVENT_ID = 11;
    private static final int TRANSACTION_ID = 22;
    private static final String CHARGE_ID = "CHARGE";
    private TransactionRepository transactionRepository;
    private ConfigurationManager configurationManager;
    private ConfigurationRepository configurationRepository;
    private Event event;
    private Transaction transaction;
    private StripeWebhookPaymentManager stripeWebhookPaymentManager;
    private TicketRepository ticketRepository;
    private TicketReservationRepository ticketReservationRepository;
    private EventRepository eventRepository;
    private AuditingRepository auditingRepository;
    private Environment environment;
    private TicketReservation ticketReservation;

    private static final ConfigurationManager.MaybeConfiguration STRIPE_SECRET_KEY_CONF =
        new ConfigurationManager.MaybeConfiguration(ConfigurationKeys.STRIPE_SECRET_KEY,
            new ConfigurationKeyValuePathLevel(null, "sk_live_", null));

    @BeforeEach
    void setup() {
        configurationManager = mock(ConfigurationManager.class);
        configurationRepository = mock(ConfigurationRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        transaction = mock(Transaction.class);
        when(transaction.getId()).thenReturn(TRANSACTION_ID);
        when(transaction.getPaymentId()).thenReturn(PAYMENT_ID);
        when(transaction.getPlatformFee()).thenReturn(0L);
        ticketReservationRepository = mock(TicketReservationRepository.class);
        eventRepository = mock(EventRepository.class);
        auditingRepository = mock(AuditingRepository.class);
        environment = mock(Environment.class);
        ticketRepository = mock(TicketRepository.class);
        ticketReservation = mock(TicketReservation.class);
        when(ticketReservation.getId()).thenReturn(RESERVATION_ID);
        when(eventRepository.findByReservationId(eq(RESERVATION_ID))).thenReturn(event);
        stripeWebhookPaymentManager = new StripeWebhookPaymentManager(configurationManager, ticketRepository, transactionRepository, configurationRepository, ticketReservationRepository, eventRepository, auditingRepository, environment);
    }

    @Test
    void ignoreNotRelevantTypes() {
        var transactionWebhookPayload = mock(TransactionWebhookPayload.class);
        when(transactionWebhookPayload.getType()).thenReturn("charge.captured", "charge.expired", "payment_intent.amount_capturable_updated");
        for (int i=0; i < 4; i++) {
            assertEquals(PaymentWebhookResult.Type.NOT_RELEVANT, stripeWebhookPaymentManager.processWebhook(transactionWebhookPayload, transaction, null).getType());
        }
    }

    @Test
    void transactionSucceeded() {
        var paymentIntent = mock(PaymentIntent.class);
        var transactionWebhookPayload = mock(TransactionWebhookPayload.class);
        when(transactionWebhookPayload.getType()).thenReturn("payment_intent.succeeded");
        when(transactionWebhookPayload.getPayload()).thenReturn(paymentIntent);
        when(paymentIntent.getMetadata()).thenReturn(Map.of(MetadataBuilder.RESERVATION_ID, RESERVATION_ID));
        when(paymentIntent.getStatus()).thenReturn(BaseStripeManager.SUCCEEDED);
        var chargeCollection = mock(ChargeCollection.class);
        when(paymentIntent.getCharges()).thenReturn(chargeCollection);
        var charge = mock(Charge.class);
        when(chargeCollection.getData()).thenReturn(List.of(charge));
        when(charge.getId()).thenReturn(CHARGE_ID);
        when(ticketReservationRepository.findOptionalReservationById(eq(RESERVATION_ID))).thenReturn(Optional.of(ticketReservation));
        when(ticketReservation.getStatus()).thenReturn(TicketReservation.TicketReservationStatus.EXTERNAL_PROCESSING_PAYMENT);
        var paymentContext = mock(PaymentContext.class);
        when(paymentContext.getEvent()).thenReturn(event);
        when(configurationManager.getFor(eq(STRIPE_SECRET_KEY), any())).thenReturn(STRIPE_SECRET_KEY_CONF);
        when(paymentIntent.getLivemode()).thenReturn(true);
        var paymentWebhookResult = stripeWebhookPaymentManager.processWebhook(transactionWebhookPayload, transaction, paymentContext);
        assertEquals(PaymentWebhookResult.Type.SUCCESSFUL, paymentWebhookResult.getType());
        verify(transactionRepository).update(eq(TRANSACTION_ID), eq(CHARGE_ID), eq(PAYMENT_ID), any(), eq(0L), eq(0L), eq(Transaction.Status.COMPLETE), eq(Map.of()));
        Map<String, Object> changes = Map.of("paymentId", CHARGE_ID, "paymentMethod", "stripe");
        verify(auditingRepository).insert(eq(RESERVATION_ID), isNull(), eq(EVENT_ID), eq(Audit.EventType.PAYMENT_CONFIRMED), any(), eq(Audit.EntityType.RESERVATION), eq(RESERVATION_ID), eq(List.of(changes)));
    }

    @Test
    void transactionFailed() {
        var paymentIntent = mock(PaymentIntent.class);
        var transactionWebhookPayload = mock(TransactionWebhookPayload.class);
        when(transactionWebhookPayload.getType()).thenReturn("payment_intent.payment_failed");
        when(transactionWebhookPayload.getPayload()).thenReturn(paymentIntent);
        when(paymentIntent.getMetadata()).thenReturn(Map.of(MetadataBuilder.RESERVATION_ID, RESERVATION_ID));
        when(paymentIntent.getStatus()).thenReturn("requires_source");

        when(ticketReservationRepository.findOptionalReservationById(eq(RESERVATION_ID))).thenReturn(Optional.of(ticketReservation));
        when(ticketReservation.getStatus()).thenReturn(TicketReservation.TicketReservationStatus.EXTERNAL_PROCESSING_PAYMENT);

        var paymentContext = mock(PaymentContext.class);
        when(paymentContext.getEvent()).thenReturn(event);
        when(configurationManager.getFor(eq(STRIPE_SECRET_KEY), any())).thenReturn(STRIPE_SECRET_KEY_CONF);
        when(paymentIntent.getLivemode()).thenReturn(true);

        var paymentWebhookResult = stripeWebhookPaymentManager.processWebhook(transactionWebhookPayload, transaction, paymentContext);
        assertEquals(PaymentWebhookResult.Type.FAILED, paymentWebhookResult.getType());
        assertTrue(StringUtils.isNotBlank(paymentWebhookResult.getReason()));
        verify(transactionRepository).updateStatusForReservation(eq(RESERVATION_ID), eq(Transaction.Status.FAILED));
        Map<String, Object> changes = Map.of("paymentId", PAYMENT_ID, "paymentMethod", "stripe");
        verify(auditingRepository).insert(eq(RESERVATION_ID), isNull(), eq(EVENT_ID), eq(Audit.EventType.PAYMENT_FAILED), any(), eq(Audit.EntityType.RESERVATION), eq(RESERVATION_ID), eq(List.of(changes)));
    }

    @Test
    void doNotAcceptTestEventsOnLiveEnv() {
        var paymentIntent = mock(PaymentIntent.class);
        var transactionWebhookPayload = mock(TransactionWebhookPayload.class);
        when(transactionWebhookPayload.getType()).thenReturn("payment_intent.succeeded");
        when(transactionWebhookPayload.getPayload()).thenReturn(paymentIntent);
        when(paymentIntent.getMetadata()).thenReturn(Map.of(MetadataBuilder.RESERVATION_ID, RESERVATION_ID));
        when(paymentIntent.getStatus()).thenReturn(BaseStripeManager.SUCCEEDED);
        var chargeCollection = mock(ChargeCollection.class);
        when(paymentIntent.getCharges()).thenReturn(chargeCollection);
        var charge = mock(Charge.class);
        when(chargeCollection.getData()).thenReturn(List.of(charge));
        when(charge.getId()).thenReturn(CHARGE_ID);
        when(ticketReservationRepository.findOptionalReservationById(eq(RESERVATION_ID))).thenReturn(Optional.of(ticketReservation));
        when(ticketReservation.getStatus()).thenReturn(TicketReservation.TicketReservationStatus.EXTERNAL_PROCESSING_PAYMENT);
        var paymentContext = mock(PaymentContext.class);
        when(paymentContext.getEvent()).thenReturn(event);
        when(configurationManager.getFor(eq(STRIPE_SECRET_KEY), any())).thenReturn(STRIPE_SECRET_KEY_CONF);
        when(paymentIntent.getLivemode()).thenReturn(false);
        var paymentWebhookResult = stripeWebhookPaymentManager.processWebhook(transactionWebhookPayload, transaction, paymentContext);
        assertEquals(PaymentWebhookResult.Type.NOT_RELEVANT, paymentWebhookResult.getType());
        verify(transactionRepository, never()).update(eq(TRANSACTION_ID), eq(CHARGE_ID), eq(PAYMENT_ID), any(), eq(0L), eq(0L), eq(Transaction.Status.COMPLETE), eq(Map.of()));
        verify(auditingRepository, never()).insert(eq(RESERVATION_ID), isNull(), eq(EVENT_ID), eq(Audit.EventType.PAYMENT_CONFIRMED), any(), eq(Audit.EntityType.RESERVATION), eq(RESERVATION_ID), anyList());
    }


}