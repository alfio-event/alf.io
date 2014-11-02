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

import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import io.bagarino.manager.support.PaymentResult;
import io.bagarino.model.Event;
import io.bagarino.model.transaction.PaymentProxy;
import io.bagarino.repository.TransactionRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

@Component
@Log4j2
public class PaymentManager {

    private final StripeManager stripeManager;
    private final TransactionRepository transactionRepository;

    @Autowired
    public PaymentManager(StripeManager stripeManager,
                          TransactionRepository transactionRepository) {
        this.stripeManager = stripeManager;
        this.transactionRepository = transactionRepository;
    }

    /**
     * This method processes the pending payment using the configured payment gateway (at the time of writing, only STRIPE)
     * and returns a PaymentResult.
     * In order to preserve the consistency of the payment, when a non-gateway Exception is thrown, it rethrows an IllegalStateException
     *
     * @param reservationId
     * @param gatewayToken
     * @param price
     * @param event
     * @param email
     * @param fullName
     * @param billingAddress
     * @return PaymentResult
     * @throws java.lang.IllegalStateException if there is an error after charging the credit card
     */
    public PaymentResult processPayment(String reservationId,
                          String gatewayToken,
                          int price,
                          Event event,
                          String email,
                          String fullName,
                          String billingAddress) {
        try {
            final Charge charge = stripeManager.chargeCreditCard(gatewayToken, price,
                    event, reservationId, email, fullName, billingAddress);
            log.info("transaction {} paid: {}", reservationId, charge.getPaid());
            transactionRepository.insert(charge.getId(), reservationId,
                    ZonedDateTime.now(), price, event.getCurrency(), charge.getDescription(), PaymentProxy.STRIPE.name());
            return PaymentResult.successful(charge.getId());
        } catch (Exception e) {
            if(e instanceof StripeException) {
                return PaymentResult.unsuccessful(stripeManager.handleException((StripeException)e));
            }
            throw new IllegalStateException(e);
        }

    }

}
