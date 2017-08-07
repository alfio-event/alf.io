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
import alfio.model.*;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentProxy;
import alfio.model.transaction.Transaction;
import alfio.repository.AuditingRepository;
import alfio.repository.TransactionRepository;
import alfio.repository.user.UserRepository;
import alfio.util.ErrorsCode;
import alfio.util.Json;
import com.paypal.base.rest.PayPalRESTException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Log4j2
@AllArgsConstructor
public class PaymentManager {

    private final StripeManager stripeManager;
    private final PaypalManager paypalManager;
    private final MollieManager mollieManager;
    private final TransactionRepository transactionRepository;
    private final ConfigurationManager configurationManager;
    private final AuditingRepository auditingRepository;
    private final UserRepository userRepository;

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
     * @param customerName
     * @param billingAddress
     * @return PaymentResult
     * @throws java.lang.IllegalStateException if there is an error after charging the credit card
     */
    public PaymentResult processStripePayment(String reservationId,
                                              String gatewayToken,
                                              int price,
                                              Event event,
                                              String email,
                                              CustomerName customerName,
                                              String billingAddress) {
        try {
            final Charge charge = stripeManager.chargeCreditCard(gatewayToken, price,
                    event, reservationId, email, customerName.getFullName(), billingAddress);
            log.info("transaction {} paid: {}", reservationId, charge.getPaid());
            transactionRepository.insert(charge.getId(), null, reservationId,
                    ZonedDateTime.now(), price, event.getCurrency(), charge.getDescription(), PaymentProxy.STRIPE.name());
            return PaymentResult.successful(charge.getId());
        } catch (Exception e) {
            if(e instanceof StripeException) {
                return PaymentResult.unsuccessful(stripeManager.handleException((StripeException)e));
            }
            throw new IllegalStateException(e);
        }

    }

    public PaymentResult processPaypalPayment(String reservationId,
                                              String token,
                                              String payerId,
                                              int price,
                                              Event event) {
        try {
            Pair<String, String> captureAndPaymentId = paypalManager.commitPayment(reservationId, token, payerId, event);
            String captureId = captureAndPaymentId.getLeft();
            String paymentId = captureAndPaymentId.getRight();
            transactionRepository.insert(captureId, paymentId, reservationId,
                ZonedDateTime.now(), price, event.getCurrency(), "Paypal confirmation", PaymentProxy.PAYPAL.name());
            return PaymentResult.successful(captureId);
        } catch (Exception e) {
            log.warn("errow while processing paypal payment: " + e.getMessage(), e);
            if(e instanceof PayPalRESTException) {
                return PaymentResult.unsuccessful(ErrorsCode.STEP_2_PAYPAL_UNEXPECTED);
            } else if(e instanceof PaypalManager.HandledPaypalErrorException) {
                return PaymentResult.unsuccessful(e.getMessage());
            }
            throw new IllegalStateException(e);
        }
    }

    public List<PaymentMethod> getPaymentMethods(int organizationId) {
        return PaymentProxy.availableProxies()
            .stream()
            .map(p -> {
                PaymentMethod.PaymentMethodStatus status = ConfigurationKeys.byCategory(p.getSettingCategories()).stream()
                    .allMatch(c -> c.isBackedByDefault() || configurationManager.getStringConfigValue(Configuration.from(organizationId, c)).filter(StringUtils::isNotEmpty).isPresent()) ? PaymentMethod.PaymentMethodStatus.ACTIVE : PaymentMethod.PaymentMethodStatus.ERROR;
                return new PaymentMethod(p, status);
            })
            .collect(Collectors.toList());
    }

    public String getStripePublicKey(Event event) {
        return stripeManager.getPublicKey(event);
    }

    public String createPaypalCheckoutRequest(Event event, String reservationId, OrderSummary orderSummary, CustomerName customerName, String email, String billingAddress, Locale locale, boolean postponeAssignment) throws Exception {
        return paypalManager.createCheckoutRequest(event, reservationId, orderSummary, customerName, email, billingAddress, locale, postponeAssignment);
    }

    public boolean refund(TicketReservation reservation, Event event, Optional<Integer> amount, String username) {
        Transaction transaction = transactionRepository.loadByReservationId(reservation.getId());
        boolean res = false;
        switch(reservation.getPaymentMethod()) {
            case PAYPAL:
                res = paypalManager.refund(transaction, event, amount);
                break;
            case STRIPE:
                res = stripeManager.refund(transaction, event, amount);
                break;
            default:
                throw new IllegalStateException("Cannot refund ");
        }

        if(res) {
            Map<String, Object> changes = new HashMap<>();
            changes.put("refund", amount.map(i -> i.toString()).orElse("full"));
            changes.put("paymentMethod", reservation.getPaymentMethod().toString());
            auditingRepository.insert(reservation.getId(), userRepository.findIdByUserName(username).orElse(null),
                event.getId(),
                Audit.EventType.REFUND, new Date(), Audit.EntityType.RESERVATION, reservation.getId(),
                Collections.singletonList(changes));
        }

        return res;
    }

    public TransactionAndPaymentInfo getInfo(TicketReservation reservation, Event event) {
        Optional<Transaction> maybeTransaction = transactionRepository.loadOptionalByReservationId(reservation.getId());
        return maybeTransaction.map(transaction -> {
            switch(reservation.getPaymentMethod()) {
                case PAYPAL:
                    return new TransactionAndPaymentInfo(reservation.getPaymentMethod(), transaction, paypalManager.getInfo(transaction, event).orElse(null));
                case STRIPE:
                    return new TransactionAndPaymentInfo(reservation.getPaymentMethod(), transaction, stripeManager.getInfo(transaction, event).orElse(null));
                default:
                    return new TransactionAndPaymentInfo(reservation.getPaymentMethod(), transaction, new PaymentInformations(reservation.getPaidAmount(), null));
            }
        }).orElse(new TransactionAndPaymentInfo(reservation.getPaymentMethod(),null, new PaymentInformations(reservation.getPaidAmount(), null)));
    }

    @Data
    public static final class PaymentMethod {

        public enum PaymentMethodStatus {
            ACTIVE, ERROR
        }

        private final PaymentProxy paymentProxy;

        private final PaymentMethodStatus status;

        public boolean isActive() {
            return status == PaymentMethodStatus.ACTIVE;
        }

        public Set<String> getOnlyForCurrency() {
            return paymentProxy.getOnlyForCurrency();
        }
    }
}
