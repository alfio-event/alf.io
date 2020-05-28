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

package alfio.manager.system;

import alfio.model.EventAndOrganizationId;
import alfio.model.system.ConfigurationKeys;
import alfio.util.HttpUtils;
import alfio.util.Json;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log4j2
@AllArgsConstructor
public class SendInBlueMailer implements Mailer {

    private final HttpClient client;
    private final ConfigurationManager configurationManager;

    @Override
    public void send(final EventAndOrganizationId event, final String fromName, final String to, final List<String> cc, final String subject, final String text, final Optional<String> html, final Attachment... attachment) {
        final var config = configurationManager.getFor(Set.of(ConfigurationKeys.SENDINBLUE_API_KEY, ConfigurationKeys.SENDINBLUE_FROM, ConfigurationKeys.MAIL_REPLY_TO), ConfigurationLevel.event(event));
        final var from = config.get(ConfigurationKeys.SENDINBLUE_FROM).getRequiredValue();
        final var payload = new HashMap<String, Object>();
        if (ArrayUtils.isNotEmpty(attachment)) {
            addAttachments(payload, attachment);
        }
        payload.put("sender", Map.of("email", from, "name", fromName));
        payload.put("to", Map.of("email",to));
        payload.put("cc",createPersonalizations(cc));
        payload.put("replyTo",config.get(ConfigurationKeys.MAIL_REPLY_TO).getRequiredValue());
        payload.put("subject",subject);
        payload.put("templateId",config.get(ConfigurationKeys.SENDINBLUE_TEMPLATE_ID).getRequiredValue());

        //prepare request
        final var body = Json.GSON.toJson(payload);
        final var request = HttpRequest.newBuilder(URI.create("https://api.sendinblue.com/v3/smtp/email"))
            .header(HttpUtils.API_KEY,config.get(ConfigurationKeys.SENDINBLUE_API_KEY).getRequiredValue())
            .header(HttpUtils.CONTENT_TYPE, HttpUtils.APPLICATION_JSON)
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (!HttpUtils.callSuccessful(response)) {
                log.warn("sending email using sendinblue was not successful: {} ", response);
                throw new IllegalStateException("Attempt to send a message using sendinblue is failed. Result is: " + response.statusCode());
            }
        } catch (IOException e) {
            log.warn("error while sending email using sendinblue", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("error while sending email using sendinblue", e);
        }
    }

    private ArrayList<Object> createPersonalizations(final List<String> cc) {
        final var recipients = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(cc)) {
            recipients.addAll(cc.stream().map(email -> Map.of("email", email)).collect(Collectors.toList()));
        }
        return recipients;
    }

    private void addAttachments(final Map<String, Object> payload, final Attachment[] attachment) {
        final var attachments = Stream.of(attachment)
            .map(attach -> Map.of("name", attach.getFilename(), "content", attach.getSource())).collect(Collectors.toList());
        payload.put("attachment", attachments);
    }
}
