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
import alfio.model.system.Configuration;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import okhttp3.*;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static alfio.model.system.ConfigurationKeys.*;

@Log4j2
@AllArgsConstructor
class MailgunMailer implements Mailer {

    private final OkHttpClient client = new OkHttpClient();
    private final ConfigurationManager configurationManager;

    
    private RequestBody prepareBody(EventAndOrganizationId event, String fromName, String to, List<String> cc, String subject, String text,
                                    Optional<String> html, Attachment... attachments) {

        String from = fromName + " <" + configurationManager.getRequiredValue(Configuration.from(event, MAILGUN_FROM)) +">";

        if (ArrayUtils.isEmpty(attachments)) {
            FormBody.Builder builder = new FormBody.Builder()
                    .add("from", from)
                    .add("to", to)
                    .add("subject", subject)
                    .add("text", text);
            if(cc != null && !cc.isEmpty()) {
                builder.add("cc", StringUtils.join(cc, ','));
            }

            String replyTo = configurationManager.getStringConfigValue(Configuration.from(event, MAIL_REPLY_TO), "");
            if(StringUtils.isNotBlank(replyTo)) {
                builder.add("h:Reply-To", replyTo);
            }
            html.ifPresent((htmlContent) -> builder.add("html", htmlContent));
            return builder.build();

        } else {
            MultipartBody.Builder multipartBuilder = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM);

            multipartBuilder.addFormDataPart("from", from)
                    .addFormDataPart("to", to)
                    .addFormDataPart("subject", subject)
                    .addFormDataPart("text", text);

            if(cc != null && !cc.isEmpty()) {
                multipartBuilder.addFormDataPart("cc", StringUtils.join(cc, ','));
            }

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
    public void send(EventAndOrganizationId event, String fromName, String to, List<String> cc, String subject, String text,
                     Optional<String> html, Attachment... attachment) {

        String apiKey = configurationManager.getRequiredValue(Configuration.from(event, MAILGUN_KEY));
        String domain = configurationManager.getRequiredValue(Configuration.from(event, MAILGUN_DOMAIN));
        boolean useEU = configurationManager.getBooleanConfigValue(Configuration.from(event, MAILGUN_EU), false);

        String baseUrl = useEU ? "https://api.eu.mailgun.net/v3/" : "https://api.mailgun.net/v3/";
        try {

            RequestBody formBody = prepareBody(event, fromName, to, cc, subject, text, html,
                    attachment);

            Request request = new Request.Builder()
                    .url(baseUrl + domain + "/messages")
                    .header("Authorization", Credentials.basic("api", apiKey))
                    .post(formBody).build();

            try(Response resp = client.newCall(request).execute()) {
                if (!resp.isSuccessful()) {
                    log.warn("sending email was not successful:" + resp);
                    throw new IllegalStateException("Attempt to send a message failed. Result is: "+resp.code());
                }
            }
        } catch (IOException e) {
            log.warn("error while sending email", e);
        }
    }
}
