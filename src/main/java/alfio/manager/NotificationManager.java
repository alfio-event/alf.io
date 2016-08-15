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

import alfio.manager.support.PartialTicketPDFGenerator;
import alfio.manager.support.PartialTicketTextGenerator;
import alfio.manager.support.TextTemplateGenerator;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.system.Mailer;
import alfio.model.*;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.model.user.Organization;
import alfio.repository.EmailMessageRepository;
import alfio.repository.EventDescriptionRepository;
import alfio.repository.EventRepository;
import alfio.repository.user.OrganizationRepository;
import com.google.gson.*;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
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
    private final EventRepository eventRepository;
    private final EventDescriptionRepository eventDescriptionRepository;
    private final OrganizationRepository organizationRepository;
    private final ConfigurationManager configurationManager;
    private final Gson gson;

    @Autowired
    public NotificationManager(Mailer mailer,
                               MessageSource messageSource,
                               PlatformTransactionManager transactionManager,
                               EmailMessageRepository emailMessageRepository,
                               EventRepository eventRepository,
                               EventDescriptionRepository eventDescriptionRepository,
                               OrganizationRepository organizationRepository,
                               ConfigurationManager configurationManager) {
        this.messageSource = messageSource;
        this.mailer = mailer;
        this.emailMessageRepository = emailMessageRepository;
        this.eventRepository = eventRepository;
        this.eventDescriptionRepository = eventDescriptionRepository;
        this.organizationRepository = organizationRepository;
        this.tx = new TransactionTemplate(transactionManager);
        this.configurationManager = configurationManager;
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Mailer.Attachment.class, new AttachmentConverter());
        this.gson = builder.create();
    }

    public void sendTicketByEmail(Ticket ticket, Event event, Locale locale, PartialTicketTextGenerator textBuilder, PartialTicketPDFGenerator ticketBuilder) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ticketBuilder.generate(ticket).createPDF(baos);

        List<Mailer.Attachment> attachments = new ArrayList<>();
        attachments.add(new Mailer.Attachment("ticket-" + ticket.getUuid() + ".pdf", baos.toByteArray(), "application/pdf"));

        String description = eventDescriptionRepository.findDescriptionByEventIdTypeAndLocale(event.getId(), EventDescription.EventDescriptionType.DESCRIPTION, locale.getLanguage()).orElse("");

        Organization organization = organizationRepository.getById(event.getOrganizationId());
        event.getIcal(description, organization.getName(), organization.getEmail()).map(ics -> new Mailer.Attachment("calendar.ics", ics, "text/calendar")).ifPresent(attachments::add);


        String encodedAttachments = encodeAttachments(attachments.toArray(new Mailer.Attachment[attachments.size()]));
        String subject = messageSource.getMessage("ticket-email-subject", new Object[]{event.getDisplayName()}, locale);
        String text = textBuilder.generate(ticket);
        String checksum = calculateChecksum(ticket.getEmail(), encodedAttachments, subject, text);
        String recipient = ticket.getEmail();
        //TODO handle HTML
        tx.execute(status -> emailMessageRepository.insert(event.getId(), recipient, subject, text, encodedAttachments, checksum, ZonedDateTime.now(UTC)));
    }

    public void sendSimpleEmail(Event event, String recipient, String subject, TextTemplateGenerator textBuilder) {
        sendSimpleEmail(event, recipient, subject, textBuilder, Collections.emptyList());
    }

    public void sendSimpleEmail(Event event, String recipient, String subject, TextTemplateGenerator textBuilder, List<Mailer.Attachment> attachments) {

        String encodedAttachments = attachments.isEmpty() ? null : encodeAttachments(attachments.toArray(new Mailer.Attachment[attachments.size()]));

        String text = textBuilder.generate();
        String checksum = calculateChecksum(recipient, encodedAttachments, subject, text);
        //in order to minimize the database size, it is worth checking if there is already another message in the table
        Optional<EmailMessage> existing = emailMessageRepository.findByEventIdAndChecksum(event.getId(), checksum);
        if(!existing.isPresent()) {
            emailMessageRepository.insert(event.getId(), recipient, subject, text, encodedAttachments, checksum, ZonedDateTime.now(UTC));
        } else {
            emailMessageRepository.updateStatus(event.getId(), WAITING.name(), existing.get().getId());
        }
    }

    public List<LightweightMailMessage> loadAllMessagesForEvent(int eventId) {
        return emailMessageRepository.findByEventId(eventId);
    }

    public Optional<EmailMessage> loadSingleMessageForEvent(int eventId, int messageId) {
        return emailMessageRepository.findByEventIdAndMessageId(eventId, messageId);
    }

    void sendWaitingMessages() {
        Date now = new Date();
        eventRepository.findAllActiveIds(ZonedDateTime.now(UTC))
            .stream()
            .flatMap(id -> emailMessageRepository.loadIdsWaitingForProcessing(id, now).stream())
            .distinct()
            .forEach(this::processMessage);
    }

    private void processMessage(int messageId) {
        EmailMessage message = emailMessageRepository.findById(messageId);
        int eventId = message.getEventId();
        int organizationId = eventRepository.findOrganizationIdByEventId(eventId);
        if(message.getAttempts() >= configurationManager.getIntConfigValue(Configuration.from(organizationId, eventId, ConfigurationKeys.MAIL_ATTEMPTS_COUNT), 10)) {
            tx.execute(status -> emailMessageRepository.updateStatusAndAttempts(messageId, ERROR.name(), message.getAttempts(), Arrays.asList(IN_PROCESS.name(), WAITING.name(), RETRY.name())));
            log.warn("Message with id " + messageId + " will be discarded");
            return;
        }


        try {
            int result = tx.execute(status -> emailMessageRepository.updateStatus(message.getEventId(), message.getChecksum(), IN_PROCESS.name(), Arrays.asList(WAITING.name(), RETRY.name())));
            if(result > 0) {
                tx.execute(status -> {
                    sendMessage(message);
                    return null;
                });
            } else {
                log.debug("no messages have been updated on DB for the following criteria: eventId: {}, checksum: {}", message.getEventId(), message.getChecksum());
            }
        } catch(Exception e) {
            tx.execute(status -> emailMessageRepository.updateStatusAndAttempts(message.getId(), RETRY.name(), DateUtils.addMinutes(new Date(), message.getAttempts() + 1), message.getAttempts() + 1, Arrays.asList(IN_PROCESS.name(), WAITING.name(), RETRY.name())));
            log.warn("could not send message: ",e);
        }
    }

    private void sendMessage(EmailMessage message) {
        Event event = eventRepository.findById(message.getEventId());
        mailer.send(event, message.getRecipient(), message.getSubject(), message.getMessage(), Optional.empty(), decodeAttachments(message.getAttachments()));
        emailMessageRepository.updateStatusToSent(message.getEventId(), message.getChecksum(), ZonedDateTime.now(UTC), Collections.singletonList(IN_PROCESS.name()));
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
            digest.update(recipient.getBytes(StandardCharsets.UTF_8));
            digest.update(subject.getBytes(StandardCharsets.UTF_8));
            Optional.ofNullable(attachments).ifPresent(v -> digest.update(v.getBytes(StandardCharsets.UTF_8)));
            digest.update(text.getBytes(StandardCharsets.UTF_8));
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
