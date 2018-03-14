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
import com.insightfullogic.lambdabehave.JunitSuiteRunner;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import org.junit.runner.RunWith;

import java.time.ZonedDateTime;
import java.util.Optional;

import static com.insightfullogic.lambdabehave.Suite.describe;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(JunitSuiteRunner.class)
public class PaymentManagerTest {{
    StripeManager successStripe = mock(StripeManager.class);
    StripeManager failureStripe = mock(StripeManager.class);
    TransactionRepository transactionRepository = mock(TransactionRepository.class);
    ConfigurationManager configurationManager = mock(ConfigurationManager.class);
    TransactionRepository failureTR = mock(TransactionRepository.class);
    AuditingRepository auditingRepository = mock(AuditingRepository.class);
    UserRepository userRepository = mock(UserRepository.class);
    TicketRepository ticketRepository = mock(TicketRepository.class);

    final String paymentId = "customer#1";
    final String error = "errorCode";
    Event event = mock(Event.class);
    CustomerName customerName = mock(CustomerName.class);
    try {
        when(successStripe.chargeCreditCard(anyString(), anyLong(), any(Event.class), anyString(), anyString(), anyString(), anyString())).thenReturn(Optional.of(new Charge() {{
            setId(paymentId);
        }}));
        when(failureStripe.chargeCreditCard(anyString(), anyLong(), any(Event.class), anyString(), anyString(), anyString(), anyString())).thenThrow(new AuthenticationException("401", "42", 401));
        when(failureStripe.handleException(any(StripeException.class))).thenReturn(error);
        when(failureTR.insert(anyString(), anyString(), anyString(), any(ZonedDateTime.class), anyInt(), anyString(), anyString(), anyString(), anyLong(), anyLong()))
                .thenThrow(new NullPointerException());
    } catch (StripeException e) {
        throw new AssertionError("it should not have thrown any exception!!");
    }

    describe("success flow", it -> {
        it.should("return a successful payment result", expect -> {
            expect.that(new PaymentManager(successStripe, null, null, transactionRepository, configurationManager, auditingRepository, userRepository, ticketRepository).processStripePayment("", "", 100, event, "", customerName, ""))
                    .is(PaymentResult.successful(paymentId));
        });
    });

    describe("stripe error", it -> {
        it.should("return an unsuccessful payment result", expect -> {
            expect.that(new PaymentManager(failureStripe, null, null, transactionRepository, configurationManager, auditingRepository, userRepository, ticketRepository).processStripePayment("", "", 100, event, "", customerName, ""))
                    .is(PaymentResult.unsuccessful(error));
        });
    });

    describe("internal error", it -> {
        it.should("throw IllegalStateException in case of internal error", expect -> {
            expect.exception(IllegalStateException.class, () -> {
                new PaymentManager(successStripe, null, null, failureTR, configurationManager, auditingRepository, userRepository, ticketRepository).processStripePayment("", "", 100, event, "", customerName, "");
            });
        });
    });
}}