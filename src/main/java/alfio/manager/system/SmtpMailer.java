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
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.MimeMessagePreparator;

import javax.activation.FileTypeMap;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;

import static alfio.model.system.ConfigurationKeys.*;

@Log4j2
@AllArgsConstructor
class SmtpMailer implements Mailer {
    
    private final ConfigurationManager configurationManager;

    @Override
    public void send(Event event, String to, String subject, String text,
            Optional<String> html, Attachment... attachments) {
        MimeMessagePreparator preparator = (mimeMessage) -> {
            MimeMessageHelper message = html.isPresent() || !ArrayUtils.isEmpty(attachments) ? new MimeMessageHelper(mimeMessage, true, "UTF-8")
                    : new MimeMessageHelper(mimeMessage, "UTF-8");
            message.setSubject(subject);
            message.setFrom(configurationManager.getRequiredValue(Configuration.from(event.getOrganizationId(), event.getId(), SMTP_FROM_EMAIL)), event.getDisplayName());
            String replyTo = configurationManager.getStringConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), MAIL_REPLY_TO), "");
            if(StringUtils.isNotBlank(replyTo)) {
                message.setReplyTo(replyTo);
            }
            message.setTo(to);
            if (html.isPresent()) {
                message.setText(text, html.get());
            } else {
                message.setText(text, false);
            }

            if (attachments != null) {
                for (Attachment a : attachments) {
                    message.addAttachment(a.getFilename(), new ByteArrayResource(a.getSource()), a.getContentType());
                }
            }
            
            message.getMimeMessage().saveChanges();
            message.getMimeMessage().removeHeader("Message-ID");
        };
        toMailSender(event).send(preparator);
    }
    
    private JavaMailSender toMailSender(Event event) {
        JavaMailSenderImpl r = new CustomJavaMailSenderImpl();
        r.setDefaultEncoding("UTF-8");

        r.setHost(configurationManager.getRequiredValue(Configuration.from(event.getOrganizationId(), event.getId(), SMTP_HOST)));
        r.setPort(Integer.valueOf(configurationManager.getRequiredValue(Configuration.from(event.getOrganizationId(), event.getId(), SMTP_PORT))));
        r.setProtocol(configurationManager.getRequiredValue(Configuration.from(event.getOrganizationId(), event.getId(), SMTP_PROTOCOL)));
        r.setUsername(configurationManager.getStringConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), SMTP_USERNAME), null));
        r.setPassword(configurationManager.getStringConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), SMTP_PASSWORD), null));

        String properties = configurationManager.getStringConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), SMTP_PROPERTIES), null);

        if (properties != null) {
            try {
                Properties prop = PropertiesLoaderUtils.loadProperties(new EncodedResource(new ByteArrayResource(
                        properties.getBytes(StandardCharsets.UTF_8)), "UTF-8"));
                r.setJavaMailProperties(prop);
            } catch (IOException e) {
                log.warn("error while setting the mail sender properties", e);
            }
        }
        return r;
    }
    
    static class CustomMimeMessage extends MimeMessage {
        
        private String defaultEncoding;
        private FileTypeMap defaultFileTypeMap;

        CustomMimeMessage(Session session, String defaultEncoding, FileTypeMap defaultFileTypeMap) {
            super(session);
            this.defaultEncoding = defaultEncoding;
            this.defaultFileTypeMap = defaultFileTypeMap;
        }

        CustomMimeMessage(Session session, InputStream contentStream) throws MessagingException {
            super(session, contentStream);
        }
        
        public final String getDefaultEncoding() {
            return this.defaultEncoding;
        }

        public final FileTypeMap getDefaultFileTypeMap() {
            return this.defaultFileTypeMap;
        }
        
        @Override
        protected void updateMessageID() throws MessagingException {
            removeHeader("Message-Id");
        }
        
        @Override
        public void setHeader(String name, String value) throws MessagingException {
            if(!"Message-Id".equals(name)) {
                super.setHeader(name, value);
            }
        }
    }
    
    static class CustomJavaMailSenderImpl extends JavaMailSenderImpl {
        @Override
        public MimeMessage createMimeMessage() {
            return new CustomMimeMessage(getSession(), getDefaultEncoding(), getDefaultFileTypeMap());
        }
        
        @Override
        public MimeMessage createMimeMessage(InputStream contentStream) throws MailException {
            try {
                return new CustomMimeMessage(getSession(), contentStream);
            }
            catch (MessagingException ex) {
                throw new MailParseException("Could not parse raw MIME content", ex);
            }
        }
    }

}
