package alfio.manager.system;

import alfio.model.EventAndOrganizationId;
import alfio.model.system.ConfigurationKeys;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Attachments;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.http.MediaType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

@Log4j2
@AllArgsConstructor
public class SendGridMailer implements Mailer {
    private static final String ENDPOINT = "mail/send";

    private final ConfigurationManager configurationManager;

    @Override
    public void send(final EventAndOrganizationId event, final String fromName, final String to, final List<String> cc, final String subject, final String text, final Optional<String> html, final Attachment... attachment) {
        //Get config
        final var config = configurationManager.getFor(Set.of(ConfigurationKeys.SENDGRID_API_KEY, ConfigurationKeys.SENDGRID_FROM, ConfigurationKeys.MAIL_REPLY_TO), ConfigurationLevel.event(event));
        //Prepare email body
        try {
            final var body = prepareEmail(to, cc, subject, text, html, config, attachment);
            //prepare request
            final var request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint(ENDPOINT);
            request.setBody(body);
            final var sendGrid = new SendGrid(config.get(ConfigurationKeys.SENDGRID_API_KEY).getRequiredValue());
            final var response = sendGrid.api(request);
            if (!isSuccessful(response.getStatusCode())) {
                log.warn("sending email was not successful: responseCode {}, responseBody {}", response.getStatusCode(), response.getBody());
                throw new IllegalStateException(String.format("Attempt to send a message failed. Result is: %d", response.getStatusCode()));
            }
        } catch (IOException e) {
            log.error("Unexpected exception during email sending", e);
        }
    }

    private String prepareEmail(final String to, final List<String> cc, final String subject, final String text, final Optional<String> html, final Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> config, final Attachment[] attachment) throws IOException {
        final var from = new Email(config.get(ConfigurationKeys.SENDGRID_FROM).getRequiredValue());
        final var toEmail = new Email(to);
        final var textContent = new Content(MediaType.TEXT_PLAIN_VALUE, text);
        final var personalization = new Personalization();
        personalization.addTo(toEmail);
        personalization.setSubject(subject);
        if (CollectionUtils.isNotEmpty(cc)) {
            cc.stream()
                .map(Email::new)
                .forEach(personalization::addCc);
        }
        final var mail = new Mail();
        mail.setFrom(from);
        mail.addContent(textContent);
        mail.addPersonalization(personalization);
        if (html != null) {
            html.ifPresent(htmlContent -> mail.addContent(new Content(MediaType.TEXT_HTML_VALUE, htmlContent)));

        }
        if (ArrayUtils.isNotEmpty(attachment)) {
            Arrays.stream(attachment)
                .map(att -> new Attachments.Builder(att.getFilename(), new ByteArrayInputStream(att.getSource()))
                    .withContentId(att.getIdentifier().name())
                    .withType(att.getContentType()).build()).forEach(mail::addAttachments);
        }
        try {
            return mail.build();
        } catch (IOException e) {
            log.error("Unexpected exception during email preparation", e);
            throw e;
        }
    }

    private boolean isSuccessful(int code) {
        return code >= 200 && code < 300;
    }
}
