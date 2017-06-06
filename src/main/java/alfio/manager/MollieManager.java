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

import alfio.manager.system.ConfigurationManager;
import alfio.model.CustomerName;
import alfio.model.Event;
import alfio.model.OrderSummary;
import alfio.model.TicketReservation;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.repository.EventRepository;
import alfio.repository.TicketReservationRepository;
import alfio.util.Json;
import com.google.gson.reflect.TypeToken;
import com.squareup.okhttp.*;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
@Log4j2
@AllArgsConstructor
public class MollieManager {

    private final OkHttpClient client = new OkHttpClient();
    private final ConfigurationManager configurationManager;
    private final MessageSource messageSource;
    private final TicketReservationRepository ticketReservationRepository;
    private final EventRepository eventRepository;

    public String createCheckoutRequest(Event event, String reservationId, OrderSummary orderSummary, CustomerName customerName, String email, String billingAddress, Locale locale, boolean postponeAssignment) throws Exception {

        String eventName = event.getShortName();

        String baseUrl = StringUtils.removeEnd(configurationManager.getRequiredValue(Configuration.from(event.getOrganizationId(), event.getId(), ConfigurationKeys.BASE_URL)), "/");

        String bookUrl = baseUrl + "/event/" + eventName + "/reservation/" + reservationId + "/book";


        Map<String, Object> payload = new HashMap<>();
        payload.put("amount", orderSummary.getTotalPrice()); // quite ugly, but the mollie api require json floating point...

        //
        String description = messageSource.getMessage("reservation-email-subject", new Object[] {configurationManager.getShortReservationID(event, reservationId), event.getDisplayName()}, locale);
        payload.put("description", description);
        payload.put("redirectUrl", bookUrl);
        payload.put("webhookUrl", baseUrl + "/api/event/" + eventName + "/webhook/mollie"); //for testing: "https://test.alf.io/api/webhook/mollie"


        Map<String, String> initialMetadata = new HashMap<>();
        initialMetadata.put("reservationId", reservationId);
        initialMetadata.put("email", email);
        initialMetadata.put("fullName", customerName.getFullName());
        if (StringUtils.isNotBlank(billingAddress)) {
            initialMetadata.put("billingAddress", billingAddress);
        }

        payload.put("metadata", initialMetadata);

        RequestBody body = RequestBody.create(MediaType.parse("application/json"), Json.GSON.toJson(payload));
        Request request = requestFor("https://api.mollie.nl/v1/payments", event)
            .post(body)
            .build();

        Response resp = client.newCall(request).execute();
        try(ResponseBody responseBody = resp.body()) {
            String respBody = responseBody.string();
            if (!resp.isSuccessful()) {
                String msg = "was not able to create a payment for reservation id " + reservationId + ": " + respBody;
                log.warn(msg);
                throw new Exception(msg);
            } else {

                TicketReservation reservation = ticketReservationRepository.findReservationById(reservationId);
                //TODO: switch the reservation in a status "PROCESSING" -> so we can then handle them in the webhook
                //TODO: see statuses: https://www.mollie.com/en/docs/status
                //      basically, we need to handle the cases "expired", "cancelled" and "paid"
                Map<String, Object> res = Json.GSON.fromJson(respBody, (new TypeToken<Map<String, Object>>() {}).getType());
                return ((Map<String, String> )res.get("links")).get("paymentUrl");
            }
        }
    }


    public void handleWebhook(String eventShortName, String paymentId) throws Exception {
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

    private Request.Builder requestFor(String url, Event event) {
        String mollieAPIKey = configurationManager.getRequiredValue(Configuration.from(event.getOrganizationId(), ConfigurationKeys.MOLLIE_API_KEY));
        return new Request.Builder().url(url).header("Authorization", "Bearer " + mollieAPIKey);
    }
}
