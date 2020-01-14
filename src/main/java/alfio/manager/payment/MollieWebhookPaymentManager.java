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
import alfio.model.Event;
import alfio.model.TicketReservation;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.*;
import alfio.model.transaction.capabilities.WebhookHandler;
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
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

import static alfio.model.system.ConfigurationKeys.*;
import static alfio.util.MonetaryUtil.formatCents;

@Component
@Log4j2
@AllArgsConstructor
public class MollieWebhookPaymentManager implements PaymentProvider, WebhookHandler {

    private static final Set<PaymentMethod> EMPTY_METHODS = Collections.unmodifiableSet(EnumSet.noneOf(PaymentMethod.class));
    private static final Map<String, PaymentMethod> SUPPORTED_METHODS = Map.of(
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

        params.add("testmode="+ key.testMode);

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
        return getPaymentResult(spec);
    }

    private PaymentResult getPaymentResult(PaymentSpecification spec) {
        try {
            var event = spec.getEvent();
            String eventName = event.getShortName();
            var configuration = getConfiguration(ConfigurationLevel.event(event));

            String baseUrl = StringUtils.removeEnd(configuration.get(BASE_URL).getRequiredValue(), "/");

            String bookUrl = baseUrl + "/event/" + eventName + "/reservation/" + spec.getReservationId() + "/book";

            var reservation = ticketReservationRepository.findReservationById(spec.getReservationId());
            int tickets = ticketRepository.countTicketsInReservation(spec.getReservationId());
            Map<String, Object> payload = Map.of(
                "amount", Map.of("value", spec.getOrderSummary().getTotalPrice(), "currency", spec.getEvent().getCurrency()),
                "description", String.format("%s - %d ticket(s) for event %s", configurationManager.getShortReservationID(spec.getEvent(), reservation), tickets, spec.getEvent().getDisplayName()),
                "redirectUrl", bookUrl,
                "webhookUrl", baseUrl + "/webhook/mollie/api/event/" + eventName + "/reservation/" + spec.getReservationId(),
                "metadata", MetadataBuilder.buildMetadata(spec, Map.of())
            );

            HttpRequest request = requestFor(PAYMENTS_ENDPOINT, configuration)
                .header(HttpUtils.CONTENT_TYPE, HttpUtils.APPLICATION_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(Json.GSON.toJson(payload)))
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if(HttpUtils.callSuccessful(response)) {
                //try (var responseReader = new InputStreamReader(response.body())) {
                    var body = JsonParser.parseString(response.body()).getAsJsonObject();
                    var checkoutLink = body.getAsJsonObject("_links")
                        .getAsJsonObject("checkout")
                        .get("href").getAsString();
                    // TODO ensure that the reservation expires *after* the payment
                    // "expiresAt": "2018-03-20T09:28:37+00:00",
                    ticketReservationRepository.updateReservationStatus(spec.getReservationId(), TicketReservation.TicketReservationStatus.EXTERNAL_PROCESSING_PAYMENT.toString());
                    return PaymentResult.redirect(checkoutLink);
                //}
            } else {
                log.warn("was not able to create a payment for reservation id " + spec.getReservationId());
                return PaymentResult.failed(ErrorsCode.STEP_2_PAYMENT_REQUEST_CREATION);
            }
        } catch (Exception e) {
            log.warn(e);
            return PaymentResult.failed(ErrorsCode.STEP_2_PAYMENT_REQUEST_CREATION);
        }
    }

    @Override
    public boolean accept(Transaction transaction) {
        return PaymentProxy.MOLLIE == transaction.getPaymentProxy();
    }

    @Override
    public PaymentMethod getPaymentMethodForTransaction(Transaction transaction) {
        // FIXME NPE
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
                .map(paymentId -> new MollieWebhookPayload(paymentId, additionalInfo.get("eventName")));
        } catch(Exception e) {
            log.warn("got exception while trying to decode Mollie Webhook Payload", e);
        }
        return Optional.empty();
    }

    @Override
    public PaymentWebhookResult processWebhook(TransactionWebhookPayload payload,
                                               Transaction transaction,
                                               PaymentContext paymentContext) {

        var paymentId = ((MollieWebhookPayload)payload).getPaymentId();
        var eventShortName = ((MollieWebhookPayload)payload).getEventName();
        Event event = eventRepository.findByShortName(eventShortName);
        HttpRequest request = requestFor(PAYMENTS_ENDPOINT+"/"+paymentId, getConfiguration(ConfigurationLevel.event(event))).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if(HttpUtils.callSuccessful(response) && Objects.nonNull(response.body())) {
            Map<String, Object> res = Json.GSON.fromJson(response.body(), (new TypeToken<Map<String, Object>>() {}).getType());
            //load metadata, check that reservationId match

            //see statuses: https://www.mollie.com/en/docs/status
            String status = (String) res.get("status");
            //open cancelled expired failed pending paid paidout refunded charged_back
            if("paid".equals(status)) {
                //TODO: register payment -> fetch reservationId from metadata -> switch as paid etc...
            } else if("expired".equals(status)) {
                //TODO: set reservation to expired so it can be handled by the job
            }
        } else {
            String msg = "was not able to get payment id " + paymentId + " for event " + eventShortName + " : " + response.body();
            log.warn(msg);
            throw new Exception(msg);
        }
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
}
