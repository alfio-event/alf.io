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
import alfio.util.HttpUtils;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static alfio.model.system.ConfigurationKeys.*;
import static alfio.util.HttpUtils.CONTENT_TYPE;

@Log4j2
@AllArgsConstructor
class MailgunMailer implements Mailer {

    private final HttpClient client = HttpClient.newHttpClient();
    private final ConfigurationManager configurationManager;

    
    private static HttpRequest.BodyPublisher prepareBody(String from, String to, String replyTo, List<String> cc, String subject, String text,
                                                         Optional<String> html, Attachment... attachments) throws IOException {


        Map<String, String> emailData = getEmailData(from, to, replyTo, cc, subject, text, html);
        if (ArrayUtils.isEmpty(attachments)) {
            return HttpUtils.ofMimeMultipartData(emailData, null);
        } else {
            return buildCustomBodyPublisher(emailData, attachments);
        }
    }

    private static HttpRequest.BodyPublisher buildCustomBodyPublisher(Map<String, String> emailData, Attachment[] attachments) {
        var boundary = new BigInteger(256, ThreadLocalRandom.current()).toString();
        var byteArrays = new ArrayList<byte[]>();
        byte[] separator = ("--" + boundary + "\r\nContent-Disposition: form-data; name=").getBytes(StandardCharsets.UTF_8);
        addEmailData(emailData, byteArrays, separator);
        addAttachments(byteArrays, separator, attachments);
        byteArrays.add(("--" + boundary + "--").getBytes(StandardCharsets.UTF_8));
        return HttpRequest.BodyPublishers.ofByteArrays(byteArrays);
    }

    private static void addEmailData(Map<String, String> emailData, ArrayList<byte[]> byteArrays, byte[] separator) {
        for (Map.Entry<String, String> entry : emailData.entrySet()) {
            byteArrays.add(separator);
            byteArrays.add(("\"" + entry.getKey() + "\"\r\n\r\n" + entry.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void addAttachments(ArrayList<byte[]> byteArrays, byte[] separator, Attachment[] attachments) {
        for(Attachment attachment : attachments) {
            byte[] data = attachment.getSource();
            byteArrays.add(separator);
            byteArrays.add(("\"file\"; filename=\"" + attachment.getFilename()
                + "\"\r\n" + CONTENT_TYPE + ": " + attachment.getContentType()
                + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            byteArrays.add(data);
            byteArrays.add("\r\n".getBytes(StandardCharsets.UTF_8));
        }
    }

    private static Map<String, String> getEmailData(String from, String to, String replyTo, List<String> cc, String subject, String text, Optional<String> html) {
        Map<String, String> emailData = Map.of(
            "from", from,
            "to", to,
            "subject", subject,
            "text", text
        );
        if(cc != null && !cc.isEmpty()) {
            emailData.put("cc", StringUtils.join(cc, ','));
        }
        if(StringUtils.isNoneBlank(replyTo)) {
            emailData.put("h:Reply-To", replyTo);
        }
        html.ifPresent(htmlContent -> emailData.put("html", htmlContent));
        return emailData;
    }

    @Override
    public void send(EventAndOrganizationId event, String fromName, String to, List<String> cc, String subject, String text,
                     Optional<String> html, Attachment... attachment) {

        var conf = configurationManager.getFor(Set.of(MAILGUN_KEY, MAILGUN_DOMAIN, MAILGUN_EU, MAILGUN_FROM, MAIL_REPLY_TO), ConfigurationLevel.event(event));

        String apiKey = conf.get(MAILGUN_KEY).getRequiredValue();
        String domain = conf.get(MAILGUN_DOMAIN).getRequiredValue();
        boolean useEU = conf.get(MAILGUN_EU).getValueAsBooleanOrDefault(false);

        String baseUrl = useEU ? "https://api.eu.mailgun.net/v3/" : "https://api.mailgun.net/v3/";
        try {

            var from = fromName + " <" + conf.get(MAILGUN_FROM).getRequiredValue() +">";

            var replyTo = conf.get(MAIL_REPLY_TO).getValueOrDefault("");

            HttpRequest.BodyPublisher bodyPublisher = prepareBody(from, to, replyTo, cc, subject, text, html, attachment);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + domain + "/messages"))
                .header(HttpUtils.AUTHORIZATION, HttpUtils.basicAuth("api", apiKey))
                .POST(bodyPublisher)
                .build();

            HttpResponse<?> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if(!HttpUtils.callSuccessful(response)) {
                log.warn("sending email was not successful:" + response);
                throw new IllegalStateException("Attempt to send a message failed. Result is: "+response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            log.warn("error while sending email", e);
        }
    }
}
