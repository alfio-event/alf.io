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

import alfio.manager.i18n.MessageSourceManager;
import alfio.manager.support.PaymentResult;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.EventAndOrganizationId;
import alfio.model.TicketReservation;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.*;
import alfio.repository.EventRepository;
import alfio.repository.TicketReservationRepository;
import alfio.util.ErrorsCode;
import alfio.util.HttpUtils;
import alfio.util.Json;
import com.google.gson.reflect.TypeToken;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static alfio.model.system.ConfigurationKeys.MOLLIE_CC_ENABLED;

@Component
@Log4j2
@AllArgsConstructor
public class MollieCreditCardManager implements PaymentProvider {

    private final HttpClient client;
    private final ConfigurationManager configurationManager;
    private final MessageSourceManager messageSourceManager;
    private final TicketReservationRepository ticketReservationRepository;
    private final EventRepository eventRepository;

    public void handleWebhook(String eventShortName, String reservationId, String paymentId) throws Exception {
        Event event = eventRepository.findByShortName(eventShortName);

        HttpRequest request = requestFor("https://api.mollie.nl/v1/payments/"+paymentId, event).GET().build();
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

    private HttpRequest.Builder requestFor(String url, EventAndOrganizationId event) {
        String mollieAPIKey = configurationManager.getFor(ConfigurationKeys.MOLLIE_API_KEY, ConfigurationLevel.organization(event.getOrganizationId())).getRequiredValue();
        return HttpRequest.newBuilder().uri(URI.create(url)).header("Authorization", "Bearer " + mollieAPIKey);
    }

    @Override
    public boolean accept(PaymentMethod paymentMethod, PaymentContext context) {
        return paymentMethod == PaymentMethod.CREDIT_CARD && configurationManager.getFor(MOLLIE_CC_ENABLED, context.getConfigurationLevel()).getValueAsBooleanOrDefault(false);
    }

    @Override
    public PaymentResult getToken(PaymentSpecification spec) {
        throw new IllegalStateException("not yet implemented");
    }

    @Override
    public PaymentResult doPayment( PaymentSpecification spec ) {
        try {
            var event = spec.getEvent();
            String eventName = event.getShortName();

            String baseUrl = StringUtils.removeEnd(configurationManager.getFor(ConfigurationKeys.BASE_URL, ConfigurationLevel.event(event)).getRequiredValue(), "/");

            String bookUrl = baseUrl + "/event/" + eventName + "/reservation/" + spec.getReservationId() + "/book";


            Map<String, Object> payload = new HashMap<>();
            payload.put("amount", spec.getOrderSummary().getTotalPrice()); // quite ugly, but the mollie api require json floating point...

            //
            String description = messageSourceManager.getMessageSourceForEvent(event).getMessage("reservation-email-subject", new Object[] {configurationManager.getShortReservationID(event, ticketReservationRepository.findReservationById(spec.getReservationId())), event.getDisplayName()}, spec.getLocale());
            payload.put("description", description);
            payload.put("redirectUrl", bookUrl);
            payload.put("webhookUrl", baseUrl + "/webhook/mollie/api/event/" + eventName + "/reservation/" + spec.getReservationId());


            payload.put("metadata", MetadataBuilder.buildMetadata(spec, Map.of()));

            HttpRequest request = requestFor("https://api.mollie.nl/v1/payments", event)
                .header(HttpUtils.CONTENT_TYPE, HttpUtils.APPLICATION_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(Json.GSON.toJson(payload)))
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String respBody = response.body();
            if(Objects.isNull(respBody)) {
                respBody = "null";
            }
            if(HttpUtils.callSuccessful(response)) {
                ticketReservationRepository.updateReservationStatus(spec.getReservationId(), TicketReservation.TicketReservationStatus.EXTERNAL_PROCESSING_PAYMENT.toString());
                Map<String, Object> res = Json.GSON.fromJson(respBody, (new TypeToken<Map<String, Object>>() {}).getType());
                @SuppressWarnings("unchecked")
                Map<String, String> links = (Map<String, String>) res.get("links");
                return PaymentResult.redirect( links.get("paymentUrl") );
            } else {
                String msg = "was not able to create a payment for reservation id " + spec.getReservationId() + ": " + respBody;
                log.warn(msg);
                return PaymentResult.failed( ErrorsCode.STEP_2_PAYMENT_REQUEST_CREATION );
            }
        } catch (Exception e) {
            log.warn(e);
            return PaymentResult.failed( ErrorsCode.STEP_2_PAYMENT_REQUEST_CREATION );
        }
    }

    @Override
    public boolean accept(Transaction transaction) {
        return PaymentProxy.MOLLIE == transaction.getPaymentProxy();
    }
}
