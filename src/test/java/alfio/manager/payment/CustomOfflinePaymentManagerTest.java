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
import alfio.manager.system.ConfigurationManager;
import alfio.model.CustomerName;
import alfio.model.Event;
import alfio.model.TicketReservation.TicketReservationStatus;
import alfio.model.transaction.*;
import alfio.repository.TicketReservationRepository;
import alfio.repository.TransactionRepository;
import alfio.repository.system.ConfigurationRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static alfio.test.util.TestUtil.clockProvider;

class CustomOfflinePaymentManagerTest {

    private ConfigurationRepository configurationRepository;
    private ConfigurationManager configurationManager;
    private CustomOfflinePaymentManager customOfflinePaymentManager;
    private Event event;
    private TicketReservationRepository ticketReservationRepository;
    private TransactionRepository transactionRepository;
    private UserDefinedOfflinePaymentMethod paymentMethod;

    @BeforeEach
    void init() {
        configurationRepository = mock(ConfigurationRepository.class);
        configurationManager = mock(ConfigurationManager.class);

        final int EXPECTED_NUM_MODIFIED_RESERVATIONS = 1;
        ticketReservationRepository = spy(TicketReservationRepository.class);
        when(ticketReservationRepository.postponePayment(
            any(), any(), any(),
            any(), any(), any(),
            any(), any(), any()
        )).thenReturn(EXPECTED_NUM_MODIFIED_RESERVATIONS);

        final int EXPECTED_NUM_INSERTED_TRANSACTIONS = 1;
        transactionRepository = spy(TransactionRepository.class);
        when(transactionRepository.insert(
            any(String.class), any(String.class), any(String.class),
            any(ZonedDateTime.class), any(Integer.class), any(String.class),
            any(String.class), any(String.class), any(Long.class), any(Long.class),
            any(), any()
        )).thenReturn(EXPECTED_NUM_INSERTED_TRANSACTIONS);


        event = mock(Event.class);
        when(event.event()).thenReturn(Optional.of(event));
        var eventEnd = ZonedDateTime.now(clockProvider().getClock()).plusDays(7);
        when(event.getEnd()).thenReturn(eventEnd);

        paymentMethod = new UserDefinedOfflinePaymentMethod(
            null,
            Map.of("en", new UserDefinedOfflinePaymentMethod.Localization(
                    "Interac E-Transfer",
                    "Instant money transfer from any Canadian bank account",
                    "Send full payment to `payments@example.com`."
                )
            )
        );
        customOfflinePaymentManager = new CustomOfflinePaymentManager(
            clockProvider(),
            configurationRepository,
            ticketReservationRepository,
            transactionRepository,
            configurationManager
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
            any(), any(), any()
        );

        verify(transactionRepository).insert(
            any(String.class), isNull(),
            argThat(resId -> paymentSpecification.getReservationId() == resId),
            any(ZonedDateTime.class), any(Integer.class), isNull(),
            any(String.class), any(String.class), any(Long.class), any(Long.class),
            argThat(transStatus -> transStatus == Transaction.Status.PENDING),
            argThat(map ->
                map.containsKey("selectedPaymentMethod")
                && map.get("selectedPaymentMethod") == paymentMethod.getPaymentMethodId()
            )
        );
    }
}