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

import alfio.manager.support.PaymentResult;
import alfio.manager.support.PaymentWebhookResult;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.system.ConfigurationManager.MaybeConfiguration;
import alfio.model.TicketReservation;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.*;
import alfio.model.transaction.capabilities.WebhookHandler;
import alfio.model.transaction.token.MollieToken;
import alfio.model.transaction.webhook.MollieWebhookPayload;
import alfio.repository.EventRepository;
import alfio.repository.TicketRepository;
import alfio.repository.TicketReservationRepository;
import alfio.repository.TransactionRepository;
import alfio.util.ErrorsCode;
import alfio.util.HttpUtils;
import alfio.util.Json;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;

import static alfio.manager.payment.PaymentManagerUtils.invalidateExistingTransactions;
import static alfio.model.TicketReservation.TicketReservationStatus.EXTERNAL_PROCESSING_PAYMENT;
import static alfio.model.TicketReservation.TicketReservationStatus.WAITING_EXTERNAL_CONFIRMATION;
import static alfio.model.system.ConfigurationKeys.*;
import static alfio.util.MonetaryUtil.formatCents;

@Component
@Log4j2
@AllArgsConstructor
public class MollieWebhookPaymentManager implements PaymentProvider, WebhookHandler {

    public static final String WEBHOOK_URL_TEMPLATE = "/api/payment/webhook/mollie/event/{eventShortName}/reservation/{reservationId}";
    private static final Set<PaymentMethod> EMPTY_METHODS = Collections.unmodifiableSet(EnumSet.noneOf(PaymentMethod.class));
    static final Map<String, PaymentMethod> SUPPORTED_METHODS = Map.of(
        "ideal", PaymentMethod.IDEAL,
        "creditcard", PaymentMethod.CREDIT_CARD
        /*
            other available:
            applepay, bancontact, banktransfer, paypal, sofort, belfius, kbc, klarnapaylater, klarnasliceit, giftcard,
            inghomepay, giropay, eps, przelewy24
        */
    );
    private static String MOLLIE_ENDPOINT = "https://api.mollie.com/v2/";
    private static String PAYMENTS_ENDPOINT = MOLLIE_ENDPOINT+"payments";
    private static String METHODS_ENDPOINT = MOLLIE_ENDPOINT+"methods";
    private final Cache<MethodCacheKey, Set<PaymentMethod>> methodsCache = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(5)).build();

    private final HttpClient client;
    private final ConfigurationManager configurationManager;
    private final TicketReservationRepository ticketReservationRepository;
    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;
    private final TransactionRepository transactionRepository;

    private HttpRequest.Builder requestFor(String url, Map<ConfigurationKeys, MaybeConfiguration> configuration) {
        String mollieAPIKey = configuration.get(ConfigurationKeys.MOLLIE_API_KEY).getRequiredValue();
        return HttpRequest.newBuilder().uri(URI.create(url)).header("Authorization", "Bearer " + mollieAPIKey);
    }

    @Override
    public Set<PaymentMethod> getSupportedPaymentMethods(PaymentContext paymentContext, TransactionRequest transactionRequest) {
        return retrieveAvailablePaymentMethods(transactionRequest, getConfiguration(paymentContext.getConfigurationLevel()));
    }

    @Override
    public PaymentProxy getPaymentProxy() {
        return PaymentProxy.MOLLIE;
    }

    @Override
    public boolean accept(PaymentMethod paymentMethod, PaymentContext context, TransactionRequest transactionRequest) {
        if(!SUPPORTED_METHODS.containsValue(paymentMethod)) {
            return false;
        }
        var configuration = getConfiguration(context.getConfigurationLevel());
        return checkIfActive(configuration) && retrieveAvailablePaymentMethods(transactionRequest, configuration).contains(paymentMethod);
    }

    private Map<ConfigurationKeys, MaybeConfiguration> getConfiguration(ConfigurationLevel configurationLevel) {
        return configurationManager.getFor(EnumSet.of(BASE_URL, MOLLIE_PROFILE_ID, MOLLIE_API_KEY, MOLLIE_CC_ENABLED, MOLLIE_LIVE_MODE), configurationLevel);
    }

    private Set<PaymentMethod> retrieveAvailablePaymentMethods(TransactionRequest transactionRequest, Map<ConfigurationKeys, MaybeConfiguration> configuration) {
        try {
            return methodsCache.get(MethodCacheKey.from(transactionRequest, configuration),
                key -> fetchAvailablePaymentMethods(key, configuration));
        } catch(Exception ex) {
            log.warn("cannot fetch payment methods", ex);
            return EMPTY_METHODS;
        }
    }

    private Set<PaymentMethod> fetchAvailablePaymentMethods(MethodCacheKey key, Map<ConfigurationKeys, MaybeConfiguration> configuration) {

        var params = new ArrayList<String>();
        if(key.amount != null) {
            params.add("amount[value]="+key.amount.getValue());
            params.add("amount[currency]="+key.amount.getCurrency());
        }

        if(key.billingCountry != null) {
            params.add("billingCountry="+key.billingCountry);
        }

        //params.add("testmode="+ key.testMode);

        HttpRequest request = requestFor(METHODS_ENDPOINT + "?" + String.join("&", params), configuration)
            .GET()
            .build();

        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if(!HttpUtils.callSuccessful(response)) {
                throw new IllegalStateException("fetch was unsuccessful (HTTP "+response.statusCode()+")");
            }
            try (var reader = new InputStreamReader(response.body())) {
                var body = JsonParser.parseReader(reader).getAsJsonObject();
                int count = body.get("count").getAsInt();
                if(count == 0) {
                    return EMPTY_METHODS;
                }
                var methodsObj = body.getAsJsonObject("_embedded").getAsJsonArray("methods");
                var result = EnumSet.noneOf(PaymentMethod.class);
                for(int i = 0; i < count; i++) {
                    var parsed = SUPPORTED_METHODS.get(methodsObj.get(i).getAsJsonObject().get("id").getAsString());
                    if (parsed != null) {
                        result.add(parsed);
                    }
                }
                return result;
            }
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public PaymentResult getToken(PaymentSpecification spec) {
        return getPaymentResult(spec);
    }

    @Override
    public PaymentResult doPayment(PaymentSpecification spec) {
        throw new IllegalStateException("not supported");
    }

    private PaymentResult getPaymentResult(PaymentSpecification spec) {
        try {
            var event = spec.getEvent();
            var configuration = getConfiguration(ConfigurationLevel.event(event));
            var reservationId = spec.getReservationId();
            var reservation = ticketReservationRepository.findReservationById(reservationId);
            String baseUrl = StringUtils.removeEnd(configuration.get(BASE_URL).getRequiredValue(), "/");

            var existingTransaction = transactionRepository.loadOptionalByReservationId(reservationId)
                .filter(t -> t.getPaymentProxy() == PaymentProxy.MOLLIE && StringUtils.isNotEmpty(t.getPaymentId()));

            if(existingTransaction.isEmpty()) {
                return initPayment(reservation, spec, baseUrl, configuration);
            } else {
                return tryToReuseExistingTransaction(existingTransaction.get(), reservation, spec, baseUrl, configuration);
            }
        } catch (Exception e) {
            log.warn(e);
            return PaymentResult.failed(ErrorsCode.STEP_2_PAYMENT_REQUEST_CREATION);
        }
    }

    private PaymentResult tryToReuseExistingTransaction(Transaction transaction,
                                                        TicketReservation reservation,
                                                        PaymentSpecification spec,
                                                        String baseUrl,
                                                        Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> configuration) throws IOException, InterruptedException {
        var getPaymentResponse = callGetPayment(transaction.getPaymentId(), configuration);
        if(HttpUtils.callSuccessful(getPaymentResponse)) {
            try (var responseReader = new InputStreamReader(getPaymentResponse.body())) {
                var body = new MolliePaymentDetails(JsonParser.parseReader(responseReader).getAsJsonObject());
                var status = body.getStatus();
                if(status.equals("open")) {
                    return PaymentResult.redirect(body.getCheckoutLink());
                } else if (status.equals("pending")) {
                    return PaymentResult.pending(transaction.getPaymentId());
                }
            }
        }

        // either get payment was not successful or the payment was cancelled.
        // falling back to initPayment
        return initPayment(reservation, spec, baseUrl, configuration);
    }

    private PaymentResult initPayment(TicketReservation reservation,
                                      PaymentSpecification spec,
                                      String baseUrl,
                                      Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> configuration) throws IOException, InterruptedException {
        var event = spec.getEvent();
        var eventName = event.getShortName();
        var reservationId = reservation.getId();
        String bookUrl = baseUrl + "/event/" + eventName + "/reservation/" + reservationId + "/book";
        int tickets = ticketRepository.countTicketsInReservation(reservation.getId());
        Map<String, Object> payload = Map.of(
            "amount", Map.of("value", spec.getOrderSummary().getTotalPrice(), "currency", spec.getEvent().getCurrency()),
            "description", String.format("%s - %d ticket(s) for event %s", configurationManager.getShortReservationID(spec.getEvent(), reservation), tickets, spec.getEvent().getDisplayName()),
            "redirectUrl", bookUrl,
            "webhookUrl", baseUrl + UriComponentsBuilder.fromPath(WEBHOOK_URL_TEMPLATE).buildAndExpand(eventName, reservationId).toUriString(),
            "metadata", MetadataBuilder.buildMetadata(spec, Map.of())
        );

        HttpRequest request = requestFor(PAYMENTS_ENDPOINT, configuration)
            .header(HttpUtils.CONTENT_TYPE, HttpUtils.APPLICATION_JSON)
            .POST(HttpRequest.BodyPublishers.ofString(Json.GSON.toJson(payload)))
            .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if(HttpUtils.callSuccessful(response)) {
            try (var responseReader = new InputStreamReader(response.body())) {
                var body = new MolliePaymentDetails(JsonParser.parseReader(responseReader).getAsJsonObject());
                var paymentId = body.getPaymentId();
                var checkoutLink = body.getCheckoutLink();
                var expiration = body.getExpiresAt().orElseThrow().plusMinutes(5); // we give an additional slack to process the payment
                ticketReservationRepository.updateReservationStatus(reservationId, EXTERNAL_PROCESSING_PAYMENT.toString());
                ticketReservationRepository.updateValidity(reservationId, Date.from(expiration.toInstant()));
                invalidateExistingTransactions(reservationId, transactionRepository);
                transactionRepository.insert(paymentId, paymentId,
                    reservationId, ZonedDateTime.now(spec.getEvent().getZoneId()),
                    spec.getPriceWithVAT(), spec.getEvent().getCurrency(), "Mollie Payment",
                    PaymentProxy.MOLLIE.name(), 0L,0L, Transaction.Status.PENDING, Map.of());
                return PaymentResult.redirect(checkoutLink);
            }
        } else {
            log.warn("was not able to create a payment for reservation id " + reservationId);
            return PaymentResult.failed(ErrorsCode.STEP_2_PAYMENT_REQUEST_CREATION);
        }
    }

    @Override
    public boolean accept(Transaction transaction) {
        return PaymentProxy.MOLLIE == transaction.getPaymentProxy();
    }

    @Override
    public PaymentMethod getPaymentMethodForTransaction(Transaction transaction) {
        return null;
    }

    @Override
    public boolean isActive(PaymentContext paymentContext) {
        return checkIfActive(getConfiguration(paymentContext.getConfigurationLevel()));
    }

    private boolean checkIfActive(Map<ConfigurationKeys, MaybeConfiguration> configuration) {
        return configuration.get(MOLLIE_CC_ENABLED).getValueAsBooleanOrDefault(false)
            && configuration.entrySet().stream()
                .filter(e -> !e.getKey().isBooleanComponentType()) // boolean have defaults
                .allMatch(c -> c.getValue().isPresent());
    }

    @Override
    public String getWebhookSignatureKey() {
        return null;
    }

    @Override
    public Optional<TransactionWebhookPayload> parseTransactionPayload(String body, String signature, Map<String, String> additionalInfo) {
        try(var reader = new StringReader(body)) {
            Properties properties = new Properties();
            properties.load(reader);
            return Optional.ofNullable(StringUtils.trimToNull(properties.getProperty("id")))
                .map(paymentId -> new MollieWebhookPayload(paymentId, additionalInfo.get("eventName"), additionalInfo.get("reservationId")));
        } catch(Exception e) {
            log.warn("got exception while trying to decode Mollie Webhook Payload", e);
        }
        return Optional.empty();
    }

    @Override
    public boolean requiresSignedBody() {
        return false;
    }

    @Override
    public PaymentWebhookResult processWebhook(TransactionWebhookPayload payload,
                                               Transaction transaction,
                                               PaymentContext paymentContext) {

        var molliePayload = (MollieWebhookPayload)payload;
        var paymentId = molliePayload.getPaymentId();
        var eventShortName = molliePayload.getEventName();
        var optionalEvent = eventRepository.findOptionalEventAndOrganizationIdByShortName(eventShortName);
        if(optionalEvent.isEmpty()) {
            return PaymentWebhookResult.notRelevant("event");
        }
        var event = optionalEvent.get();
        try {
            var configuration = getConfiguration(ConfigurationLevel.event(event));
            HttpResponse<InputStream> response = callGetPayment(paymentId, configuration);
            if(HttpUtils.callSuccessful(response)) {
                try (var reader = new InputStreamReader(response.body())) {
                    var body = new MolliePaymentDetails(JsonParser.parseReader(reader).getAsJsonObject());
                    if(configuration.get(MOLLIE_LIVE_MODE).getValueAsBooleanOrDefault(false) != body.isLiveMode()) {
                        return PaymentWebhookResult.notRelevant("liveMode");
                    }
                    Validate.isTrue(body.getPaymentId().equals(paymentId));
                    Validate.isTrue(transaction.getPaymentId().equals(paymentId));
                    var status = body.getStatus();
                    var reservationId = body.getReservationId();
                    var optionalReservation = ticketReservationRepository.findOptionalStatusAndValidationById(reservationId)
                        .filter(reservation -> (reservation.getStatus() == EXTERNAL_PROCESSING_PAYMENT || reservation.getStatus() == WAITING_EXTERNAL_CONFIRMATION));
                    if(optionalReservation.isEmpty()) {
                        return PaymentWebhookResult.error("reservation not found");
                    }

                    //see statuses: https://www.mollie.com/en/docs/status

                    var transactionMetadata = transaction.getMetadata();
                    switch (status) {
                        case "paid":
                            transactionMetadata.put("paymentMethod", body.getPaymentMethod().name());
                            transactionRepository.update(transaction.getId(), paymentId, paymentId, body.getConfirmationTimestamp().orElseThrow(),
                                0L, 0L, Transaction.Status.COMPLETE, transaction.getMetadata());
                            return PaymentWebhookResult.successful(new MollieToken(paymentId, body.getPaymentMethod()));
                        case "failed":
                        case "expired":
                            transactionMetadata.put("paymentMethod", Optional.ofNullable(body.getPaymentMethod()).map(PaymentMethod::name).orElse(null));
                            transactionRepository.update(transaction.getId(), paymentId, paymentId, null,
                                transaction.getPlatformFee(), transaction.getGatewayFee(), transaction.getStatus(), transactionMetadata);
                            return PaymentWebhookResult.failed("failed");
                        default:
                            return PaymentWebhookResult.notRelevant(status);
                    }
                }
            } else {
                if(response.statusCode() == 404) {
                    log.warn("Received suspicious webhook for non-existent payment id "+paymentId);
                    return PaymentWebhookResult.notRelevant("");
                }
                log.warn("was not able to get payment id " + paymentId + " for event " + eventShortName);
                return PaymentWebhookResult.error("internal error");
            }
        } catch(Exception ex) {
            log.error("got exception while trying to process Mollie Payment "+paymentId, ex);
            return PaymentWebhookResult.error(ex.getMessage());
        }
    }

    private HttpResponse<InputStream> callGetPayment(String paymentId, Map<ConfigurationKeys, MaybeConfiguration> configuration) throws IOException, InterruptedException {
        HttpRequest request = requestFor(PAYMENTS_ENDPOINT+"/"+paymentId, configuration).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    }

    @Data
    private static class MethodCacheKey {
        private final PaymentAmount amount;
        private final String billingCountry;
        private final boolean testMode;

        private static MethodCacheKey from(TransactionRequest transactionRequest,
                                           Map<ConfigurationKeys, MaybeConfiguration> configuration) {

            String billingCountry = null;
            if(transactionRequest.getBillingDetails() != null) {
                billingCountry = StringUtils.trimToNull(transactionRequest.getBillingDetails().getCountry());
            }
            PaymentAmount amount = null;
            if(transactionRequest.getPrice() != null) {
                String currencyCode = transactionRequest.getPrice().getCurrencyCode();
                amount = new PaymentAmount(formatCents(transactionRequest.getPrice().getPriceWithVAT(), currencyCode), currencyCode);
            }
            boolean testMode = !configuration.get(MOLLIE_LIVE_MODE).getValueAsBooleanOrDefault(false);
            return new MethodCacheKey(amount, billingCountry, testMode);
        }
    }

    @Data
    public static class PaymentAmount {
        private final String value;
        private final String currency;
    }

    @AllArgsConstructor
    private static class MolliePaymentDetails {
        private final JsonObject body;

        String getReservationId() {
            return Objects.requireNonNull(body.getAsJsonObject("metadata").get(MetadataBuilder.RESERVATION_ID), "reservation id")
                .getAsString();
        }

        String getPaymentId() {
            return Objects.requireNonNull(body.get("id")).getAsString();
        }

        String getStatus() {
            return Objects.requireNonNull(body.get("status")).getAsString();
        }

        String getCheckoutLink() {
            return body.getAsJsonObject("_links").getAsJsonObject("checkout").get("href").getAsString();
        }

        Optional<ZonedDateTime> getExpiresAt() {
            return Optional.ofNullable(body.get("expiresAt"))
                .map(at -> ZonedDateTime.parse(at.getAsString()));
        }

        boolean isLiveMode() {
            return "live".equals(body.get("mode").getAsString());
        }

        Optional<ZonedDateTime> getConfirmationTimestamp() {
            return Optional.ofNullable(body.get("paidAt"))
                .map(at -> ZonedDateTime.parse(at.getAsString()));
        }

        PaymentMethod getPaymentMethod() {
            return SUPPORTED_METHODS.get(body.get("method").getAsString());
        }
    }
}
