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

import alfio.manager.support.FeeCalculator;
import alfio.manager.support.PaymentResult;
import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentProxy;
import alfio.model.transaction.Transaction;
import alfio.repository.AuditingRepository;
import alfio.repository.TicketRepository;
import alfio.repository.TransactionRepository;
import alfio.repository.user.UserRepository;
import alfio.util.ErrorsCode;
import com.paypal.base.rest.PayPalRESTException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Fee;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Supplier;
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
    private final TicketRepository ticketRepository;

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
    PaymentResult processStripePayment(String reservationId,
                                       String gatewayToken,
                                       int price,
                                       Event event,
                                       String email,
                                       CustomerName customerName,
                                       String billingAddress) {
        try {
            final Optional<Charge> optionalCharge = stripeManager.chargeCreditCard(gatewayToken, price,
                    event, reservationId, email, customerName.getFullName(), billingAddress);
            return optionalCharge.map(charge -> {
                log.info("transaction {} paid: {}", reservationId, charge.getPaid());
                Pair<Long, Long> fees = Optional.ofNullable(charge.getBalanceTransactionObject()).map(bt -> {
                    List<Fee> feeDetails = bt.getFeeDetails();
                    return Pair.of(Optional.ofNullable(StripeManager.getFeeAmount(feeDetails, "application_fee")).map(Long::parseLong).orElse(0L),
                                   Optional.ofNullable(StripeManager.getFeeAmount(feeDetails, "stripe_fee")).map(Long::parseLong).orElse(0L));
                }).orElse(null);

                transactionRepository.insert(charge.getId(), null, reservationId,
                        ZonedDateTime.now(), price, event.getCurrency(), charge.getDescription(), PaymentProxy.STRIPE.name(),
                        fees != null ? fees.getLeft() : 0L, fees != null ? fees.getRight() : 0L);
                return PaymentResult.successful(charge.getId());
            }).orElseGet(() -> PaymentResult.unsuccessful("error.STEP2_UNABLE_TO_TRANSITION"));
        } catch (Exception e) {
            if(e instanceof StripeException) {
                return PaymentResult.unsuccessful(stripeManager.handleException((StripeException)e));
            }
            throw new IllegalStateException(e);
        }

    }

    PaymentResult processPayPalPayment(String reservationId,
                                       String token,
                                       String payerId,
                                       int price,
                                       Event event) {
        try {
            Pair<String, String> captureAndPaymentId = paypalManager.commitPayment(reservationId, token, payerId, event);
            String captureId = captureAndPaymentId.getLeft();
            String paymentId = captureAndPaymentId.getRight();
            Supplier<String> feeSupplier = () -> FeeCalculator.getCalculator(event, configurationManager)
                .apply(ticketRepository.countTicketsInReservation(reservationId), (long) price)
                .map(String::valueOf)
                .orElse("0");
            Pair<Long, Long> fees = paypalManager.getInfo(paymentId, captureId, event, feeSupplier).map(i -> {
                Long platformFee = Optional.ofNullable(i.getPlatformFee()).map(Long::parseLong).orElse(0L);
                Long gatewayFee = Optional.ofNullable(i.getFee()).map(Long::parseLong).orElse(0L);
                return Pair.of(platformFee, gatewayFee);
            }).orElseGet(() -> Pair.of(0L, 0L));
            transactionRepository.insert(captureId, paymentId, reservationId,
                ZonedDateTime.now(), price, event.getCurrency(), "Paypal confirmation", PaymentProxy.PAYPAL.name(),
                fees.getLeft(), fees.getRight());
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
        String blacklist = configurationManager.getStringConfigValue(Configuration.from(organizationId, ConfigurationKeys.PAYMENT_METHODS_BLACKLIST), "");
        return PaymentProxy.availableProxies()
            .stream()
            .filter(p -> !blacklist.contains(p.getKey()))
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

    public String createPayPalCheckoutRequest(Event event, String reservationId, OrderSummary orderSummary,
                                              CustomerName customerName, String email, String billingAddress, String customerReference,
                                              Locale locale, boolean postponeAssignment, boolean invoiceRequested) throws Exception {
        return paypalManager.createCheckoutRequest(event, reservationId, orderSummary, customerName, email,
                                                   billingAddress, customerReference, locale, postponeAssignment, invoiceRequested);
    }

    public boolean refund(TicketReservation reservation, Event event, Optional<Integer> amount, String username) {
        Transaction transaction = transactionRepository.loadByReservationId(reservation.getId());
        boolean res;
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
            changes.put("refund", amount.map(Object::toString).orElse("full"));
            changes.put("paymentMethod", reservation.getPaymentMethod().toString());
            auditingRepository.insert(reservation.getId(), userRepository.findIdByUserName(username).orElse(null),
                event.getId(),
                Audit.EventType.REFUND, new Date(), Audit.EntityType.RESERVATION, reservation.getId(),
                Collections.singletonList(changes));
        }

        return res;
    }

    TransactionAndPaymentInfo getInfo(TicketReservation reservation, Event event) {
        Optional<TransactionAndPaymentInfo> maybeTransaction = transactionRepository.loadOptionalByReservationId(reservation.getId())
            .map(transaction -> {
                switch(reservation.getPaymentMethod()) {
                    case PAYPAL:
                        return new TransactionAndPaymentInfo(reservation.getPaymentMethod(), transaction, paypalManager.getInfo(transaction, event).orElse(null));
                    case STRIPE:
                        return new TransactionAndPaymentInfo(reservation.getPaymentMethod(), transaction, stripeManager.getInfo(transaction, event).orElse(null));
                    default:
                        return new TransactionAndPaymentInfo(reservation.getPaymentMethod(), transaction, new PaymentInformation(reservation.getPaidAmount(), null, String.valueOf(transaction.getGatewayFee()), String.valueOf(transaction.getPlatformFee())));
                }
            });
        maybeTransaction.ifPresent(info -> {
            try {
                Transaction transaction = info.getTransaction();
                String transactionId = transaction.getTransactionId();
                PaymentInformation paymentInformation = info.getPaymentInformation();
                if(paymentInformation != null) {
                    transactionRepository.updateFees(transactionId, reservation.getId(), safeParseLong(paymentInformation.getPlatformFee()), safeParseLong(paymentInformation.getFee()));
                }
            } catch (Exception e) {
                log.warn("cannot update fees", e);
            }
        });
        return maybeTransaction.orElseGet(() -> new TransactionAndPaymentInfo(reservation.getPaymentMethod(),null, new PaymentInformation(reservation.getPaidAmount(), null, null, null)));
    }

    private static long safeParseLong(String src) {
        return Optional.ofNullable(src).map(Long::parseLong).orElse(0L);
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
