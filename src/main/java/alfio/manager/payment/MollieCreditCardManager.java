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
import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.EventAndOrganizationId;
import alfio.model.TicketReservation;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentContext;
import alfio.model.transaction.PaymentMethod;
import alfio.model.transaction.PaymentProvider;
import alfio.repository.EventRepository;
import alfio.repository.TicketReservationRepository;
import alfio.util.ErrorsCode;
import alfio.util.Json;
import com.google.gson.reflect.TypeToken;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static alfio.model.system.ConfigurationKeys.MOLLIE_CC_ENABLED;

@Component
@Log4j2
@AllArgsConstructor
public class MollieCreditCardManager implements PaymentProvider {

    private final OkHttpClient client = new OkHttpClient();
    private final ConfigurationManager configurationManager;
    private final MessageSource messageSource;
    private final TicketReservationRepository ticketReservationRepository;
    private final EventRepository eventRepository;

    public void handleWebhook(String eventShortName, String reservationId, String paymentId) throws Exception {
        Event event = eventRepository.findByShortName(eventShortName);

        Request request = requestFor("https://api.mollie.nl/v1/payments/"+paymentId, event).get().build();
        Response resp = client.newCall(request).execute();

        try(ResponseBody responseBody = resp.body()) {
            String respBody = responseBody.string();
            if(!resp.isSuccessful()) {
                String msg = "was not able to get payment id " + paymentId + " for event " + eventShortName + " : " + respBody;
                log.warn(msg);
                throw new Exception(msg);
            } else {
                Map<String, Object> res = Json.GSON.fromJson(respBody, (new TypeToken<Map<String, Object>>() {}).getType());

                //load metadata, check that reservationId match

                //see statuses: https://www.mollie.com/en/docs/status
                String status = (String) res.get("status");
                //open cancelled expired failed pending paid paidout refunded charged_back
                if("paid".equals(status)) {
                    //TODO: register payment -> fetch reservationId from metadata -> switch as paid etc...
                } else if("expired".equals(status)) {
                    //TODO: set reservation to expired so it can be handled by the job
                }
            }
        }
    }

    private Request.Builder requestFor(String url, EventAndOrganizationId event) {
        String mollieAPIKey = configurationManager.getRequiredValue(Configuration.from(event.getOrganizationId(), ConfigurationKeys.MOLLIE_API_KEY));
        return new Request.Builder().url(url).header("Authorization", "Bearer " + mollieAPIKey);
    }

    @Override
    public boolean accept(PaymentMethod paymentMethod, PaymentContext context) {
        return paymentMethod == PaymentMethod.CREDIT_CARD && configurationManager.getBooleanConfigValue(context.narrow(MOLLIE_CC_ENABLED), false);
    }

    @Override
    public PaymentResult getToken(PaymentSpecification spec) {
        throw new IllegalStateException("not yet implemented");
    }

    @Override
    public PaymentResult doPayment( PaymentSpecification spec ) {
        try {
            String eventName = spec.getEvent().getShortName();

            String baseUrl = StringUtils.removeEnd(configurationManager.getRequiredValue(Configuration.from(spec.getEvent(), ConfigurationKeys.BASE_URL)), "/");

            String bookUrl = baseUrl + "/event/" + eventName + "/reservation/" + spec.getReservationId() + "/book";


            Map<String, Object> payload = new HashMap<>();
            payload.put("amount", spec.getOrderSummary().getTotalPrice()); // quite ugly, but the mollie api require json floating point...

            //
            String description = messageSource.getMessage("reservation-email-subject", new Object[] {configurationManager.getShortReservationID(spec.getEvent(), ticketReservationRepository.findReservationById(spec.getReservationId())), spec.getEvent().getDisplayName()}, spec.getLocale());
            payload.put("description", description);
            payload.put("redirectUrl", bookUrl);
            payload.put("webhookUrl", baseUrl + "/webhook/mollie/api/event/" + eventName + "/reservation/" + spec.getReservationId());


            payload.put("metadata", MetadataBuilder.buildMetadata(spec, Map.of()));

            RequestBody body = RequestBody.create(MediaType.parse("application/json"), Json.GSON.toJson(payload));
            Request request = requestFor("https://api.mollie.nl/v1/payments", spec.getEvent())
                .post(body)
                .build();

            try (Response resp = client.newCall(request).execute()) {
                ResponseBody responseBody = resp.body();
                String respBody = responseBody != null ? responseBody.string() : "null";
                if (resp.isSuccessful()) {
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
            }
        } catch (Exception e) {
            log.warn(e);
            return PaymentResult.failed( ErrorsCode.STEP_2_PAYMENT_REQUEST_CREATION );
        }
    }
}
