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

import alfio.model.Configurable;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log4j2
@AllArgsConstructor
public class SendGridMailer implements Mailer {

    private final HttpClient client;

    private final ConfigurationManager configurationManager;

    @Override
    public void send(Configurable configurable, final String fromName, final String to, final List<String> cc, final String subject, final String text, final Optional<String> html, final Attachment... attachment) {
        final var config = configurationManager.getFor(Set.of(ConfigurationKeys.SENDGRID_API_KEY, ConfigurationKeys.SENDGRID_FROM, ConfigurationKeys.MAIL_REPLY_TO), configurable.getConfigurationLevel());
        final var from = config.get(ConfigurationKeys.SENDGRID_FROM).getRequiredValue();
        final var personalizations = createPersonalizations(to, cc, subject);
        final var contents = createContents(text, html);
        final var payload = new HashMap<String, Object>();
        if (ArrayUtils.isNotEmpty(attachment)) {
            addAttachments(payload, attachment);
        }
        payload.put("from", Map.of("email", from, "name", fromName));
        payload.put("personalizations", personalizations);
        payload.put("content", contents);
        //prepare request
        final var body = Json.GSON.toJson(payload);
        final var request = HttpRequest.newBuilder(URI.create("https://api.sendgrid.com/v3/mail/send"))
            .header(HttpUtils.AUTHORIZATION, String.format("Bearer %s", config.get(ConfigurationKeys.SENDGRID_API_KEY).getRequiredValue()))
            .header(HttpUtils.CONTENT_TYPE, HttpUtils.APPLICATION_JSON)
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (!HttpUtils.callSuccessful(response)) {
                log.warn("sending email was not successful: {} ", response);
                throw new IllegalStateException("Attempt to send a message failed. Result is: " + response.statusCode());
            }
        } catch (IOException e) {
            log.warn("error while sending email", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("error while sending email", e);
        }
    }

    private List<Map<String, Object>> createPersonalizations(final String to, final List<String> cc, final String subject) {
        final var recipients = new ArrayList<>();
        recipients.add(Map.of("email", to));
        if (CollectionUtils.isNotEmpty(cc)) {
            recipients.addAll(cc.stream().map(email -> Map.of("email", email)).collect(Collectors.toList()));
        }
        return List.of(Map.of("to", recipients, "subject", subject));
    }

    private List<Map<String, String>> createContents(final String text, final Optional<String> html) {
        final var contents = new ArrayList<Map<String, String>>();
        contents.add(Map.of("type", MediaType.TEXT_PLAIN_VALUE, "value", text));
        if (html != null) {
            html.ifPresent(htmlContent -> contents.add(Map.of("type", MediaType.TEXT_HTML_VALUE, "value", htmlContent)));
        }
        return contents;
    }

    private void addAttachments(final Map<String, Object> payload, final Attachment[] attachment) {
        final var attachments = Stream.of(attachment)
            .map(attach -> Map.of("filename", attach.getFilename(), "content", attach.getSource(), "content_id", attach.getIdentifier().name(), "type", attach.getContentType())).collect(Collectors.toList());
        payload.put("attachments", attachments);
    }
}
