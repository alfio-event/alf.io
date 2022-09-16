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
import alfio.repository.user.OrganizationRepository;
import alfio.util.HttpUtils;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Stream;

import static alfio.model.system.ConfigurationKeys.*;

@Log4j2
class MailgunMailer extends BaseMailer {

    private final HttpClient client;
    private final ConfigurationManager configurationManager;

    MailgunMailer(HttpClient client,
                         ConfigurationManager configurationManager,
                         OrganizationRepository organizationRepository) {
        super(organizationRepository);
        this.client = client;
        this.configurationManager = configurationManager;
    }


    private static Map<String, String> getEmailData(String from,
                                                    String to,
                                                    List<String> cc,
                                                    String subject,
                                                    String text,
                                                    Optional<String> html) {
        Map<String, String> emailData = new HashMap<>(Map.of(
            "from", from,
            "to", to,
            "subject", subject,
            "text", text
        ));
        
        if(cc != null && !cc.isEmpty()) {
            emailData.put("cc", StringUtils.join(cc, ','));
        }
        html.ifPresent(htmlContent -> emailData.put("html", htmlContent));
        return emailData;
    }

    @Override
    public void send(Configurable configurable, String fromName, String to, List<String> cc, String subject, String text,
                     Optional<String> html, Attachment... attachment) {

        var conf = configurationManager.getFor(
            Set.of(
                MAILGUN_KEY,
                MAILGUN_DOMAIN,
                MAILGUN_EU,
                MAILGUN_FROM,
                MAIL_REPLY_TO,
                MAIL_SET_ORG_REPLY_TO),
            configurable.getConfigurationLevel());

        String apiKey = conf.get(MAILGUN_KEY).getRequiredValue();
        String domain = conf.get(MAILGUN_DOMAIN).getRequiredValue();
        boolean useEU = conf.get(MAILGUN_EU).getValueAsBooleanOrDefault();

        String baseUrl = useEU ? "https://api.eu.mailgun.net/v3/" : "https://api.mailgun.net/v3/";
        try {

            var from = fromName + " <" + conf.get(MAILGUN_FROM).getRequiredValue() +">";

            var emailData = getEmailData(from, to, cc, subject, text, html);

            setReplyToIfPresent(conf, configurable.getOrganizationId(),
                replyTo -> emailData.put("h:Reply-To", replyTo));

            var requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + domain + "/messages"))
                .header(HttpUtils.AUTHORIZATION, HttpUtils.basicAuth("api", apiKey));

            if (ArrayUtils.isEmpty(attachment)) {
                requestBuilder.header(HttpUtils.CONTENT_TYPE, HttpUtils.APPLICATION_FORM_URLENCODED);
                requestBuilder.POST(HttpUtils.ofFormUrlEncodedBody(emailData));
            } else {
                var mpb = new HttpUtils.MultiPartBodyPublisher();
                requestBuilder.header(HttpUtils.CONTENT_TYPE, HttpUtils.MULTIPART_FORM_DATA+";boundary=\""+mpb.getBoundary()+"\"");
                emailData.forEach(mpb::addPart);
                Stream.of(attachment).forEach(a -> mpb.addPart("attachment", () -> new ByteArrayInputStream(a.getSource()), a.getFilename(), a.getContentType()));
                requestBuilder.POST(mpb.build());
            }

            HttpRequest request = requestBuilder.build();

            HttpResponse<?> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if(!HttpUtils.callSuccessful(response)) {
                log.warn("sending email was not successful:" + response);
                throw new IllegalStateException("Attempt to send a message failed. Result is: "+response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Request interrupted while calling Mailgun API", e);
            throw new IllegalStateException(e);
        } catch (IOException e) {
            log.warn("error while sending email", e);
            throw new IllegalStateException(e);
        }
    }
}
