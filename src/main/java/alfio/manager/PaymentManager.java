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
import alfio.model.Event;
import alfio.model.transaction.PaymentProxy;
import alfio.repository.TransactionRepository;
import alfio.util.ErrorsCode;
import com.paypal.base.rest.PayPalRESTException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.UUID;

@Component
@Log4j2
public class PaymentManager {

    private final StripeManager stripeManager;
    private final PaypalManager paypalManager;
    private final TransactionRepository transactionRepository;

    @Autowired
    public PaymentManager(StripeManager stripeManager,
                          PaypalManager paypalManager,
                          TransactionRepository transactionRepository) {
        this.stripeManager = stripeManager;
        this.paypalManager = paypalManager;
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

    public PaymentResult processOfflinePayment(String reservationId, int price, Event event) {
        String transactionId = UUID.randomUUID().toString();
        transactionRepository.insert(transactionId, reservationId, ZonedDateTime.now(event.getZoneId()), price, event.getCurrency(), "Offline payment confirmation", PaymentProxy.OFFLINE.toString());
        return PaymentResult.successful(transactionId);
    }

    public PaymentResult processPaypalPayment(String reservationId, String token, String payerId, int price, Event event) {
        try {
            String transactionId = paypalManager.commitPayment(reservationId, token, payerId, event);
            transactionRepository.insert(transactionId, reservationId,
                ZonedDateTime.now(), price, event.getCurrency(), "Paypal confirmation", PaymentProxy.PAYPAL.name());
            return PaymentResult.successful(transactionId);
        } catch (Exception e) {
            log.warn("errow while processing paypal payment: " + e.getMessage(), e);
            if(e instanceof PayPalRESTException) {
                return PaymentResult.unsuccessful(ErrorsCode.STEP_2_PAYPAL_UNEXPECTED);
            }
            throw new IllegalStateException(e);
        }
    }
}
