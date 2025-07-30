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

import alfio.manager.payment.custom.offline.CustomOfflineConfigurationManager;
import alfio.manager.payment.custom.offline.CustomOfflineConfigurationManager.CustomOfflinePaymentMethodDoesNotExistException;
import alfio.manager.support.PaymentResult;
import alfio.model.CustomerName;
import alfio.model.Event;
import alfio.model.TicketReservation.TicketReservationStatus;
import alfio.model.transaction.*;
import alfio.repository.EventRepository;
import alfio.repository.TicketReservationRepository;
import alfio.repository.TransactionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static alfio.test.util.TestUtil.clockProvider;

class CustomOfflinePaymentManagerTest {
    private CustomOfflinePaymentManager customOfflinePaymentManager;
    private Event event;
    private TicketReservationRepository ticketReservationRepository;
    private TransactionRepository transactionRepository;
    private EventRepository eventRepository;
    private UserDefinedOfflinePaymentMethod paymentMethod;
    private CustomOfflineConfigurationManager customOfflineConfigurationManager;

    @BeforeEach
    void init() throws CustomOfflinePaymentMethodDoesNotExistException {
        customOfflineConfigurationManager = mock(CustomOfflineConfigurationManager.class);

        final int EXPECTED_NUM_MODIFIED_RESERVATIONS = 1;
        ticketReservationRepository = spy(TicketReservationRepository.class);
        when(ticketReservationRepository.postponePayment(
            any(), any(), any(),
            any(), any(), any(),
            any(), any(), any(),
            any()
        )).thenReturn(EXPECTED_NUM_MODIFIED_RESERVATIONS);

        final int EXPECTED_NUM_INSERTED_TRANSACTIONS = 1;
        transactionRepository = spy(TransactionRepository.class);
        when(transactionRepository.insert(
            any(String.class), any(String.class), any(String.class),
            any(ZonedDateTime.class), any(Integer.class), any(String.class),
            any(String.class), any(String.class), any(Long.class), any(Long.class),
            any(), any()
        )).thenReturn(EXPECTED_NUM_INSERTED_TRANSACTIONS);

        eventRepository = mock(EventRepository.class);

        event = mock(Event.class);
        when(event.event()).thenReturn(Optional.of(event));
        var eventEnd = ZonedDateTime.now(clockProvider().getClock()).plusDays(7);
        when(event.getEnd()).thenReturn(eventEnd);
        when(event.getOrganizationId()).thenReturn(1);

        paymentMethod = new UserDefinedOfflinePaymentMethod(
            "c20c5b0b-43bb-4a12-869c-f04ef2a27a79",
            Map.of("en", new UserDefinedOfflinePaymentMethod.Localization(
                    "Interac E-Transfer",
                    "Instant money transfer from any Canadian bank account",
                    "Send full payment to `payments@example.com`."
                )
            )
        );

        when(
            customOfflineConfigurationManager.getOrganizationCustomOfflinePaymentMethodById(anyInt(), anyString())
        ).thenCallRealMethod();
        when(
            customOfflineConfigurationManager.getOrganizationCustomOfflinePaymentMethods(1)
        ).thenReturn(List.of(paymentMethod));

        customOfflinePaymentManager = new CustomOfflinePaymentManager(
            clockProvider(),
            ticketReservationRepository,
            transactionRepository,
            eventRepository,
            customOfflineConfigurationManager
        );
    }

    @Test
    void doPayment() {
        var paymentSpecification = new PaymentSpecification(
            "b18aceed-79da-4bcf-b98f-33317400d2fb",
            null,
            paymentMethod,
            1,
            event,
            "john.doe@example.com",
            new CustomerName("John Doe", "John", "Doe", true)
        );

        PaymentResult paymentResult;
        try (MockedStatic<PaymentManagerUtils> payManagerUtilsMock = mockStatic(PaymentManagerUtils.class)) {
            paymentResult = customOfflinePaymentManager.doPayment(paymentSpecification);
        }
        assertNotNull(paymentResult);
        assertTrue(paymentResult.isSuccessful());

        verify(ticketReservationRepository).postponePayment(
            argThat(resId -> resId == paymentSpecification.getReservationId()),
            argThat(resStatus -> resStatus == TicketReservationStatus.CUSTOM_OFFLINE_PAYMENT),
            any(), any(), any(), any(),
            any(), any(), any(), any()
        );

        verify(transactionRepository).insert(
            any(String.class), isNull(),
            argThat(resId -> paymentSpecification.getReservationId() == resId),
            any(ZonedDateTime.class), any(Integer.class), isNull(),
            any(String.class), any(String.class), any(Long.class), any(Long.class),
            argThat(transStatus -> transStatus == Transaction.Status.PENDING),
            argThat(map ->
                map.containsKey(Transaction.SELECTED_PAYMENT_METHOD_KEY)
                && map.get(Transaction.SELECTED_PAYMENT_METHOD_KEY) == paymentMethod.getPaymentMethodId()
            )
        );
    }

    @Test
    void acceptPassesForEventOrgCustomMethods() {
        boolean result;
        var paymentContext = new PaymentContext(event);

        result = customOfflinePaymentManager.accept(paymentMethod, paymentContext, TransactionRequest.empty());
        assertTrue(result);

        result = customOfflinePaymentManager.accept(StaticPaymentMethods.PAYPAL, paymentContext, TransactionRequest.empty());
        assertFalse(result);
    }

    @Test
    void acceptTransactionPassesForCustomProxy() {
        var transaction = mock(Transaction.class);

        when(transaction.getPaymentProxy()).thenReturn(PaymentProxy.CUSTOM_OFFLINE);
        assertTrue(customOfflinePaymentManager.accept(transaction));

        when(transaction.getPaymentProxy()).thenReturn(PaymentProxy.ON_SITE);
        assertFalse(customOfflinePaymentManager.accept(transaction));
    }

    @Test
    void canGetTransactionCustomPaymentMethod() {
        var transaction = mock(Transaction.class);
        when(transaction.getMetadata()).thenReturn(
            new HashMap<String, String>()
        );
        assertEquals(null, customOfflinePaymentManager.getPaymentMethodForTransaction(transaction));

        when(transaction.getMetadata()).thenReturn(
            Map.of(Transaction.SELECTED_PAYMENT_METHOD_KEY, "c20c5b0b-43bb-4a12-869c-f04ef2a27a79")
        );
        when(eventRepository.findByReservationId("02ef8df8-efe9-4fa5-9434-2ba0656a01be")).thenReturn(event);

        // No event associated with returned reservationId
        when(transaction.getReservationId()).thenReturn("a623f091-6c8b-4061-86dd-319e593aa920");
        assertEquals(null, customOfflinePaymentManager.getPaymentMethodForTransaction(transaction));

        when(
            customOfflineConfigurationManager.getOrganizationCustomOfflinePaymentMethods(1)
        ).thenReturn(List.of());
        var result = customOfflinePaymentManager.getPaymentMethodForTransaction(transaction);
        assertEquals(null, result);

        when(
            customOfflineConfigurationManager.getOrganizationCustomOfflinePaymentMethods(1)
        ).thenReturn(List.of(paymentMethod));
        when(transaction.getReservationId()).thenReturn("02ef8df8-efe9-4fa5-9434-2ba0656a01be");
        result = customOfflinePaymentManager.getPaymentMethodForTransaction(transaction);
        assertNotEquals(null, result);
        assertEquals(paymentMethod.getPaymentMethodId(), result.getPaymentMethodId());
    }

    @Test
    void verifyIsActiveWorksForValidContextAndEvent() {
        var paymentContext = new PaymentContext();
        assertFalse(customOfflinePaymentManager.isActive(paymentContext));

        paymentContext = new PaymentContext(event);
        assertTrue(customOfflinePaymentManager.isActive(paymentContext));
    }

}