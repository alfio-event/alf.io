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
package alfio.plugin.mailchimp;

import alfio.model.*;
import alfio.model.plugin.PluginConfigOption;
import alfio.model.system.ComponentType;
import alfio.plugin.PluginDataStorageProvider;
import alfio.plugin.PluginDataStorageProvider.PluginDataStorage;
import alfio.plugin.ReservationConfirmationPlugin;
import alfio.plugin.TicketAssignmentPlugin;
import alfio.plugin.WaitingQueueSubscriptionPlugin;
import alfio.util.Json;
import com.squareup.okhttp.*;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Hex;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
@Log4j2
public class MailChimpPlugin implements ReservationConfirmationPlugin, TicketAssignmentPlugin, WaitingQueueSubscriptionPlugin {

    private static final String DATA_CENTER = "dataCenter";
    private static final String API_KEY = "apiKey";
    private static final String LIST_ID = "listId";
    private static final String LIST_ADDRESS = "https://%s.api.mailchimp.com/3.0/lists/%s/";
    private static final String LIST_MEMBERS = "members/";
    private static final String MERGE_FIELDS = "merge-fields/";
    private static final String FAILURE_MSG = "cannot add user {email: %s, name:%s, language: %s} to the list (%s)";
    private static final String ALFIO_EVENT_KEY = "ALFIO_EKEY";
    private static final String APPLICATION_JSON = "application/json";
    private final String id = "alfio.mailchimp";
    private final PluginDataStorage pluginDataStorage;
    private final OkHttpClient httpClient = new OkHttpClient();

    public MailChimpPlugin(PluginDataStorageProvider pluginDataStorageProvider) {
        this.pluginDataStorage = pluginDataStorageProvider.getDataStorage(id);
    }


    @Override
    public void onTicketAssignment(Ticket ticket) {

        Event event = pluginDataStorage.getEventById(ticket.getEventId());
        CustomerName customerName = new CustomerName(ticket.getFullName(), ticket.getFirstName(), ticket.getLastName(), event);
        subscribeUser(ticket.getEmail(), customerName, ticket.getUserLanguage(), ticket.getEventId(), event.getShortName());
    }

    @Override
    public void onReservationConfirmation(TicketReservation ticketReservation, int eventId) {
        Event event = pluginDataStorage.getEventById(eventId);
        CustomerName customerName = new CustomerName(ticketReservation.getFullName(), ticketReservation.getFirstName(), ticketReservation.getLastName(), event);
        subscribeUser(ticketReservation.getEmail(), customerName, ticketReservation.getUserLanguage(), eventId, event.getShortName());
    }

    @Override
    public void onWaitingQueueSubscription(WaitingQueueSubscription waitingQueueSubscription) {
        Event event = pluginDataStorage.getEventById(waitingQueueSubscription.getEventId());
        CustomerName customerName = new CustomerName(waitingQueueSubscription.getFullName(), waitingQueueSubscription.getFirstName(), waitingQueueSubscription.getLastName(), event);
        subscribeUser(waitingQueueSubscription.getEmailAddress(), customerName, waitingQueueSubscription.getUserLanguage(), waitingQueueSubscription.getEventId(), event.getShortName());
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return "Mailchimp newsletter subscriber";
    }

    @Override
    public boolean isEnabled(int eventId) {
        return pluginDataStorage.getConfigValue(ENABLED_CONF_NAME, eventId).map(Boolean::parseBoolean).orElse(false);
    }

    @Override
    public Collection<PluginConfigOption> getConfigOptions(int eventId) {
        return Arrays.asList(new PluginConfigOption(getId(), eventId, DATA_CENTER, "", "The MailChimp data center used by your account (e.g. us6)", ComponentType.TEXT),
                new PluginConfigOption(getId(), eventId, API_KEY, "", "the Mailchimp API Key", ComponentType.TEXT),
                new PluginConfigOption(getId(), eventId, LIST_ID, "", "the list ID", ComponentType.TEXT));
    }

    @Override
    public void install(int eventId) {
        getConfigOptions(eventId).forEach(o -> pluginDataStorage.insertConfigValue(eventId, o.getOptionName(), o.getOptionValue(), o.getDescription(), o.getComponentType()));
    }

    private Optional<String> getListAddress(int eventId, String email, CustomerName name, String language) {
        Optional<String> dataCenter = pluginDataStorage.getConfigValue(DATA_CENTER, eventId);
        Optional<String> listId = pluginDataStorage.getConfigValue(LIST_ID, eventId);
        if(dataCenter.isPresent() && listId.isPresent()) {
            return Optional.of(String.format(LIST_ADDRESS, dataCenter.get(), listId.get()));
        } else {
            pluginDataStorage.registerFailure(String.format(FAILURE_MSG, email, name, language, "check listId and dataCenter"), eventId);
        }
        return Optional.empty();
    }

    private Optional<String> getApiKey(int eventId, String email, CustomerName name, String language) {
        Optional<String> apiKey = pluginDataStorage.getConfigValue(API_KEY, eventId);
        if(!apiKey.isPresent()) {
            pluginDataStorage.registerFailure(String.format(FAILURE_MSG, email, name, language, "missing API Key"), eventId);
        }
        return apiKey;
    }

    private void subscribeUser(String email, CustomerName name, String language, int eventId, String eventKey) {
        Optional<String> listAddress = getListAddress(eventId, email, name, language);
        Optional<String> apiKey = getApiKey(eventId, email, name, language);
        if(listAddress.isPresent() && apiKey.isPresent()) {
            createMergeFieldIfNotPresent(listAddress.get(), apiKey.get(), eventId, eventKey);
            send(eventId, listAddress.get() + LIST_MEMBERS + getMd5Email(email), apiKey.get(), email, name, language, eventKey);
        }
    }

    static String getMd5Email(String email) {
        try {
            return Hex.encodeHexString(MessageDigest.getInstance("MD5").digest(email.trim().getBytes("UTF-8")));
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            return email;//should never happen...
        }
    }

    private boolean send(int eventId, String address, String apiKey, String email, CustomerName name, String language, String eventKey) {
        Map<String, Object> content = new HashMap<>();
        content.put("email_address", email);
        content.put("status", "subscribed");
        Map<String, String> mergeFields = new HashMap<>();
        mergeFields.put("FNAME", name.isHasFirstAndLastName() ? name.getFirstName() : name.getFullName());
        mergeFields.put(ALFIO_EVENT_KEY, eventKey);
        content.put("merge_fields", mergeFields);
        content.put("language", language);
        Request request = new Request.Builder()
                .url(address)
                .header("Authorization", Credentials.basic("alfio", apiKey))
                .put(RequestBody.create(MediaType.parse(APPLICATION_JSON), Json.GSON.toJson(content, Map.class)))
                .build();
        try {
            Response response = httpClient.newCall(request).execute();
            if(response.isSuccessful()) {
                pluginDataStorage.registerSuccess(String.format("user %s has been subscribed to list", email), eventId);
                return true;
            }
            String responseBody = response.body().string();
            if(response.code() != 400 || responseBody.contains("\"errors\"")) {
                pluginDataStorage.registerFailure(String.format(FAILURE_MSG, email, name, language, responseBody), eventId);
                return false;
            } else {
                pluginDataStorage.registerWarning(String.format(FAILURE_MSG, email, name, language, responseBody), eventId);
            }
            return true;
        } catch (IOException e) {
            pluginDataStorage.registerFailure(String.format(FAILURE_MSG, email, name, language, e.toString()), eventId);
            return false;
        }
    }

    private void createMergeFieldIfNotPresent(String listAddress, String apiKey, int eventId, String eventKey) {
        Request request = new Request.Builder()
            .url(listAddress + MERGE_FIELDS)
            .header("Authorization", Credentials.basic("alfio", apiKey))
            .get()
            .build();
        try {
            Response response = httpClient.newCall(request).execute();
            String responseBody = response.body().string();
            if(!responseBody.contains(ALFIO_EVENT_KEY)) {
                log.debug("can't find ALFIO_EKEY for event "+eventKey);
                createMergeField(listAddress, apiKey, eventKey, eventId);
            }
        } catch (IOException e) {
            pluginDataStorage.registerFailure(String.format("Cannot get merge fields for %s, got: %s", eventKey, e.getMessage()), eventId);
            log.warn("exception while reading merge fields for event id "+eventId, e);
        }
    }

    private void createMergeField(String listAddress, String apiKey, String eventKey, int eventId) {

        Map<String, Object> mergeField = new HashMap<>();
        mergeField.put("tag", ALFIO_EVENT_KEY);
        mergeField.put("name", "Alfio's event key");
        mergeField.put("type", "text");
        mergeField.put("required", false);
        mergeField.put("public", false);


        Request request = new Request.Builder()
            .url(listAddress + MERGE_FIELDS)
            .header("Authorization", Credentials.basic("alfio", apiKey))
            .post(RequestBody.create(MediaType.parse(APPLICATION_JSON), Json.GSON.toJson(mergeField, Map.class)))
            .build();

        try {
            Response response = httpClient.newCall(request).execute();
            if(!response.isSuccessful()) {
                log.debug("can't create {} merge field. Got: {}", ALFIO_EVENT_KEY, response.body().string());
            }
        } catch (IOException e) {
            pluginDataStorage.registerFailure(String.format("Cannot create merge field for %s, got: %s", eventKey, e.getMessage()), eventId);
            log.warn("exception while creating ALFIO_EKEY for event id "+eventId, e);
        }
    }

}
