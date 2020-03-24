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

import alfio.manager.support.FeeCalculator;
import alfio.manager.support.PaymentResult;
import alfio.manager.support.PaymentWebhookResult;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.system.ConfigurationManager.MaybeConfiguration;
import alfio.model.Event;
import alfio.model.PaymentInformation;
import alfio.model.TicketReservation;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.*;
import alfio.model.transaction.capabilities.PaymentInfo;
import alfio.model.transaction.capabilities.RefundRequest;
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
import alfio.util.MonetaryUtil;
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
public class MollieWebhookPaymentManager implements PaymentProvider, WebhookHandler, RefundRequest, PaymentInfo {

    public static final String WEBHOOK_URL_TEMPLATE = "/api/payment/webhook/mollie/event/{eventShortName}/reservation/{reservationId}";
    private static final Set<PaymentMethod> EMPTY_METHODS = Collections.unmodifiableSet(EnumSet.noneOf(PaymentMethod.class));
    static final Map<String, PaymentMethod> SUPPORTED_METHODS = Map.of(
        "ideal", PaymentMethod.IDEAL,
        "creditcard", PaymentMethod.CREDIT_CARD,
        "applepay", PaymentMethod.APPLE_PAY,
        "bancontact", PaymentMethod.BANCONTACT,
        "inghomepay", PaymentMethod.ING_HOME_PAY,
        "belfius", PaymentMethod.BELFIUS,
        "przelewy24", PaymentMethod.PRZELEWY_24,
        "kbc", PaymentMethod.KBC
        /*
            other available:
            banktransfer, paypal, sofort, klarnapaylater, klarnasliceit, giftcard, giropay, eps
        */
    );
    public static final EnumSet<ConfigurationKeys> ALL_OPTIONS = EnumSet.of(
        MOLLIE_CC_ENABLED,
        PLATFORM_MODE_ENABLED,
        BASE_URL,
        MOLLIE_CONNECT_PROFILE_ID,
        MOLLIE_API_KEY,
        MOLLIE_CONNECT_LIVE_MODE,
        MOLLIE_CONNECT_CLIENT_ID,
        MOLLIE_CONNECT_CLIENT_SECRET,
        MOLLIE_CONNECT_REFRESH_TOKEN,
        MOLLIE_CONNECT_CALLBACK
    );
    private static String MOLLIE_ENDPOINT = "https://api.mollie.com/v2/";
    private static String PAYMENTS_ENDPOINT = MOLLIE_ENDPOINT+"payments";
    private static String METHODS_ENDPOINT = MOLLIE_ENDPOINT+"methods";
    private final Cache<MethodCacheKey, Set<PaymentMethod>> methodsCache = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(5)).build();
    private final Cache<Integer, String> accessTokenCache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(45)).build(); // token lifetime is 1h

    private final HttpClient client;
    private final ConfigurationManager configurationManager;
    private final TicketReservationRepository ticketReservationRepository;
    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;
    private final TransactionRepository transactionRepository;
    private final MollieConnectManager mollieConnectManager;

    private HttpRequest.Builder requestFor(String url, Map<ConfigurationKeys, MaybeConfiguration> configuration, ConfigurationLevel configurationLevel) {
        // check if platform mode is active
        boolean platformMode = configurationLevel.getOrganizationId().isPresent() && configuration.get(PLATFORM_MODE_ENABLED).getValueAsBooleanOrDefault(false);
        var requestBuilder = HttpRequest.newBuilder().uri(URI.create(url));
        if(platformMode) {
            return requestBuilder.header("Authorization", "Bearer " + accessTokenCache.get(configurationLevel.getOrganizationId().orElseThrow(), org -> {
                var accessTokenResponseDetails = mollieConnectManager.refreshAccessToken(configuration);
                if(accessTokenResponseDetails.isSuccess()) {
                    return accessTokenResponseDetails.getAccessToken();
                }
                throw new IllegalStateException("cannot refresh access token");
            }));
        }
        String mollieAPIKey = configuration.get(ConfigurationKeys.MOLLIE_API_KEY).getRequiredValue();
        return requestBuilder.header("Authorization", "Bearer " + mollieAPIKey);
    }

    @Override
    public Set<PaymentMethod> getSupportedPaymentMethods(PaymentContext paymentContext, TransactionRequest transactionRequest) {
        var configuration = getConfiguration(paymentContext.getConfigurationLevel());
        if (checkIfActive(configuration) && isPlatformConfigurationPresent(configuration)) {
            return retrieveAvailablePaymentMethods(transactionRequest, configuration, paymentContext.getConfigurationLevel());
        } else {
            return Set.of();
        }
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
        return checkIfActive(configuration)
            && isPlatformConfigurationPresent(configuration)
            && retrieveAvailablePaymentMethods(transactionRequest, configuration, context.getConfigurationLevel()).contains(paymentMethod);
    }

    private boolean isPlatformConfigurationPresent(Map<ConfigurationKeys, MaybeConfiguration> configuration) {
        return !configuration.get(PLATFORM_MODE_ENABLED).getValueAsBooleanOrDefault(false) || (configuration.get(MOLLIE_CONNECT_REFRESH_TOKEN).isPresent() && configuration.get(MOLLIE_CONNECT_PROFILE_ID).isPresent());
    }

    private Map<ConfigurationKeys, MaybeConfiguration> getConfiguration(ConfigurationLevel configurationLevel) {
        return configurationManager.getFor(ALL_OPTIONS, configurationLevel);
    }

    private Set<PaymentMethod> retrieveAvailablePaymentMethods(TransactionRequest transactionRequest, Map<ConfigurationKeys, MaybeConfiguration> configuration, ConfigurationLevel configurationLevel) {
        try {
            return methodsCache.get(MethodCacheKey.from(transactionRequest, configuration),
                key -> fetchAvailablePaymentMethods(key, configuration, configurationLevel));
        } catch(Exception ex) {
            log.warn("cannot fetch payment methods", ex);
            return EMPTY_METHODS;
        }
    }

    private Set<PaymentMethod> fetchAvailablePaymentMethods(MethodCacheKey key, Map<ConfigurationKeys, MaybeConfiguration> configuration, ConfigurationLevel configurationLevel) {

        var params = new ArrayList<String>();
        if(key.amount != null) {
            params.add("amount[value]="+key.amount.getValue());
            params.add("amount[currency]="+key.amount.getCurrency());
        }

        if(key.billingCountry != null) {
            params.add("billingCountry="+key.billingCountry);
        }

        if(configuration.get(PLATFORM_MODE_ENABLED).getValueAsBooleanOrDefault(false)) {
            params.add("testmode="+ key.testMode);
            params.add("profileId="+configuration.get(MOLLIE_CONNECT_PROFILE_ID).getRequiredValue());
        }

        HttpRequest request = requestFor(METHODS_ENDPOINT + "?" + String.join("&", params), configuration, configurationLevel)
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
                var rejectedMethods = new ArrayList<String>();
                var result = EnumSet.noneOf(PaymentMethod.class);
                for(int i = 0; i < count; i++) {
                    var methodId = methodsObj.get(i).getAsJsonObject().get("id").getAsString();
                    var parsed = SUPPORTED_METHODS.get(methodId);
                    if (parsed != null) {
                        result.add(parsed);
                    } else {
                        rejectedMethods.add(methodId);
                    }
                }
                if(rejectedMethods.isEmpty()) {
                    return result;
                } else {
                    log.warn("Unsupported payment methods found: {}. Please check configuration", rejectedMethods);
                    throw new IllegalStateException("unsupported methods found");
                }
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
        var getPaymentResponse = callGetPayment(transaction.getPaymentId(), configuration, ConfigurationLevel.event(spec.getEvent()));
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
        Map<String, Object> payload = new HashMap<>();
        payload.put("amount", Map.of("value", spec.getOrderSummary().getTotalPrice(), "currency", spec.getEvent().getCurrency()));
        payload.put("description", String.format("%s - %d ticket(s) for event %s", configurationManager.getShortReservationID(spec.getEvent(), reservation), tickets, spec.getEvent().getDisplayName()));
        payload.put("redirectUrl", bookUrl);
        payload.put("webhookUrl", baseUrl + UriComponentsBuilder.fromPath(WEBHOOK_URL_TEMPLATE).buildAndExpand(eventName, reservationId).toUriString());
        payload.put("metadata", MetadataBuilder.buildMetadata(spec, Map.of()));

        if(configuration.get(PLATFORM_MODE_ENABLED).getValueAsBooleanOrDefault(false)) {
            payload.put("profileId", configuration.get(MOLLIE_CONNECT_PROFILE_ID).getRequiredValue());
            payload.put("testmode", !configuration.get(MOLLIE_CONNECT_LIVE_MODE).getValueAsBooleanOrDefault(false));
            String currencyCode = spec.getCurrencyCode();
            FeeCalculator.getCalculator(spec.getEvent(), configurationManager, currencyCode).apply(tickets, (long) spec.getPriceWithVAT())
                .filter(fee -> fee > 1L) //minimum fee for Mollie is 0.01
                .map(fee -> MonetaryUtil.formatCents(fee, currencyCode))
                .ifPresent(fee -> payload.put("applicationFee", Map.of("amount", Map.of("currency", currencyCode, "value", fee), "description", "Reservation" + reservationId)));
        }

        HttpRequest request = requestFor(PAYMENTS_ENDPOINT, configuration, ConfigurationLevel.event(spec.getEvent()))
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
            && configuration.get(BASE_URL).isPresent()
            && configuration.get(MOLLIE_API_KEY).isPresent()
            && connectOptionsPresent(configuration);
    }

    private boolean connectOptionsPresent(Map<ConfigurationKeys, MaybeConfiguration> configuration) {
        if(!configuration.get(PLATFORM_MODE_ENABLED).getValueAsBooleanOrDefault(false)) {
            return true;
        }
        return configuration.get(MOLLIE_CONNECT_CLIENT_ID).isPresent() && configuration.get(MOLLIE_CONNECT_CLIENT_SECRET).isPresent();
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
        var optionalEvent = eventRepository.findOptionalByShortName(eventShortName);
        if(optionalEvent.isEmpty()) {
            return PaymentWebhookResult.notRelevant("event");
        }
        var event = optionalEvent.get();
        return validateRemotePayment(transaction, paymentContext, paymentId, event);
    }

    private PaymentWebhookResult validateRemotePayment(Transaction transaction, PaymentContext paymentContext, String paymentId, Event event) {
        try {
            var configuration = getConfiguration(ConfigurationLevel.event(event));
            HttpResponse<InputStream> response = callGetPayment(paymentId, configuration, paymentContext.getConfigurationLevel());
            if(HttpUtils.callSuccessful(response)) {
                try (var reader = new InputStreamReader(response.body())) {
                    var body = new MolliePaymentDetails(JsonParser.parseReader(reader).getAsJsonObject());
                    if(configuration.get(PLATFORM_MODE_ENABLED).getValueAsBooleanOrDefault(false)
                        && configuration.get(MOLLIE_CONNECT_LIVE_MODE).getValueAsBooleanOrDefault(false) != body.isLiveMode()) {
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
                            transactionRepository.update(transaction.getId(), paymentId, paymentId, ZonedDateTime.now(event.getZoneId()),
                                transaction.getPlatformFee(), transaction.getGatewayFee(), transaction.getStatus(), transactionMetadata);
                            return status.equals("failed") ? PaymentWebhookResult.failed("failed") : PaymentWebhookResult.cancelled();
                        case "canceled":
                            transactionRepository.update(transaction.getId(), paymentId, paymentId, ZonedDateTime.now(event.getZoneId()),
                                0L, 0L, Transaction.Status.CANCELLED, transaction.getMetadata());
                            return PaymentWebhookResult.cancelled();
                        case "open":
                            return PaymentWebhookResult.redirect(body.getCheckoutLink());
                        default:
                            return PaymentWebhookResult.notRelevant(status);
                    }
                }
            } else {
                if(response.statusCode() == 404) {
                    log.warn("Received suspicious call for non-existent payment id "+paymentId);
                    return PaymentWebhookResult.notRelevant("");
                }
                log.warn("was not able to get payment id " + paymentId + " for event " + event.getShortName());
                return PaymentWebhookResult.error("internal error");
            }
        } catch(Exception ex) {
            log.error("got exception while trying to process Mollie Payment "+paymentId, ex);
            return PaymentWebhookResult.error(ex.getMessage());
        }
    }

    @Override
    public PaymentWebhookResult forceTransactionCheck(TicketReservation reservation, Transaction transaction, PaymentContext paymentContext) {
        return validateRemotePayment(transaction, paymentContext, transaction.getPaymentId(), paymentContext.getEvent());
    }

    private HttpResponse<InputStream> callGetPayment(String paymentId, Map<ConfigurationKeys, MaybeConfiguration> configuration, ConfigurationLevel configurationLevel) throws IOException, InterruptedException {
        var paymentResourceUrl = PAYMENTS_ENDPOINT+"/"+paymentId;
        if(configuration.get(PLATFORM_MODE_ENABLED).getValueAsBooleanOrDefault(false)) {
            paymentResourceUrl += ("?testmode=" + !configuration.get(MOLLIE_CONNECT_LIVE_MODE).getValueAsBooleanOrDefault(false));
        }
        HttpRequest request = requestFor(paymentResourceUrl, configuration, configurationLevel).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    }

    @Override
    public boolean refund(Transaction transaction, Event event, Integer amount) {
        var currencyCode = transaction.getCurrency();
        var amountToRefund = Optional.ofNullable(amount)
            .map(a -> MonetaryUtil.formatCents(a, currencyCode))
            .orElseGet(transaction::getFormattedAmount);
        log.trace("Attempting to refund {} for reservation {}", amountToRefund, transaction.getReservationId());
        var configurationLevel = ConfigurationLevel.event(event);
        var configuration = getConfiguration(configurationLevel);
        var paymentId = transaction.getPaymentId();
        var parameters = new HashMap<String, Object>();
        parameters.put("amount[currency]", currencyCode);
        parameters.put("amount[value]", amountToRefund);
        if(configuration.get(PLATFORM_MODE_ENABLED).getValueAsBooleanOrDefault(false)) {
            log.trace("Platform mode is active. Setting testmode to {}", !configuration.get(MOLLIE_CONNECT_LIVE_MODE).getValueAsBooleanOrDefault(false));
            parameters.put("testmode", !configuration.get(MOLLIE_CONNECT_LIVE_MODE).getValueAsBooleanOrDefault(false));
        }

        var request = requestFor(PAYMENTS_ENDPOINT+"/"+ paymentId +"/refunds", configuration, configurationLevel)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpUtils.ofFormUrlEncodedBody(parameters))
            .build();

        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if(HttpUtils.callSuccessful(response)) {
                log.trace("Received a successful response from Mollie. Body is {}", response::body);
                // we ignore the answer, for now
                return true;
            } else {
                log.warn("got {} response while calling refund API for payment ID {}", response.statusCode(), paymentId);
                log.trace("detailed reply from mollie: {}", response::body);
                return false;
            }
        } catch (Exception e) {
            log.warn("error while calling refund API", e);
            return false;
        }
    }

    @Override
    public Optional<PaymentInformation> getInfo(Transaction transaction, Event event) {
        ConfigurationLevel configurationLevel = ConfigurationLevel.event(event);
        var configuration = getConfiguration(configurationLevel);
        try {
            var getPaymentResponse = callGetPayment(transaction.getPaymentId(), configuration, configurationLevel);
            if(HttpUtils.callSuccessful(getPaymentResponse)) {
                try (var responseReader = new InputStreamReader(getPaymentResponse.body())) {
                    var body = new MolliePaymentDetails(JsonParser.parseReader(responseReader).getAsJsonObject());
                    var paidAmount = body.getPaidAmount();
                    var refundAmount = body.getRefundAmount().map(PaymentAmount::getValue).orElse(null);
                    return Optional.of(new PaymentInformation(paidAmount.getValue(), refundAmount, null, null));
                }
            }
        } catch (Exception e) {
            log.warn("got exception while calling getInfo", e);
            return Optional.empty();
        }
        return Optional.empty();
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
            boolean testMode = !configuration.get(MOLLIE_CONNECT_LIVE_MODE).getValueAsBooleanOrDefault(false);
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
            return Optional.ofNullable(body.getAsJsonObject("_links").getAsJsonObject("checkout")).map(c -> c.get("href").getAsString()).orElse("");
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

        boolean hasPaymentMethod() {
            return !body.get("method").isJsonNull();
        }

        PaymentMethod getPaymentMethod() {
            if(!hasPaymentMethod()) {
                return null;
            }
            return SUPPORTED_METHODS.get(body.get("method").getAsString());
        }

        Optional<String> applicationFee() {
            return Optional.ofNullable(body.getAsJsonObject("applicationFee"))
                .map(feeObj -> feeObj.getAsJsonObject("amount"))
                .map(fee -> fee.get("value").getAsString());
        }

        PaymentAmount getPaidAmount() {
            var amount = body.getAsJsonObject("amount");
            return new PaymentAmount(amount.get("value").getAsString(), amount.get("currency").getAsString());
        }

        Optional<PaymentAmount> getRefundAmount() {
            return Optional.ofNullable(body.getAsJsonObject("amountRefunded"))
                .map(refund -> new PaymentAmount(refund.get("value").getAsString(), refund.get("currency").getAsString()));
        }
    }
}
