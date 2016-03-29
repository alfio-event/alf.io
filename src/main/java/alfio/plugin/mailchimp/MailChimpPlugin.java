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

import alfio.model.Ticket;
import alfio.model.TicketReservation;
import alfio.model.WaitingQueueSubscription;
import alfio.model.plugin.PluginConfigOption;
import alfio.model.system.ComponentType;
import alfio.plugin.PluginDataStorageProvider;
import alfio.plugin.PluginDataStorageProvider.PluginDataStorage;
import alfio.plugin.ReservationConfirmationPlugin;
import alfio.plugin.TicketAssignmentPlugin;
import alfio.plugin.WaitingQueueSubscriptionPlugin;
import alfio.util.Json;
import com.squareup.okhttp.*;

import java.io.IOException;
import java.util.*;

public class MailChimpPlugin implements ReservationConfirmationPlugin, TicketAssignmentPlugin, WaitingQueueSubscriptionPlugin {

    private static final String DATA_CENTER = "dataCenter";
    private static final String API_KEY = "apiKey";
    private static final String LIST_ID = "listId";
    private static final String LIST_ADDRESS = "https://%s.api.mailchimp.com/3.0/lists/%s/members/";
    private static final String FAILURE_MSG = "cannot add user {email: %s, name:%s, language: %s} to the list (%s)";
    private final String id = "alfio.mailchimp";
    private final PluginDataStorage pluginDataStorage;
    private final OkHttpClient httpClient = new OkHttpClient();

    public MailChimpPlugin(PluginDataStorageProvider pluginDataStorageProvider) {
        this.pluginDataStorage = pluginDataStorageProvider.getDataStorage(id);
    }


    @Override
    public void onTicketAssignment(Ticket ticket) {
        subscribeUser(ticket.getEmail(), ticket.getFullName(), ticket.getUserLanguage(), ticket.getEventId());
    }

    @Override
    public void onReservationConfirmation(TicketReservation ticketReservation, int eventId) {
        subscribeUser(ticketReservation.getEmail(), ticketReservation.getFullName(), ticketReservation.getUserLanguage(), eventId);
    }

    @Override
    public void onWaitingQueueSubscription(WaitingQueueSubscription waitingQueueSubscription) {
        subscribeUser(waitingQueueSubscription.getEmailAddress(), waitingQueueSubscription.getFullName(), waitingQueueSubscription.getUserLanguage(), waitingQueueSubscription.getEventId());
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
        getConfigOptions(eventId).stream().forEach(o -> pluginDataStorage.insertConfigValue(eventId, o.getOptionName(), o.getOptionValue(), o.getDescription(), o.getComponentType()));
    }

    private Optional<String> getListAddress(int eventId, String email, String name, String language) {
        Optional<String> dataCenter = pluginDataStorage.getConfigValue(DATA_CENTER, eventId);
        Optional<String> listId = pluginDataStorage.getConfigValue(LIST_ID, eventId);
        if(dataCenter.isPresent() && listId.isPresent()) {
            return Optional.of(String.format(LIST_ADDRESS, dataCenter.get(), listId.get()));
        } else {
            pluginDataStorage.registerFailure(String.format(FAILURE_MSG, email, name, language, "check listId and dataCenter"), eventId);
        }
        return Optional.empty();
    }

    private Optional<String> getApiKey(int eventId, String email, String name, String language) {
        Optional<String> apiKey = pluginDataStorage.getConfigValue(API_KEY, eventId);
        if(!apiKey.isPresent()) {
            pluginDataStorage.registerFailure(String.format(FAILURE_MSG, email, name, language, "missing API Key"), eventId);
        }
        return apiKey;
    }

    private void subscribeUser(String email, String name, String language, int eventId) {
        Optional<String> listAddress = getListAddress(eventId, email, name, language);
        Optional<String> apiKey = getApiKey(eventId, email, name, language);
        if(listAddress.isPresent() && apiKey.isPresent()) {
            send(eventId, listAddress.get(), apiKey.get(), email, name, language);
        }
    }

    private boolean send(int eventId, String address, String apiKey, String email, String name, String language) {
        Map<String, Object> content = new HashMap<>();
        content.put("email_address", email);
        content.put("status", "subscribed");
        content.put("merge_fields", Collections.singletonMap("FNAME", name));
        content.put("language", language);
        Request request = new Request.Builder()
                .url(address)
                .header("Authorization", Credentials.basic("api", apiKey))
                .post(RequestBody.create(MediaType.parse("application/json"), Json.GSON.toJson(content, Map.class)))
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

}
