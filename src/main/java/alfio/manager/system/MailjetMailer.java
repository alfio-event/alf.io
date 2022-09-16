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
import alfio.repository.user.OrganizationRepository;
import alfio.util.HttpUtils;
import alfio.util.Json;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Base64;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;

import static alfio.model.system.ConfigurationKeys.*;

@Log4j2
class MailjetMailer extends BaseMailer  {

    private final HttpClient client;
    private final ConfigurationManager configurationManager;

    MailjetMailer(HttpClient httpClient,
                         ConfigurationManager configurationManager,
                         OrganizationRepository organizationRepository) {
        super(organizationRepository);
        this.client = httpClient;
        this.configurationManager = configurationManager;
    }

    @Override
    public void send(Configurable configurable, String fromName, String to, List<String> cc, String subject, String text, Optional<String> html, Attachment... attachment) {

        var conf = configurationManager.getFor(
            EnumSet.of(MAILJET_APIKEY_PUBLIC, MAILJET_APIKEY_PRIVATE, MAILJET_FROM, MAIL_REPLY_TO, MAIL_SET_ORG_REPLY_TO), configurable.getConfigurationLevel());


        String apiKeyPublic = conf.get(MAILJET_APIKEY_PUBLIC).getRequiredValue();
        String apiKeyPrivate = conf.get(MAILJET_APIKEY_PRIVATE).getRequiredValue();
        String fromEmail = conf.get(MAILJET_FROM).getRequiredValue();

        //https://dev.mailjet.com/guides/?shell#sending-with-attached-files
        Map<String, Object> mailPayload = new HashMap<>();

        List<Map<String, String>> recipients = new ArrayList<>();
        recipients.add(Collections.singletonMap("Email", to));
        if(cc != null && !cc.isEmpty()) {
            recipients.addAll(cc.stream().map(email -> Collections.singletonMap("Email", email)).collect(Collectors.toList()));
        }

        mailPayload.put("FromEmail", fromEmail);
        mailPayload.put("FromName", fromName);
        mailPayload.put("Subject", subject);
        mailPayload.put("Text-part", text);
        html.ifPresent(h -> mailPayload.put("Html-part", h));
        mailPayload.put("Recipients", recipients);

        setReplyToIfPresent(conf, configurable.getOrganizationId(),
            address -> mailPayload.put("Headers", Collections.singletonMap("Reply-To", address)));

        if(attachment != null && attachment.length > 0) {
            mailPayload.put("Attachments", Arrays.stream(attachment).map(MailjetMailer::fromAttachment).collect(Collectors.toList()));
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.mailjet.com/v3/send"))
            .header(HttpUtils.AUTHORIZATION, HttpUtils.basicAuth(apiKeyPublic, apiKeyPrivate))
            .header(HttpUtils.CONTENT_TYPE, HttpUtils.APPLICATION_JSON)
            .POST(HttpRequest.BodyPublishers.ofString(Json.GSON.toJson(mailPayload)))
            .build();

        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if(!HttpUtils.callSuccessful(response)) {
                log.warn("sending email was not successful:" + response);
                throw new IllegalStateException("Attempt to send a message failed. Result is: "+response.statusCode());
            }
        } catch (IOException e) {
            log.warn("error while sending email", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("error while sending email", e);
        }
    }



    private static Map<String, String> fromAttachment(Attachment a) {
        Map<String, String> m = new HashMap<>();
        m.put("Content-type", a.getContentType());
        m.put("Filename", a.getFilename());
        m.put("content", Base64.encodeBase64String(a.getSource()));
        return m;
    }
}
