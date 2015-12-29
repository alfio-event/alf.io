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

import alfio.model.Event;
import alfio.model.system.Configuration;
import com.squareup.okhttp.*;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import static alfio.model.system.ConfigurationKeys.*;

@Log4j2
@AllArgsConstructor
class MailgunMailer implements Mailer {

    private final OkHttpClient client = new OkHttpClient();
    private final ConfigurationManager configurationManager;

    
    private RequestBody prepareBody(Event event, String to, String subject, String text,
            Optional<String> html, Attachment... attachments)
            throws IOException {

        String from = event.getDisplayName() + " <" + configurationManager.getRequiredValue(Configuration.from(event.getOrganizationId(), event.getId(), MAILGUN_FROM)) +">";

        if (ArrayUtils.isEmpty(attachments)) {
            FormEncodingBuilder builder = new FormEncodingBuilder()
                    .add("from", from).add("to", to).add("subject", subject)
                    .add("text", text);

            String replyTo = configurationManager.getStringConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), MAIL_REPLY_TO), "");
            if(StringUtils.isNotBlank(replyTo)) {
                builder.add("h:Reply-To", replyTo);
            }
            html.ifPresent((htmlContent) -> builder.add("html", htmlContent));
            return builder.build();

        } else {
            // https://github.com/square/okhttp/blob/parent-2.1.0/samples/guide/src/main/java/com/squareup/okhttp/recipes/PostMultipart.java
            MultipartBuilder multipartBuilder = new MultipartBuilder()
                    .type(MultipartBuilder.FORM);

            multipartBuilder.addFormDataPart("from", from)
                    .addFormDataPart("to", to)
                    .addFormDataPart("subject", subject)
                    .addFormDataPart("text", text);

            html.ifPresent((htmlContent) -> multipartBuilder.addFormDataPart(
                    "html", htmlContent));

            for (Attachment attachment : attachments) {
                byte[] data = attachment.getSource();
                multipartBuilder.addFormDataPart("attachment", attachment
                        .getFilename(), RequestBody.create(MediaType
                        .parse(attachment.getContentType()), Arrays.copyOf(data, data.length)));
            }
            return multipartBuilder.build();
        }
    }

    @Override
    public void send(Event event, String to, String subject, String text,
            Optional<String> html, Attachment... attachment) {

        String apiKey = configurationManager.getRequiredValue(Configuration.from(event.getOrganizationId(), event.getId(), MAILGUN_KEY));
        String domain = configurationManager.getRequiredValue(Configuration.from(event.getOrganizationId(), event.getId(), MAILGUN_DOMAIN));

        try {

            RequestBody formBody = prepareBody(event, to, subject, text, html,
                    attachment);

            Request request = new Request.Builder()
                    .url("https://api.mailgun.net/v2/" + domain + "/messages")
                    .header("Authorization", Credentials.basic("api", apiKey))
                    .post(formBody).build();

            Response resp = client.newCall(request).execute();
            if (!resp.isSuccessful()) {
                log.warn("sending email was not successful:" + resp);
            }
        } catch (IOException e) {
            log.warn("error while sending email", e);
        }
    }
}
