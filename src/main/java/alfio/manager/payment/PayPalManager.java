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

import alfio.manager.PaymentManager;
import alfio.manager.support.FeeCalculator;
import alfio.manager.support.PaymentResult;
import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentMethod;
import alfio.model.transaction.*;
import alfio.model.transaction.capabilities.ExtractPaymentTokenFromTransaction;
import alfio.model.transaction.capabilities.PaymentInfo;
import alfio.model.transaction.capabilities.RefundRequest;
import alfio.model.transaction.token.PayPalToken;
import alfio.repository.TicketRepository;
import alfio.repository.TicketReservationRepository;
import alfio.repository.TransactionRepository;
import alfio.util.*;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.paypal.core.PayPalEnvironment;
import com.paypal.core.PayPalHttpClient;
import com.paypal.http.HttpResponse;
import com.paypal.http.exceptions.HttpException;
import com.paypal.orders.*;
import com.paypal.payments.CapturesRefundRequest;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static alfio.model.system.ConfigurationKeys.PAYPAL_ENABLED;
import static alfio.util.MonetaryUtil.*;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;

@Component
@Log4j2
@AllArgsConstructor
public class PayPalManager implements PaymentProvider, RefundRequest, PaymentInfo, ExtractPaymentTokenFromTransaction {

    private final Cache<String, PayPalHttpClient> cachedClients = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofHours(1L))
        .build();
    private final ConfigurationManager configurationManager;
    private final TicketReservationRepository ticketReservationRepository;
    private final TicketRepository ticketRepository;
    private final TransactionRepository transactionRepository;
    private final Json json;
    private final ClockProvider clockProvider;

    private PayPalHttpClient getClient(Configurable configurable) {
        PayPalEnvironment apiContext = getApiContext(configurable);
        return cachedClients.get(generateKey(apiContext), key -> new PayPalHttpClient(apiContext));
    }

    private String generateKey(PayPalEnvironment environment) {
        return DigestUtils.sha256Hex(environment.baseUrl() + "::" + environment.clientId() + "::" + environment.clientSecret());
    }

    private PayPalEnvironment getApiContext(Configurable configurable) {
        var paypalConf = configurationManager.getFor(Set.of(ConfigurationKeys.PAYPAL_LIVE_MODE, ConfigurationKeys.PAYPAL_CLIENT_ID, ConfigurationKeys.PAYPAL_CLIENT_SECRET),
            configurable.getConfigurationLevel());
        boolean isLive = paypalConf.get(ConfigurationKeys.PAYPAL_LIVE_MODE).getValueAsBooleanOrDefault();
        String clientId = paypalConf.get(ConfigurationKeys.PAYPAL_CLIENT_ID).getRequiredValue();
        String clientSecret = paypalConf.get(ConfigurationKeys.PAYPAL_CLIENT_SECRET).getRequiredValue();
        return isLive ? new PayPalEnvironment.Live(clientId, clientSecret) : new PayPalEnvironment.Sandbox(clientId, clientSecret);
    }

    private static final String URL_PLACEHOLDER = "--operation--";

    private String createCheckoutRequest(PaymentSpecification spec) throws Exception {

        TicketReservation reservation = ticketReservationRepository.findReservationById(spec.getReservationId());
        String purchaseContextType = spec.getPurchaseContext().getType().getUrlComponent();
        String publicIdentifier = spec.getPurchaseContext().getPublicIdentifier();

        String baseUrl = StringUtils.removeEnd(configurationManager.getFor(ConfigurationKeys.BASE_URL, spec.getPurchaseContext().getConfigurationLevel()).getRequiredValue(), "/");
        String bookUrl = baseUrl + "/" + purchaseContextType + "/" + publicIdentifier + "/reservation/" + spec.getReservationId() + "/payment/paypal/" + URL_PLACEHOLDER;

        String hmac = computeHMAC(spec.getCustomerName(), spec.getEmail(), spec.getBillingAddress(), spec.getPurchaseContext());
        UriComponentsBuilder bookUrlBuilder = UriComponentsBuilder.fromUriString(bookUrl)
            .queryParam("hmac", hmac);
        String finalUrl = bookUrlBuilder.toUriString();

        ApplicationContext applicationContext = new ApplicationContext()
            .landingPage("BILLING")
            .cancelUrl(finalUrl.replace(URL_PLACEHOLDER, "cancel"))
            .returnUrl(finalUrl.replace(URL_PLACEHOLDER, "confirm"))
            .userAction("CONTINUE")
            .shippingPreference("NO_SHIPPING");

        OrderRequest orderRequest = new OrderRequest()
            .applicationContext(applicationContext)
            .checkoutPaymentIntent("CAPTURE")
            .purchaseUnits(List.of(new PurchaseUnitRequest().amountWithBreakdown(new AmountWithBreakdown().currencyCode(spec.getCurrencyCode()).value(spec.getOrderSummary().getTotalPrice()))));
        OrdersCreateRequest request = new OrdersCreateRequest().requestBody(orderRequest);
        request.header("prefer","return=representation");
        request.header("PayPal-Request-Id", reservation.getId());
        HttpResponse<Order> response = getClient(spec.getPurchaseContext()).execute(request);
        if(HttpUtils.statusCodeIsSuccessful(response.statusCode())) {
            Order order = response.result();
            var status = order.status();

            if("APPROVED".equals(status) || "COMPLETED".equals(status)) {
                if("APPROVED".equals(status)) {
                    saveToken(reservation.getId(), spec.getPurchaseContext(), new PayPalToken(order.payer().payerId(), order.id(), hmac));
                }
                return "/" + purchaseContextType + "/" + publicIdentifier + "/reservation/" + spec.getReservationId();
            } else if("CREATED".equals(status)) {
                //add 15 minutes of validity in case the paypal flow is slow
                ticketReservationRepository.updateValidity(spec.getReservationId(), DateUtils.addMinutes(reservation.getValidity(), 15));
                return order.links().stream().filter(l -> l.rel().equals("approve")).map(LinkDescription::href).findFirst().orElseThrow();
            }

        }
        throw new IllegalStateException();
    }

    private static String computeHMAC(CustomerName customerName, String email, String billingAddress, PurchaseContext purchaseContext) {
        return new HmacUtils(HmacAlgorithms.HMAC_SHA_256, purchaseContext.getPrivateKey()).hmacHex(StringUtils.trimToEmpty(customerName.getFullName()) + StringUtils.trimToEmpty(email) + StringUtils.trimToEmpty(billingAddress));
    }

    private static boolean isValidHMAC(CustomerName customerName, String email, String billingAddress, String hmac, PurchaseContext purchaseContext) {
        String computedHmac = computeHMAC(customerName, email, billingAddress, purchaseContext);
        return MessageDigest.isEqual(hmac.getBytes(StandardCharsets.UTF_8), computedHmac.getBytes(StandardCharsets.UTF_8));
    }

    public static class HandledPayPalErrorException extends RuntimeException {
        HandledPayPalErrorException(String errorMessage) {
            super(errorMessage);
        }
    }

    private static final Set<String> MAPPED_ERROR = Set.of("FAILED_TO_CHARGE_CC", "INSUFFICIENT_FUNDS", "EXPIRED_CREDIT_CARD", "INSTRUMENT_DECLINED");

    private static Optional<String> mappedException(HttpException e) {
        //https://developer.paypal.com/docs/api/#errors
        var message = StringUtils.defaultString(e.getMessage());
        var match = MAPPED_ERROR.stream().filter(message::contains).findFirst();// ugly, but PayPal API is uglier :p
        if(match.isPresent()) {
            return Optional.of("error.STEP_2_PAYPAL_"+match.get());
        } else {
            log.warn("Exception from PayPal APIs", e);
            return Optional.empty();
        }
    }

    private PayPalChargeDetails commitPayment(String reservationId, PayPalToken payPalToken, PurchaseContext purchaseContext) throws HttpException {

        try {
            OrdersCaptureRequest request = new OrdersCaptureRequest(payPalToken.getPaymentId()).payPalRequestId(reservationId);
            request.header("prefer","return=representation");//force the API to reply with the full object
            request.requestBody(new OrderRequest());
            HttpResponse<Order> response = getClient(purchaseContext).execute(request);

            if(HttpUtils.statusCodeIsSuccessful(response.statusCode())) {
                var result = response.result();
                // state can only be "created", "approved" or "failed".
                // if we are at this stage, the only possible options are approved or failed, thus it's safe to re transition the reservation to a pending status: no payment has been made!
                if(!"COMPLETED".equals(result.status())) {
                    log.warn("error in state for reservationId {}, expected 'approved' state, but got '{}'", reservationId, result.status());
                    throw new IllegalStateException();
                }

                var captureOptional = result.purchaseUnits().stream()
                    .map(PurchaseUnit::payments)
                    .flatMap(pc -> pc.captures().stream())
                    .findFirst();
                var captureId = captureOptional.map(Capture::id).orElseGet(() -> {
                    log.warn("PayPal: null Capture returned for OrderId {}", result.id());
                    return result.id();
                });

                var payPalFee = captureOptional.map(Capture::sellerReceivableBreakdown)
                    .map(MerchantReceivableBreakdown::paypalFee)
                    .map(money -> MonetaryUtil.unitToCents(new BigDecimal(money.value()), money.currencyCode()))
                    .orElse(0);

                return new PayPalChargeDetails(captureId, result.id(), payPalFee);
            }
        } catch (HttpException e) {
            mappedException(e).ifPresent(message -> {
                throw new HandledPayPalErrorException(message);
            });
            throw e;
        } catch(IOException e) {
            throw new IllegalStateException(e);
        }

        throw new IllegalStateException("cannot commit payment");
    }

    private Optional<PaymentInformation> getInfo(Transaction transaction, PurchaseContext purchaseContext, Supplier<String> platformFeeSupplier) {
        String transactionId = transaction.getTransactionId();
        String paymentId = transaction.getPaymentId();
        String currency = transaction.getCurrency();

        try {
            if(paymentId != null) {
                var orderResponse = getClient(purchaseContext).execute(new OrdersGetRequest(paymentId));
                if(HttpUtils.statusCodeIsSuccessful(orderResponse.statusCode()) && orderResponse.result() != null) {
                    var order = orderResponse.result();
                    var payments = order.purchaseUnits().stream()
                        .map(PurchaseUnit::payments)
                        .collect(Collectors.toList());

                    var refund = payments.stream().flatMap(p -> emptyIfNull(p.refunds()).stream())
                        .map(r -> new BigDecimal(r.amount().value()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                    var gatewayFee = payments.stream().flatMap(p -> emptyIfNull(p.captures()).stream())
                        .map(c -> {
                            if(c != null && c.sellerReceivableBreakdown() != null) {
                                return c.sellerReceivableBreakdown().paypalFee();
                            }
                            return null;
                        })
                        .map(fee -> {
                            if(fee != null) {
                                return new BigDecimal(fee.value());
                            }
                            return BigDecimal.ZERO;
                        })
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                    var paidAmount = payments.stream().flatMap(p -> emptyIfNull(p.captures()).stream())
                        .map(r -> new BigDecimal(r.amount().value()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return Optional.of(new PaymentInformation(formatUnit(paidAmount, currency), formatUnit(refund, currency), String.valueOf(unitToCents(gatewayFee, currency)), platformFeeSupplier.get()));
                }
            }
            return Optional.empty();
        } catch (IOException ex) {
            log.warn("Paypal: error while fetching information for payment id " + transactionId, ex);
            return Optional.empty();
        }
    }

    @Override
    public Optional<PaymentInformation> getInfo(alfio.model.transaction.Transaction transaction, PurchaseContext purchaseContext) {
        return getInfo(transaction, purchaseContext, () -> {
            if(transaction.getPlatformFee() > 0) {
                return String.valueOf(transaction.getPlatformFee());
            }
            return FeeCalculator.getCalculator(purchaseContext, configurationManager, transaction.getCurrency())
                    .apply(ticketRepository.countTicketsInReservation(transaction.getReservationId()), (long) transaction.getPriceInCents())
                    .map(String::valueOf)
                    .orElse("0");
        });
    }


    @Override
    public boolean refund(alfio.model.transaction.Transaction transaction, PurchaseContext purchaseContext, Integer amountToRefund) {
        Optional<Integer> amount = Optional.ofNullable(amountToRefund);
        String captureId = transaction.getTransactionId();
        try {

            var payPalClient = getClient(purchaseContext);
            var refundRequest = new CapturesRefundRequest(captureId);
            String currency = transaction.getCurrency();
            String amountOrFull = amount.map(a -> MonetaryUtil.formatCents(a, currency)).orElse("full");
            amount.ifPresent(i -> refundRequest.requestBody(
                new com.paypal.payments.RefundRequest().amount(new com.paypal.payments.Money().currencyCode(currency).value(formatCents(i, currency))))
            );
            var refundResponse = payPalClient.execute(refundRequest);
            if(HttpUtils.statusCodeIsSuccessful(refundResponse.statusCode())) {
                log.info("Paypal: refund for payment {} executed with success for amount: {}", captureId, amountOrFull);
                return true;
            } else {
                log.warn("Paypal: was not able to refund payment with id {} [HTTP {}]" , captureId, refundResponse.statusCode());
                return false;
            }
        } catch(IOException ex) {
            log.warn("Paypal: was not able to refund payment with id " + captureId, ex);
            return false;
        }
    }

    @Override
    public Set<PaymentMethod> getSupportedPaymentMethods(PaymentContext paymentContext, TransactionRequest transactionRequest) {
        return EnumSet.of(PaymentMethod.PAYPAL);
    }

    @Override
    public PaymentProxy getPaymentProxy() {
        return PaymentProxy.PAYPAL;
    }

    @Override
    public boolean accept(PaymentMethod paymentMethod, PaymentContext context, TransactionRequest transactionRequest) {
        return paymentMethod == PaymentMethod.PAYPAL && isActive(context);
    }

    @Override
    public boolean accept(alfio.model.transaction.Transaction transaction) {
        return PaymentProxy.PAYPAL == transaction.getPaymentProxy();
    }

    @Override
    public PaymentMethod getPaymentMethodForTransaction(alfio.model.transaction.Transaction transaction) {
        return PaymentMethod.PAYPAL;
    }

    @Override
    public boolean isActive(PaymentContext paymentContext) {
        var paypalConf = configurationManager.getFor(Set.of(PAYPAL_ENABLED, ConfigurationKeys.PAYPAL_CLIENT_ID, ConfigurationKeys.PAYPAL_CLIENT_SECRET),
            paymentContext.getConfigurationLevel());
        return paypalConf.get(PAYPAL_ENABLED).getValueAsBooleanOrDefault()
            && paypalConf.get(ConfigurationKeys.PAYPAL_CLIENT_ID).isPresent()
            && paypalConf.get(ConfigurationKeys.PAYPAL_CLIENT_SECRET).isPresent();
    }

    @Override
    public PaymentResult getToken(PaymentSpecification spec) {
        try {
            PaymentToken gatewayToken = spec.getGatewayToken();
            if(gatewayToken != null && gatewayToken.getPaymentProvider() == PaymentProxy.PAYPAL) {
                return PaymentResult.initialized(gatewayToken.getToken());
            }
            return PaymentResult.redirect(createCheckoutRequest(spec));
        } catch (Exception e) {
            log.error(e);
            return PaymentResult.failed( ErrorsCode.STEP_2_PAYMENT_REQUEST_CREATION );
        }
    }

    @Override
    public PaymentResult doPayment(PaymentSpecification spec) {
        try {
            PayPalToken gatewayToken = (PayPalToken) spec.getGatewayToken();
            if(!isValidHMAC(spec.getCustomerName(), spec.getEmail(), spec.getBillingAddress(), gatewayToken.getHmac(), spec.getPurchaseContext())) {
                return PaymentResult.failed(ErrorsCode.STEP_2_INVALID_HMAC);
            }
            var chargeDetails = commitPayment(spec.getReservationId(), gatewayToken, spec.getPurchaseContext());
            long applicationFee = FeeCalculator.getCalculator(spec.getPurchaseContext(), configurationManager, spec.getCurrencyCode())
                .apply(ticketRepository.countTicketsInReservation(spec.getReservationId()), (long) spec.getPriceWithVAT())
                .orElse(0L);

            log.info("PayPalManager::: doPayment {} ", spec.getReservationId());
            var existingTransaction = transactionRepository.loadOptionalByReservationIdAndStatus(spec.getReservationId(), Transaction.Status.COMPLETE).orElse(null);
            if(existingTransaction != null && existingTransaction.getPaymentProxy() == PaymentProxy.PAYPAL && existingTransaction.getTransactionId().equals(chargeDetails.captureId)) {
                log.info("skipped transaction already complete. This is probably due to a concurrent confirmation");
            } else {
                PaymentManagerUtils.invalidateExistingTransactions(spec.getReservationId(), transactionRepository);
                transactionRepository.insert(chargeDetails.captureId, chargeDetails.orderId, spec.getReservationId(),
                    ZonedDateTime.now(clockProvider.withZone(spec.getPurchaseContext().getZoneId())), spec.getPriceWithVAT(), spec.getPurchaseContext().getCurrency(), "Paypal confirmation", PaymentProxy.PAYPAL.name(),
                    applicationFee, chargeDetails.payPalFee, alfio.model.transaction.Transaction.Status.COMPLETE, Map.of());
            }
            return PaymentResult.successful(chargeDetails.captureId);
        } catch (Exception e) {
            log.warn("error while processing paypal payment: " + e.getMessage(), e);
            if(e instanceof HttpException) {
                return PaymentResult.failed(ErrorsCode.STEP_2_PAYPAL_UNEXPECTED);
            } else if(e instanceof HandledPayPalErrorException) {
                return PaymentResult.failed(e.getMessage());
            }
            throw new IllegalStateException(e);
        }
    }

    public void saveToken(String reservationId, PurchaseContext purchaseContext, PayPalToken token) {
        PaymentManagerUtils.invalidateExistingTransactions(reservationId, transactionRepository);
        transactionRepository.insert(reservationId, token.getPaymentId(), reservationId,
            purchaseContext.now(clockProvider), 0, purchaseContext.getCurrency(), "Paypal token", PaymentProxy.PAYPAL.name(), 0, 0,
            alfio.model.transaction.Transaction.Status.PENDING, Map.of(PaymentManager.PAYMENT_TOKEN, json.asJsonString(token)));
    }

    @Override
    public Optional<PaymentToken> extractToken(alfio.model.transaction.Transaction transaction) {
        var jsonPaymentToken = transaction.getMetadata().getOrDefault(PaymentManager.PAYMENT_TOKEN, null);
        return Optional.ofNullable(jsonPaymentToken).map(t -> json.fromJsonString(t, PayPalToken.class));
    }

    public void removeToken(TicketReservation reservation, String paymentId) {
        var optionalTransaction = transactionRepository.loadOptionalByReservationId(reservation.getId());
        if(optionalTransaction.isPresent()) {
            var transaction = optionalTransaction.get();
            if(StringUtils.equals(transaction.getPaymentId(), paymentId) && transaction.getPaymentProxy() == PaymentProxy.PAYPAL && transaction.getStatus() == Transaction.Status.PENDING) {
                PaymentManagerUtils.invalidateExistingTransactions(reservation.getId(), transactionRepository, PaymentProxy.PAYPAL);
            }
        } else {
            log.warn("attempting to delete non-existing transaction {} for reservation {}", paymentId, reservation.getId());
        }
    }

    @AllArgsConstructor
    private static class PayPalChargeDetails {
        private final String captureId;
        private final String orderId;
        private final long payPalFee;
    }

}
