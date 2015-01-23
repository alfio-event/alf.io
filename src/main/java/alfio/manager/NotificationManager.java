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
package alfio.manager;

import alfio.manager.support.EmailQueue;
import alfio.manager.support.PartialTicketPDFGenerator;
import alfio.manager.support.PartialTicketTextGenerator;
import alfio.manager.support.TextTemplateGenerator;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.system.Mailer;
import alfio.model.EmailMessage;
import alfio.model.Event;
import alfio.model.Ticket;
import alfio.model.system.ConfigurationKeys;
import alfio.repository.EmailMessageRepository;
import com.google.gson.*;
import com.lowagie.text.DocumentException;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Type;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.*;

import static alfio.model.EmailMessage.Status.*;

@Component
@Log4j2
public class NotificationManager {

    public static final Clock UTC = Clock.systemUTC();
    private final Mailer mailer;
    private final MessageSource messageSource;
    private final EmailMessageRepository emailMessageRepository;
    private final TransactionTemplate tx;
    private final EmailQueue messages;
    private final ConfigurationManager configurationManager;
    private final Gson gson;

    @Autowired
    public NotificationManager(Mailer mailer,
                               MessageSource messageSource,
                               PlatformTransactionManager transactionManager,
                               EmailMessageRepository emailMessageRepository,
                               ConfigurationManager configurationManager) {
        this.messageSource = messageSource;
        this.mailer = mailer;
        this.emailMessageRepository = emailMessageRepository;
        this.configurationManager = configurationManager;
        this.messages = new EmailQueue();
        this.tx = new TransactionTemplate(transactionManager);
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Mailer.Attachment.class, new AttachmentConverter());
        this.gson = builder.create();
    }

    public void sendTicketByEmail(Ticket ticket, Event event, Locale locale, PartialTicketTextGenerator textBuilder, PartialTicketPDFGenerator ticketBuilder) throws DocumentException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ticketBuilder.generate(ticket).createPDF(baos);
        Mailer.Attachment attachment = new Mailer.Attachment("ticket-" + ticket.getUuid() + ".pdf", baos.toByteArray(), "application/pdf");
        String attachments = encodeAttachments(attachment);
        String subject = messageSource.getMessage("ticket-email-subject", new Object[]{event.getShortName()}, locale);
        String text = textBuilder.generate(ticket);
        String checksum = calculateChecksum(ticket.getEmail(), attachments, subject, text);
        String recipient = ticket.getEmail();
        //TODO handle HTML
        tx.execute(status -> emailMessageRepository.insert(event.getId(), recipient, subject, text, attachments, checksum, ZonedDateTime.now(UTC)));
        messages.offer(new EmailMessage(-1, event.getId(), WAITING.name(), recipient, subject, text, null, checksum));
    }

    public void sendSimpleEmail(Event event, String recipient, String subject, TextTemplateGenerator textBuilder) {
        String text = textBuilder.generate();
        String checksum = calculateChecksum(recipient, null, subject, text);
        emailMessageRepository.insert(event.getId(), recipient, subject, text, null, checksum, ZonedDateTime.now(UTC));
        messages.offer(new EmailMessage(-1, event.getId(), WAITING.name(), recipient, subject, text, null, checksum));
    }

    public void sendSimpleEmail(Event event, String recipient, String subject, String text) {
        String checksum = calculateChecksum(recipient, null, subject, text);
        emailMessageRepository.insert(event.getId(), recipient, subject, text, null, checksum, ZonedDateTime.now(UTC));
        messages.offer(new EmailMessage(-1, event.getId(), WAITING.name(), recipient, subject, text, null, checksum));
    }

    void sendWaitingMessages() {
        Set<EmailMessage> toBeSent = messages.poll(configurationManager.getIntConfigValue(ConfigurationKeys.MAX_EMAIL_PER_CYCLE, 10));
        toBeSent.forEach(m -> {
            try {
                processMessage(m);
            } catch (Exception e) {
                log.warn("cannot send message: ",e);
            }
        });
    }

    void processNotSentEmail() {
        ZonedDateTime now = ZonedDateTime.now(UTC);
        tx.execute(status -> {
            String owner = UUID.randomUUID().toString();
            int updated = emailMessageRepository.updateStatusForRetry(now, now.minusMinutes(10), owner);
            log.debug("found {} expired messages", updated);
            if(updated > 0) {
                emailMessageRepository.loadForRetry(owner).forEach(messages::offer);
            }
            return null;
        });
    }

    private void processMessage(EmailMessage message) {
        int result = tx.execute(status -> emailMessageRepository.updateStatus(message.getEventId(), message.getChecksum(), IN_PROCESS.name(), Arrays.asList(WAITING.name(), RETRY.name())));
        if(result > 0) {
            tx.execute(status -> {
                sendMessage(message);
                return null;
            });
        } else {
            log.debug("no messages have been updated on DB for the following criteria: eventId: {}, checksum: {}", message.getEventId(), message.getChecksum());
        }
    }

    private void sendMessage(EmailMessage message) {
        EmailMessage storedMessage = emailMessageRepository.findByEventIdAndChecksum(message.getEventId(), message.getChecksum(), IN_PROCESS.name());
        mailer.send(message.getRecipient(), message.getSubject(), storedMessage.getMessage(), Optional.empty(), decodeAttachments(storedMessage.getAttachments()));
        emailMessageRepository.updateStatusToSent(storedMessage.getEventId(), storedMessage.getChecksum(), ZonedDateTime.now(UTC), Collections.singletonList(IN_PROCESS.name()));
    }

    private String encodeAttachments(Mailer.Attachment... files) {
        return gson.toJson(files);
    }

    private Mailer.Attachment[] decodeAttachments(String input) {
        if(StringUtils.isBlank(input)) {
            return new Mailer.Attachment[0];
        }
        return gson.fromJson(input, Mailer.Attachment[].class);
    }

    private static String calculateChecksum(String recipient, String attachments, String subject, String text)  {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(recipient.getBytes());
            digest.update(subject.getBytes());
            Optional.ofNullable(attachments).ifPresent(v -> digest.update(v.getBytes()));
            digest.update(text.getBytes());
            return new String(Hex.encode(digest.digest()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class AttachmentConverter implements JsonSerializer<Mailer.Attachment>, JsonDeserializer<Mailer.Attachment> {

        @Override
        public JsonElement serialize(Mailer.Attachment src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("filename", src.getFilename());
            obj.addProperty("source", Base64.getEncoder().encodeToString(src.getSource()));
            obj.addProperty("contentType", src.getContentType());
            return obj;
        }

        @Override
        public Mailer.Attachment deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            return new Mailer.Attachment(jsonObject.getAsJsonPrimitive("filename").getAsString(),
                    Base64.getDecoder().decode(jsonObject.getAsJsonPrimitive("source").getAsString()),
                    jsonObject.getAsJsonPrimitive("contentType").getAsString());
        }
    }
}
