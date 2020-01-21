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

import alfio.manager.support.PaymentResult;
import alfio.manager.system.ConfigurationManager.MaybeConfiguration;
import alfio.model.Event;
import alfio.model.TicketReservationStatusAndValidation;
import alfio.model.system.ConfigurationKeyValuePathLevel;
import alfio.model.transaction.*;
import alfio.repository.TicketReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;

import static alfio.manager.TicketReservationManager.NOT_YET_PAID_TRANSACTION_ID;
import static alfio.model.TicketReservation.TicketReservationStatus.DEFERRED_OFFLINE_PAYMENT;
import static alfio.model.TicketReservation.TicketReservationStatus.OFFLINE_PAYMENT;
import static alfio.model.system.ConfigurationKeys.DEFERRED_BANK_TRANSFER_ENABLED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeferredBankTransferManagerTest {

    private TicketReservationRepository ticketReservationRepository;
    private BankTransferManager bankTransferManager;
    private DeferredBankTransferManager deferredBankTransferManager;
    private Event event;

    @BeforeEach
    void init() {
        ticketReservationRepository = mock(TicketReservationRepository.class);
        bankTransferManager = mock(BankTransferManager.class);
        deferredBankTransferManager = new DeferredBankTransferManager(ticketReservationRepository, bankTransferManager);
        event = mock(Event.class);
    }

    @Test
    void acceptPaymentRequest() {
        var paymentContext = new PaymentContext(event);
        when(bankTransferManager.options(paymentContext)).thenReturn(Map.of(DEFERRED_BANK_TRANSFER_ENABLED,
            new MaybeConfiguration(DEFERRED_BANK_TRANSFER_ENABLED, new ConfigurationKeyValuePathLevel(DEFERRED_BANK_TRANSFER_ENABLED.name(), "true", null))));
        when(bankTransferManager.bankTransferEnabledForMethod(any(), eq(paymentContext), anyMap())).thenReturn(true);
        when(bankTransferManager.isPaymentDeferredEnabled(anyMap())).thenCallRealMethod();
        assertTrue(deferredBankTransferManager.accept(PaymentMethod.BANK_TRANSFER, paymentContext, TransactionRequest.empty()));
        assertFalse(deferredBankTransferManager.accept(PaymentMethod.BANK_TRANSFER, new PaymentContext(), TransactionRequest.empty()));
    }

    @Test
    void doNotAcceptPaymentRequestBecauseFlagIsNotActive() {
        var paymentContext = new PaymentContext(event);
        when(bankTransferManager.options(paymentContext)).thenReturn(Map.of(DEFERRED_BANK_TRANSFER_ENABLED,
            new MaybeConfiguration(DEFERRED_BANK_TRANSFER_ENABLED)));
        when(bankTransferManager.bankTransferEnabledForMethod(any(), eq(paymentContext), anyMap())).thenReturn(true);
        when(bankTransferManager.isPaymentDeferredEnabled(anyMap())).thenCallRealMethod();
        assertFalse(deferredBankTransferManager.accept(PaymentMethod.BANK_TRANSFER, paymentContext, TransactionRequest.empty()));
    }

    @Test
    void doPayment() {
        var eventBegin = ZonedDateTime.now(Clock.systemUTC()).plusDays(7);
        when(event.getBegin()).thenReturn(eventBegin);
        var paymentSpecification = new PaymentSpecification("reservation-id", null, 1, event, "", null);
        assertEquals(PaymentResult.successful(NOT_YET_PAID_TRANSACTION_ID), deferredBankTransferManager.doPayment(paymentSpecification));
        verify(bankTransferManager).postponePayment(eq(paymentSpecification), eq(DEFERRED_OFFLINE_PAYMENT), eq(eventBegin));
        verify(bankTransferManager).overrideExistingTransactions(paymentSpecification);
    }

    @Test
    @SuppressWarnings("unchecked")
    void acceptTransaction() {
        var reservationId = "reservation-id";
        var transaction = mock(Transaction.class);
        when(transaction.getPaymentProxy()).thenReturn(PaymentProxy.OFFLINE);
        when(transaction.getReservationId()).thenReturn(reservationId);
        when(ticketReservationRepository.findOptionalStatusAndValidationById(eq(reservationId)))
            .thenReturn(
                Optional.of(new TicketReservationStatusAndValidation(DEFERRED_OFFLINE_PAYMENT, true)),
                Optional.of(new TicketReservationStatusAndValidation(OFFLINE_PAYMENT, true)),
                Optional.empty()
            );
        // first call, we accept the transaction
        assertTrue(deferredBankTransferManager.accept(transaction));
        // second call, we do not accept the transaction because the reservation status is wrong
        assertFalse(deferredBankTransferManager.accept(transaction));
        // third call, we do not accept the transaction because the reservation was not found
        assertFalse(deferredBankTransferManager.accept(transaction));
    }
}