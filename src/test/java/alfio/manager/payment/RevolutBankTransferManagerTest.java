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
import alfio.model.TicketReservation;
import alfio.model.TicketReservationWithTransaction;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentContext;
import alfio.model.transaction.Transaction;
import alfio.model.transaction.provider.RevolutTransactionDescriptor;
import alfio.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static alfio.model.system.ConfigurationKeys.REVOLUT_MANUAL_REVIEW;
import static alfio.test.util.TestUtil.clockProvider;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RevolutBankTransferManagerTest {

    private static final String FIRST_UUID = UUID.randomUUID().toString();
    private static final String SECOND_UUID = UUID.randomUUID().toString();
    private static final String THIRD_UUID = UUID.randomUUID().toString();
    private static final int TRANSACTION_ID = 23;
    private TicketReservationWithTransaction first;
    private TicketReservationWithTransaction second;
    private TicketReservationWithTransaction third;
    private ConfigurationManager configurationManager;
    private TransactionRepository transactionRepository;
    private RevolutBankTransferManager revolutBankTransferManager;
    private Event event;
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        first = mock(TicketReservationWithTransaction.class);
        var firstReservation = mock(TicketReservation.class);
        when(firstReservation.getId()).thenReturn(FIRST_UUID);
        when(first.getTicketReservation()).thenReturn(firstReservation);
        transaction = mock(Transaction.class);
        when(transaction.getPriceInCents()).thenReturn(100);
        when(transaction.getId()).thenReturn(TRANSACTION_ID);
        when(transaction.getCurrency()).thenReturn("CHF");
        when(transaction.getStatus()).thenReturn(Transaction.Status.PENDING);
        when(transaction.getTimestamp()).thenReturn(ZonedDateTime.now(clockProvider().getClock()));
        when(first.getTransaction()).thenReturn(transaction);

        second = mock(TicketReservationWithTransaction.class);
        var secondReservation = mock(TicketReservation.class);
        when(secondReservation.getId()).thenReturn(SECOND_UUID);
        when(second.getTicketReservation()).thenReturn(secondReservation);

        third = mock(TicketReservationWithTransaction.class);
        var thirdReservation = mock(TicketReservation.class);
        when(thirdReservation.getId()).thenReturn(THIRD_UUID);
        when(third.getTicketReservation()).thenReturn(thirdReservation);
        BankTransferManager bankTransferManager = mock(BankTransferManager.class);
        configurationManager = mock(ConfigurationManager.class);
        transactionRepository = mock(TransactionRepository.class);
        revolutBankTransferManager = new RevolutBankTransferManager(bankTransferManager, configurationManager, transactionRepository, HttpClient.newHttpClient(), clockProvider());
        event = mock(Event.class);

        when(configurationManager.getShortReservationID(eq(event), eq(firstReservation))).thenReturn(FIRST_UUID.substring(0,8));
        when(configurationManager.getShortReservationID(eq(event), eq(secondReservation))).thenReturn(SECOND_UUID.substring(0,8));
        when(configurationManager.getShortReservationID(eq(event), eq(thirdReservation))).thenReturn(THIRD_UUID.substring(0,8));
        var maybeConfiguration = mock(ConfigurationManager.MaybeConfiguration.class);
        when(maybeConfiguration.getValueAsIntOrDefault(anyInt())).thenReturn(8);
        when(configurationManager.getFor(any(ConfigurationKeys.class), any(ConfigurationLevel.class))).thenReturn(maybeConfiguration);
    }

    @Test
    void matchSingleTransaction() {
        internalMatchSingleTransaction(false);
    }

    @Test
    void matchSingleTransactionAutomaticConfirmation() {
        internalMatchSingleTransaction(true);
    }

    private void internalMatchSingleTransaction(boolean automaticConfirmation) {
        var paymentContext = new PaymentContext(event);
        var automaticConfirmationMock = mock(ConfigurationManager.MaybeConfiguration.class);
        when(automaticConfirmationMock.getValueAsBooleanOrDefault()).thenReturn(!automaticConfirmation);
        when(configurationManager.getFor(eq(REVOLUT_MANUAL_REVIEW), any())).thenReturn(automaticConfirmationMock);
        when(transactionRepository.lockLatestForUpdate(eq(FIRST_UUID))).thenReturn(Optional.of(transaction));
        var pendingReservations = List.of(first, second, third);
        var single = mock(RevolutTransactionDescriptor.class);
        when(single.getReference()).thenReturn("Very long description "+FIRST_UUID.substring(0,8)+ "... to be continued...");
        when(single.getTransactionBalance()).thenReturn(BigDecimal.ONE);
        String paymentId = UUID.randomUUID().toString();
        when(single.getId()).thenReturn(paymentId);
        var leg = mock(RevolutTransactionDescriptor.TransactionLeg.class);
        when(leg.getAmount()).thenReturn(BigDecimal.ONE);
        when(leg.getCurrency()).thenReturn("CHF");
        when(single.getLegs()).thenReturn(List.of(leg));
        var result = revolutBankTransferManager.matchTransactions(pendingReservations, List.of(single), paymentContext, !automaticConfirmation);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getData().size());
        assertEquals(FIRST_UUID, result.getData().get(0));
        verify(transactionRepository).update(
            eq(TRANSACTION_ID),
            eq(paymentId),
            isNull(),
            any(),
            eq(0L),
            eq(0L),
            eq(automaticConfirmation ? Transaction.Status.OFFLINE_MATCHING_PAYMENT_FOUND : Transaction.Status.OFFLINE_PENDING_REVIEW),
            any()
        );
    }


    @Test
    void noMatches() {
        var pendingReservations = List.of(first, second, third);
        var single = mock(RevolutTransactionDescriptor.class);
        when(single.getReference()).thenReturn("Very long description but no reference");
        when(single.getTransactionBalance()).thenReturn(BigDecimal.ONE);
        var leg = mock(RevolutTransactionDescriptor.TransactionLeg.class);
        when(leg.getAmount()).thenReturn(BigDecimal.ONE);
        when(single.getLegs()).thenReturn(List.of(leg));
        var result = revolutBankTransferManager.matchTransactions(pendingReservations, List.of(single), new PaymentContext(event), true);
        assertTrue(result.isSuccess());
        assertEquals(0, result.getData().size());
    }
}