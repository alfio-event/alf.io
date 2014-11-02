/**
 * This file is part of bagarino.
 *
 * bagarino is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bagarino is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.bagarino.manager;

import com.insightfullogic.lambdabehave.JunitSuiteRunner;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import io.bagarino.manager.support.PaymentResult;
import io.bagarino.model.Event;
import io.bagarino.repository.TransactionRepository;
import org.junit.runner.RunWith;

import java.time.ZonedDateTime;

import static com.insightfullogic.lambdabehave.Suite.describe;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(JunitSuiteRunner.class)
public class PaymentManagerTest {{
    StripeManager successStripe = mock(StripeManager.class);
    StripeManager failureStripe = mock(StripeManager.class);
    TransactionRepository transactionRepository = mock(TransactionRepository.class);
    TransactionRepository failureTR = mock(TransactionRepository.class);
    final String paymentId = "customer#1";
    final String error = "errorCode";
    Event event = mock(Event.class);
    try {
        when(successStripe.chargeCreditCard(anyString(), anyLong(), any(Event.class), anyString(), anyString(), anyString(), anyString())).thenReturn(new Charge() {{
            setId(paymentId);
        }});
        when(failureStripe.chargeCreditCard(anyString(), anyLong(), any(Event.class), anyString(), anyString(), anyString(), anyString())).thenThrow(new AuthenticationException("401"));
        when(failureStripe.handleException(any(StripeException.class))).thenReturn(error);
        when(failureTR.insert(anyString(), anyString(), any(ZonedDateTime.class), anyInt(), anyString(), anyString(), anyString()))
                .thenThrow(new NullPointerException());
    } catch (StripeException e) {
        throw new AssertionError("it should not have thrown any exception!!");
    }

    describe("success flow", it -> {
        it.should("return a successful payment result", expect -> {
            expect.that(new PaymentManager(successStripe, transactionRepository).processPayment("", "", 100, event, "", "", ""))
                    .is(PaymentResult.successful(paymentId));
        });
    });

    describe("stripe error", it -> {
        it.should("return an unsuccessful payment result", expect -> {
            expect.that(new PaymentManager(failureStripe, transactionRepository).processPayment("", "", 100, event, "", "", ""))
                    .is(PaymentResult.unsuccessful(error));
        });
    });

    describe("internal error", it -> {
        it.should("throw IllegalStateException in case of internal error", expect -> {
            expect.exception(IllegalStateException.class, () -> {
                new PaymentManager(successStripe, failureTR).processPayment("", "", 100, event, "", "", "");
            });
        });
    });
}}