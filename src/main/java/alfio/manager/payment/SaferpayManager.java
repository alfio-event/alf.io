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

import alfio.manager.payment.saferpay.*;
import alfio.manager.support.PaymentResult;
import alfio.manager.support.PaymentWebhookResult;
import alfio.manager.system.ConfigurationManager;
import alfio.model.PaymentInformation;
import alfio.model.PurchaseContext;
import alfio.model.TicketReservation;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.*;
import alfio.model.transaction.capabilities.PaymentInfo;
import alfio.model.transaction.capabilities.WebhookHandler;
import alfio.model.transaction.token.SaferpayToken;
import alfio.model.transaction.webhook.EmptyWebhookPayload;
import alfio.repository.TicketRepository;
import alfio.repository.TicketReservationRepository;
import alfio.repository.TransactionRepository;
import alfio.util.ClockProvider;
import alfio.util.HttpUtils;
import alfio.util.MonetaryUtil;
import alfio.util.oauth2.AccessTokenResponseDetails;
import com.google.gson.JsonParser;
import lombok.AllArgsConstructor;
import lombok.experimental.Delegate;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.*;

import static alfio.manager.payment.PaymentManagerUtils.invalidateExistingTransactions;
import static alfio.model.TicketReservation.TicketReservationStatus.EXTERNAL_PROCESSING_PAYMENT;
import static alfio.model.system.ConfigurationKeys.*;
import static java.util.Base64.getEncoder;

@Component
@Transactional
@AllArgsConstructor
public class SaferpayManager implements PaymentProvider, /*RefundRequest,*/ PaymentInfo, WebhookHandler {

    private static final String LIVE_ENDPOINT = "https://www.saferpay.com/api";
    private static final String TEST_ENDPOINT = "https://test.saferpay.com/api";
    private static final Logger LOGGER = LoggerFactory.getLogger(SaferpayManager.class);
    private static final String RETRY_COUNT = "retryCount";
    private static final String TRANSACTION = "Transaction";
    private static final String STATUS = "Status";

    private final ConfigurationManager configurationManager;
    private final HttpClient httpClient;
    private final TicketReservationRepository ticketReservationRepository;
    private final TransactionRepository transactionRepository;
    private final TicketRepository ticketRepository;
    private final ClockProvider clockProvider;

    @Override
    public Set<PaymentMethod> getSupportedPaymentMethods(PaymentContext paymentContext, TransactionRequest transactionRequest) {
        return EnumSet.of(PaymentMethod.CREDIT_CARD);
    }

    @Override
    public PaymentProxy getPaymentProxy() {
        return PaymentProxy.SAFERPAY;
    }

    @Override
    public boolean accept(PaymentMethod paymentMethod, PaymentContext context, TransactionRequest transactionRequest) {
        return paymentMethod == PaymentMethod.CREDIT_CARD
            && isActive(context);
    }

    @Override
    public boolean accept(Transaction transaction) {
        return transaction.getPaymentProxy() == PaymentProxy.SAFERPAY;
    }

    @Override
    public PaymentMethod getPaymentMethodForTransaction(Transaction transaction) {
        return PaymentMethod.CREDIT_CARD; // TODO retrieve payment method from transaction
    }

    @Override
    public boolean isActive(PaymentContext paymentContext) {
        var configurationMap = configurationManager.getFor(EnumSet.of(SAFERPAY_ENABLED, SAFERPAY_API_USERNAME, SAFERPAY_API_PASSWORD, SAFERPAY_CUSTOMER_ID, SAFERPAY_TERMINAL_ID), paymentContext.getConfigurationLevel());
        return configurationMap
            .values()
            .stream()
            .allMatch(ConfigurationManager.MaybeConfiguration::isPresent);
    }

    @Override
    public PaymentResult doPayment(PaymentSpecification spec) {
        var purchaseContext = spec.getPurchaseContext();
        var configuration = loadConfiguration(purchaseContext);
        var reservationId = spec.getReservationId();
        var reservation = ticketReservationRepository.findReservationById(reservationId);
        final int items;
        if(spec.getPurchaseContext().getType() == PurchaseContext.PurchaseContextType.event) {
            items = ticketRepository.countTicketsInReservation(spec.getReservationId());
        } else {
            items = 1;
        }
        int retryCount = 0;
        var existingTransaction = transactionRepository.loadOptionalByStatusAndPaymentProxyForUpdate(reservationId, Transaction.Status.PENDING, PaymentProxy.SAFERPAY);
        if(existingTransaction.isPresent()) {
            var transaction = existingTransaction.get();
            var processResult = internalProcessWebhook(transaction, spec.getPaymentContext());
            if(processResult.getType() == PaymentWebhookResult.Type.SUCCESSFUL) {
                return PaymentResult.successful(processResult.getPaymentToken().getToken());
            } else {
                retryCount = Integer.parseInt(transaction.getMetadata().getOrDefault(RETRY_COUNT, "0")) + 1;
            }
        }

        var description = purchaseContext.ofType(PurchaseContext.PurchaseContextType.event) ? "ticket(s) for event" : "x subscription";
        var paymentDescription = String.format("%s - %d %s %s", configurationManager.getShortReservationID(purchaseContext, reservation), items, description, purchaseContext.getDisplayName());
        var requestBody = new PaymentPageInitializeRequestBuilder(configuration.get(BASE_URL).getRequiredValue(), spec)
            .addAuthentication(configuration.get(SAFERPAY_CUSTOMER_ID).getRequiredValue(), reservationId, configuration.get(SAFERPAY_TERMINAL_ID).getRequiredValue())
            .addOrderInformation(reservationId, Integer.toString(spec.getPriceWithVAT()), spec.getCurrencyCode(), paymentDescription, retryCount)
            .build();
        var request = buildRequest(configuration, "/Payment/v1/PaymentPage/Initialize", requestBody);

        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if(!HttpUtils.callSuccessful(response)) {
                LOGGER.warn("Create session failed with status {}, body {}", response.statusCode(), response.body());
                throw new IllegalStateException("session creation was not successful (HTTP "+response.statusCode()+")");
            }
            LOGGER.debug("received successful response {}", response.body());
            return PaymentResult.redirect(processPaymentInitializationResponse(response, spec, retryCount));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Payment init interrupted", e);
            return PaymentResult.failed(e.getMessage());
        } catch (Exception ex) {
            LOGGER.error("unexpected error while calling payment init", ex);
            return PaymentResult.failed(ex.getMessage());
        }
    }

    private String authorizationHeader(Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> configuration) {
        var credentials = configuration.get(SAFERPAY_API_USERNAME).getRequiredValue() + ":" + configuration.get(SAFERPAY_API_PASSWORD).getRequiredValue();
        return getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Optional<TransactionWebhookPayload> parseTransactionPayload(String body,
                                                                       String signature,
                                                                       Map<String, String> additionalInfo,
                                                                       PaymentContext paymentContext) {
        return Optional.of(new EmptyWebhookPayload(additionalInfo.get("reservationId"), TransactionWebhookPayload.Status.SUCCESS));
    }

    @Override
    public PaymentWebhookResult processWebhook(TransactionWebhookPayload payload, Transaction transaction, PaymentContext paymentContext) {
        // assert that the payment has been actually confirmed
        return internalProcessWebhook(transaction, paymentContext);
    }

    PaymentWebhookResult internalProcessWebhook(Transaction transaction, PaymentContext paymentContext) {
        int retryCount = Integer.parseInt(transaction.getMetadata().getOrDefault(RETRY_COUNT, "0"));
        var configuration = loadConfiguration(paymentContext.getPurchaseContext());
        var paymentStatus = retrievePaymentStatus(configuration, transaction.getPaymentId(), transaction.getReservationId(), retryCount);
        if(paymentStatus.isEmpty()) {
            LOGGER.debug("Invalidating transaction with ID {}", transaction.getId());
            transactionRepository.invalidateById(transaction.getId());
            ticketReservationRepository.updateValidity(transaction.getReservationId(), DateUtils.addMinutes(new Date(), configuration.get(RESERVATION_TIMEOUT).getValueAsIntOrDefault(25)));
            return PaymentWebhookResult.cancelled();
        }

        if(paymentStatus.isSuccessful()) {
            transactionRepository.update(transaction.getId(), paymentStatus.transactionId,
                paymentStatus.captureId, paymentStatus.timestamp, transaction.getPlatformFee(),
                transaction.getGatewayFee(), Transaction.Status.COMPLETE, transaction.getMetadata());
            return PaymentWebhookResult.successful(new SaferpayToken(paymentStatus.captureId));
        } else if(paymentStatus.isInitialized()) {
            return PaymentWebhookResult.processStarted(new SaferpayToken(transaction.getPaymentId()));
        }
        return PaymentWebhookResult.pending();
    }

    @Override
    public PaymentWebhookResult forceTransactionCheck(TicketReservation reservation, Transaction transaction, PaymentContext paymentContext) {
        return internalProcessWebhook(transaction, paymentContext);
    }

    @Override
    public boolean requiresSignedBody() {
        return false;
    }

    @Override
    public Optional<PaymentInformation> getInfo(Transaction transaction, PurchaseContext purchaseContext) {
        var configuration = loadConfiguration(purchaseContext);
        var requestBody = new TransactionInquireRequestBuilder(transaction.getTransactionId(), 0)
            .addAuthentication(configuration.get(SAFERPAY_CUSTOMER_ID).getRequiredValue(), transaction.getReservationId())
            .build();
        var request = buildRequest(configuration, "/Payment/v1/Transaction/Inquire", requestBody);

        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if(!HttpUtils.callSuccessful(response)) {
                LOGGER.warn("Cannot retrieve transaction info. Status {}, body {}", response.statusCode(), response.body());
                return Optional.empty();
            }
            LOGGER.debug("received successful response {}", response.body());
            var responseBody = JsonParser.parseString(response.body()).getAsJsonObject();
            var amount = responseBody.get(TRANSACTION).getAsJsonObject().get("Amount").getAsJsonObject();
            var centsAsString = amount.get("Value").getAsString();
            var formattedAmount = MonetaryUtil.formatCents(Integer.parseInt(centsAsString), amount.get("CurrencyCode").getAsString());
            return Optional.of(new PaymentInformation(formattedAmount, null, null, null));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Request interrupted while calling getInfo", e);
        } catch (Exception ex) {
            LOGGER.error("unexpected error while calling getInfo", ex);
        }
        return Optional.empty();
    }

    //@Override
    public boolean refund(Transaction transaction, PurchaseContext purchaseContext, Integer amount) {
        var configuration = loadConfiguration(purchaseContext);
        var requestBody = new TransactionRefundBuilder(transaction.getPaymentId(), 0)
            .addAuthentication(configuration.get(SAFERPAY_CUSTOMER_ID).getRequiredValue(), transaction.getReservationId())
            .build(Integer.toString(amount), transaction.getCurrency());
        var request = buildRequest(configuration, "/Payment/v1/Transaction/Refund", requestBody);
        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if(!HttpUtils.callSuccessful(response)) {
                LOGGER.warn("Cannot refund transaction. Status {}, body {}", response.statusCode(), response.body());
                return false;
            }
            var transactionResponse = JsonParser.parseString(response.body()).getAsJsonObject().get(TRANSACTION).getAsJsonObject();
            Validate.isTrue("REFUND".equals(transactionResponse.get("Type").getAsString()), "Unexpected transaction type");
            if("AUTHORIZED".equals(transactionResponse.get(STATUS).getAsString())) {
                return confirmTransaction(configuration, transactionResponse.get("Id").getAsString(), null, UUID.randomUUID().toString(), 0).isSuccessful();
            }
            LOGGER.debug("received successful response {}", response.body());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Refund request interrupted", e);
            return false;
        } catch(Exception ex) {
            LOGGER.error("unexpected error while trying to refund transaction {}", transaction.getTransactionId(), ex);
            return false;
        }
    }

    private PaymentStatus retrievePaymentStatus(Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> configuration,
                                                String token,
                                                String reservationId,
                                                int retryCount) {
        var requestBody = new PaymentPageAssertRequestBuilder(token, retryCount)
            .addAuthentication(configuration.get(SAFERPAY_CUSTOMER_ID).getRequiredValue(), reservationId)
            .build();
        var request = buildRequest(configuration, "/Payment/v1/PaymentPage/Assert", requestBody);
        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (HttpUtils.callSuccessful(response)) {
                var responseBody = JsonParser.parseString(response.body()).getAsJsonObject();
                var transaction = responseBody.get(TRANSACTION).getAsJsonObject();
                var paymentStatus = transaction.get(STATUS).getAsString();
                var transactionId = transaction.get("Id").getAsString();
                switch (paymentStatus) {
                    case "CAPTURED":
                        var captureId = transaction.get("CaptureId").getAsString();
                        var timestamp = ZonedDateTime.parse(transaction.get("Date").getAsString());
                        return new PaymentStatus(PaymentResult.successful(captureId), transactionId, captureId, timestamp);
                    case "AUTHORIZED":
                        return confirmTransaction(configuration, transactionId, token, reservationId, retryCount);
                    case "PENDING":
                        throw new IllegalStateException("PENDING status is not supported");
                    default:
                        return PaymentStatus.EMPTY;
                }
            }
            int statusCode = response.statusCode();
            if(statusCode > 499) {
                // temporary error
                throw new IllegalStateException("Internal server error");
            }
            return PaymentStatus.EMPTY;
        } catch(IllegalStateException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private PaymentStatus confirmTransaction(Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> configuration,
                                             String transactionId,
                                             String token,
                                             String requestId,
                                             int retryCount) {
        var requestBody = new TransactionCaptureRequestBuilder(transactionId, retryCount)
            .addAuthentication(configuration.get(SAFERPAY_CUSTOMER_ID).getRequiredValue(), requestId)
            .build();
        var request = buildRequest(configuration, "/Payment/v1/Transaction/Capture", requestBody);

        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (HttpUtils.callSuccessful(response)) {
                var responseBody = JsonParser.parseString(response.body()).getAsJsonObject();
                var paymentStatus = responseBody.get(STATUS).getAsString();
                Validate.isTrue(paymentStatus.equals("CAPTURED"), "Expected CAPTURED Payment Status, got %s", paymentStatus);
                var captureId = responseBody.get("CaptureId").getAsString();
                var timestamp = ZonedDateTime.parse(responseBody.get("Date").getAsString());
                return new PaymentStatus(PaymentResult.successful(captureId), transactionId, captureId, timestamp);
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        return new PaymentStatus(PaymentResult.initialized(token), token, null, null);
    }

    private HttpRequest buildRequest(Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> configuration,
                                     String api,
                                     String requestBody) {
        var endpoint = configuration.get(SAFERPAY_LIVE_MODE).getValueAsBooleanOrDefault() ? LIVE_ENDPOINT : TEST_ENDPOINT;
        return HttpRequest.newBuilder()
            .uri(URI.create(endpoint + api))
            .header("Authorization", "Basic " + authorizationHeader(configuration))
            .header("Content-Type", "application/json; charset=utf-8")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
    }

    private Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> loadConfiguration(PurchaseContext purchaseContext) {
        return configurationManager.getFor(EnumSet.of(SAFERPAY_ENABLED, SAFERPAY_API_USERNAME, SAFERPAY_API_PASSWORD, SAFERPAY_CUSTOMER_ID, SAFERPAY_TERMINAL_ID, SAFERPAY_LIVE_MODE, BASE_URL, RESERVATION_TIMEOUT), purchaseContext.getConfigurationLevel());
    }

    private String processPaymentInitializationResponse(HttpResponse<String> response, PaymentSpecification spec, int retryCount) {
        var reservationId = spec.getReservationId();

        var responseBody = JsonParser.parseString(response.body()).getAsJsonObject();
        var paymentToken = responseBody.get("Token").getAsString();
        var expiration = ZonedDateTime.parse(responseBody.get("Expiration").getAsString());

        ticketReservationRepository.updateReservationStatus(reservationId, EXTERNAL_PROCESSING_PAYMENT.toString());
        ticketReservationRepository.updateValidity(reservationId, Date.from(expiration.toInstant()));
        invalidateExistingTransactions(reservationId, transactionRepository);
        transactionRepository.insert(paymentToken, paymentToken,
            reservationId, ZonedDateTime.now(clockProvider.withZone(spec.getPurchaseContext().getZoneId())),
            spec.getPriceWithVAT(), spec.getPurchaseContext().getCurrency(), "Saferpay Payment",
            PaymentProxy.SAFERPAY.name(), 0L,0L, Transaction.Status.PENDING, Map.of(RETRY_COUNT, String.valueOf(retryCount)));

        return responseBody.get("RedirectUrl").getAsString();
    }

    @AllArgsConstructor
    private static class PaymentStatus {
        static final PaymentStatus EMPTY = new PaymentStatus(null, null, null, null);

        @Delegate
        private final PaymentResult paymentResult;
        private final String transactionId;
        private final String captureId;
        private final ZonedDateTime timestamp;


        private boolean isEmpty() {
            return paymentResult == null;
        }
    }

}
