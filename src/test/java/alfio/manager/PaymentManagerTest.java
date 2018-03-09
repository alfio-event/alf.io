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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PaymentManagerTest {

    @Mock
    private StripeManager stripeManager;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private ConfigurationManager configurationManager;
    @Mock
    private AuditingRepository auditingRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private Event event;
    @Mock
    private CustomerName customerName;

    private final String paymentId = "customer#1";
    private final String error = "errorCode";

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

    @Test(expected = IllegalArgumentException.class)
    public void internalError() throws StripeException {
        when(transactionRepository.insert(anyString(), anyString(), anyString(), any(ZonedDateTime.class), anyInt(), anyString(), anyString(), anyString(), anyLong(), anyLong()))
            .thenThrow(new NullPointerException());
        stripeSuccess().processStripePayment("", "", 100, event, "", customerName, "");
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
        }}));
        return new PaymentManager(stripeManager,
            null, null, transactionRepository, configurationManager, auditingRepository,
            userRepository, ticketRepository);
    }
}