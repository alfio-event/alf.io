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

import alfio.manager.support.PaymentResult;
import alfio.manager.system.ConfigurationManager;
import alfio.model.CustomerName;
import alfio.model.Event;
import alfio.repository.AuditingRepository;
import alfio.repository.TicketRepository;
import alfio.repository.TransactionRepository;
import alfio.repository.user.UserRepository;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PaymentManagerTest {

    private StripeManager stripeManager;
    private TransactionRepository transactionRepository;
    private ConfigurationManager configurationManager;
    private AuditingRepository auditingRepository;
    private UserRepository userRepository;
    private TicketRepository ticketRepository;
    private Event event;
    private CustomerName customerName;

    private final String paymentId = "customer#1";
    private final String error = "errorCode";

    @BeforeEach
    public void setUp() {
        stripeManager = mock(StripeManager.class);
        transactionRepository = mock(TransactionRepository.class);
        configurationManager = mock(ConfigurationManager.class);
        auditingRepository = mock(AuditingRepository.class);
        userRepository = mock(UserRepository.class);
        ticketRepository = mock(TicketRepository.class);
        event = mock(Event.class);
        customerName = mock(CustomerName.class);
        when(customerName.getFullName()).thenReturn("ciccio");
    }

    @Test
    public void successFlow() throws StripeException {
        PaymentResult result = stripeSuccess().processStripePayment("", "", 100, event, "", customerName, "");
        assertEquals(result, PaymentResult.successful(paymentId));
    }

    @Test
    public void stripeError() throws StripeException {
        PaymentResult result = stripeFailure().processStripePayment("", "", 100, event, "", customerName, "");
        assertEquals(result, PaymentResult.unsuccessful(error));
    }

    @Test
    public void internalError() throws StripeException {
        when(event.getCurrency()).thenReturn("CHF");
        when(transactionRepository.insert(anyString(), isNull(), anyString(), any(ZonedDateTime.class), anyInt(), eq("CHF"), anyString(), anyString(), anyLong(), anyLong()))
            .thenThrow(new NullPointerException());
        Assertions.assertThrows(IllegalStateException.class, () -> stripeSuccess().processStripePayment("", "", 100, event, "", customerName, ""));
    }

    private PaymentManager stripeFailure() throws StripeException {
        when(stripeManager.chargeCreditCard(anyString(), anyLong(), any(Event.class), anyString(), anyString(), anyString(), anyString())).thenThrow(new AuthenticationException("401", "42", 401));
        when(stripeManager.handleException(any(StripeException.class))).thenReturn(error);
        return new PaymentManager(stripeManager,
            null, null, transactionRepository, configurationManager, auditingRepository,
            userRepository, ticketRepository);
    }

    private PaymentManager stripeSuccess() throws StripeException {
        when(stripeManager.chargeCreditCard(anyString(), anyLong(), any(Event.class), anyString(), anyString(), anyString(), anyString())).thenReturn(Optional.of(new Charge() {{
            setId(paymentId);
            setDescription("description");
        }}));
        return new PaymentManager(stripeManager,
            null, null, transactionRepository, configurationManager, auditingRepository,
            userRepository, ticketRepository);
    }
}