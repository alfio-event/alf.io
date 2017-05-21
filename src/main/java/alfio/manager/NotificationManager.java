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

import alfio.controller.support.TemplateProcessor;
import alfio.manager.support.CustomMessageManager;
import alfio.manager.support.PDFTemplateGenerator;
import alfio.manager.support.PartialTicketTextGenerator;
import alfio.manager.support.TextTemplateGenerator;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.system.Mailer;
import alfio.model.*;
import alfio.model.Event;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.model.user.Organization;
import alfio.repository.EmailMessageRepository;
import alfio.repository.EventDescriptionRepository;
import alfio.repository.EventRepository;
import alfio.repository.TicketReservationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.Json;
import alfio.util.TemplateManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.*;
import com.ryantenney.passkit4j.Pass;
import com.ryantenney.passkit4j.PassResource;
import com.ryantenney.passkit4j.PassSerializer;
import com.ryantenney.passkit4j.model.*;
import com.ryantenney.passkit4j.model.Color;
import com.ryantenney.passkit4j.model.TextField;
import com.ryantenney.passkit4j.sign.PassSigner;
import com.ryantenney.passkit4j.sign.PassSignerImpl;
import com.ryantenney.passkit4j.sign.PassSigningException;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StreamUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateException;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final OrganizationRepository organizationRepository;
    private final ConfigurationManager configurationManager;
    private final Gson gson;
    private final Cache<String, Optional<byte[]>> passbookLogoCache = Caffeine.newBuilder()
        .maximumSize(20)
        .expireAfterWrite(20, TimeUnit.MINUTES)
        .build();

    private final EnumMap<Mailer.AttachmentIdentifier, Function<Map<String, String>, byte[]>> attachmentTransformer;

    @Autowired
    public NotificationManager(Mailer mailer,
                               MessageSource messageSource,
                               PlatformTransactionManager transactionManager,
                               EmailMessageRepository emailMessageRepository,
                               EventRepository eventRepository,
                               EventDescriptionRepository eventDescriptionRepository,
                               OrganizationRepository organizationRepository,
                               ConfigurationManager configurationManager,
                               FileUploadManager fileUploadManager,
                               TemplateManager templateManager,
                               TicketReservationRepository ticketReservationRepository) {
        this.messageSource = messageSource;
        this.mailer = mailer;
        this.emailMessageRepository = emailMessageRepository;
        this.eventRepository = eventRepository;
        this.organizationRepository = organizationRepository;
        this.tx = new TransactionTemplate(transactionManager);
        this.configurationManager = configurationManager;
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Mailer.Attachment.class, new AttachmentConverter());
        this.gson = builder.create();
        attachmentTransformer = new EnumMap<>(Mailer.AttachmentIdentifier.class);


        attachmentTransformer.put(Mailer.AttachmentIdentifier.CALENDAR_ICS, (model) -> {
            Event event;
            Locale locale;
            if(model.containsKey("eventId")) {
                //legacy branch, now we generate the ics as a reinterpreted ticket
                event = eventRepository.findById(Integer.valueOf(model.get("eventId"), 10));
                locale = Json.fromJson(model.get("locale"), Locale.class);
            } else {
                Ticket ticket = Json.fromJson(model.get("ticket"), Ticket.class);
                event = eventRepository.findById(ticket.getEventId());
                locale = Locale.forLanguageTag(ticket.getUserLanguage());
            }
            String description = eventDescriptionRepository.findDescriptionByEventIdTypeAndLocale(event.getId(), EventDescription.EventDescriptionType.DESCRIPTION, locale.getLanguage()).orElse("");
            return event.getIcal(description).orElse(null);
        });

        attachmentTransformer.put(Mailer.AttachmentIdentifier.RECEIPT_PDF, receiptOrInvoiceFactory(
            payload -> TemplateProcessor.buildReceiptPdf(payload.getLeft(),
                fileUploadManager,
                payload.getMiddle(),
                templateManager,
                payload.getRight())));

        attachmentTransformer.put(Mailer.AttachmentIdentifier.INVOICE_PDF, receiptOrInvoiceFactory(
            payload -> TemplateProcessor.buildInvoicePdf(payload.getLeft(),
                fileUploadManager,
                payload.getMiddle(),
                templateManager,
                payload.getRight())));

        attachmentTransformer.put(Mailer.AttachmentIdentifier.PASSBOOK, (model) -> {
            Ticket ticket = Json.fromJson(model.get("ticket"), Ticket.class);
            int eventId = ticket.getEventId();
            Event event = eventRepository.findById(eventId);
            Organization organization = organizationRepository.getById(Integer.valueOf(model.get("organizationId"), 10));
            int organizationId = organization.getId();

            Function<ConfigurationKeys, Configuration.ConfigurationPathKey> partial = Configuration.from(organizationId, eventId);
            Map<ConfigurationKeys, Optional<String>> pbookConf = configurationManager.getStringConfigValueFrom(
                partial.apply(ConfigurationKeys.PASSBOOK_TYPE_IDENTIFIER),
                partial.apply(ConfigurationKeys.PASSBOOK_KEYSTORE),
                partial.apply(ConfigurationKeys.PASSBOOK_KEYSTORE_PASSWORD),
                partial.apply(ConfigurationKeys.PASSBOOK_TEAM_IDENTIFIER));
            //check if all are set
            if(pbookConf.values().stream().anyMatch(o -> !o.isPresent())) {
                log.warn("Missing configuration keys, check if all 4 are presents");
                return null;
            }

            //
            String teamIdentifier = pbookConf.get(ConfigurationKeys.PASSBOOK_TEAM_IDENTIFIER).orElseThrow(IllegalStateException::new);
            String typeIdentifier = pbookConf.get(ConfigurationKeys.PASSBOOK_TYPE_IDENTIFIER).orElseThrow(IllegalStateException::new);
            byte[] keystoreRaw = Base64.getDecoder().decode(pbookConf.get(ConfigurationKeys.PASSBOOK_KEYSTORE).orElseThrow(IllegalStateException::new));
            String keystorePwd = pbookConf.get(ConfigurationKeys.PASSBOOK_KEYSTORE_PASSWORD).orElseThrow(IllegalStateException::new);

            //ugly, find an alternative way?
            Optional<KeyStore> ksJks = loadKeyStore(keystoreRaw, "jks");
            if(!ksJks.isPresent()) {
                log.warn("Not able to load keystore, check");
                return null;
            }
            KeyStore keyStore = ksJks.orElseThrow(IllegalStateException::new);

            // from example: https://github.com/ryantenney/passkit4j/blob/master/src/test/java/com/ryantenney/passkit4j/EventTicketExample.java

            Location loc = new Location(Double.parseDouble(event.getLatitude()), Double.parseDouble(event.getLongitude())).altitude(0.0);

            Pass pass = new Pass()
                .teamIdentifier(teamIdentifier)
                .passTypeIdentifier(typeIdentifier)
                .organizationName(organization.getName())
                .description(event.getDisplayName())
                .serialNumber(ticket.getUuid())
                .relevantDate(Date.from(event.getBegin().toInstant()))
                .expirationDate(Date.from(event.getEnd().toInstant()))
                .locations(loc)
                .barcode(new Barcode(BarcodeFormat.QR, ticket.ticketCode(event.getPrivateKey())))
                .foregroundColor(Color.WHITE)
                .backgroundColor(Color.WHITE)
                .passInformation(
                    new EventTicket()
                        .primaryFields(new TextField("event", "EVENT", event.getDisplayName()))
                        .secondaryFields(new TextField("loc", "LOCATION", event.getLocation()))
                );

            List<PassResource> passResources = new ArrayList<>(4);
            try(InputStream icon = new ClassPathResource("/alfio/icon/icon.png").getInputStream();
                InputStream icon2 = new ClassPathResource("/alfio/icon/icon@2x.png").getInputStream()) {
                passResources.add(new PassResource("icon.png", StreamUtils.copyToByteArray(icon)));
                passResources.add(new PassResource("icon@2x.png", StreamUtils.copyToByteArray(icon2)));
            } catch (IOException e) {
                //
            }


            fileUploadManager.findMetadata(event.getFileBlobId()).ifPresent(metadata -> {
                if(metadata.getContentType().equals("image/png") || metadata.getContentType().equals("image/jpeg")) {
                    Optional<byte[]> cachedLogo = passbookLogoCache.get(event.getFileBlobId(), (id) -> {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        fileUploadManager.outputFile(event.getFileBlobId(), baos);
                        return readAndConvertImage(baos);
                    });
                    cachedLogo.ifPresent(logo -> {
                        passResources.add(new PassResource("logo.png", logo));
                        passResources.add(new PassResource("logo@2x.png", logo));
                    });
                }
            });

            pass.files(passResources.toArray(new PassResource[passResources.size()]));

            try(ByteArrayOutputStream baos = new ByteArrayOutputStream();
                InputStream appleCert = new ClassPathResource("/alfio/certificates/AppleWWDRCA.cer").getInputStream()) {
                PassSigner signer = PassSignerImpl.builder()
                    .keystore(keyStore, keystorePwd)
                    .intermediateCertificate(appleCert)
                    .build();
                PassSerializer.writePkPassArchive(pass, signer, baos);
                //PassSerializer.writePkPassArchive(pass, signer, new FileOutputStream("/tmp/Passbook.pkpass"));
                return baos.toByteArray();
            } catch (IOException | PassSigningException e) {
                log.warn("was not able to generate the passbook file", e);
                return null;
            }
        });

        attachmentTransformer.put(Mailer.AttachmentIdentifier.TICKET_PDF, (model) -> {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Ticket ticket = Json.fromJson(model.get("ticket"), Ticket.class);
            try {
                TicketReservation reservation = ticketReservationRepository.findReservationById(ticket.getTicketsReservationId());
                TicketCategory ticketCategory = Json.fromJson(model.get("ticketCategory"), TicketCategory.class);
                Event event = eventRepository.findById(ticket.getEventId());
                Organization organization = organizationRepository.getById(Integer.valueOf(model.get("organizationId"), 10));
                PDFTemplateGenerator pdfTemplateGenerator = TemplateProcessor.buildPDFTicket(Locale.forLanguageTag(ticket.getUserLanguage()), event, reservation,
                    ticket, ticketCategory, organization, templateManager, fileUploadManager, configurationManager.getShortReservationID(event, ticket.getTicketsReservationId()));
                pdfTemplateGenerator.generate().createPDF(baos);
            } catch (IOException e) {
                log.warn("was not able to generate ticket pdf for ticket with id" + ticket.getId(), e);
            }
            return baos.toByteArray();
        });
    }

    //"jks"
    // -> "pkcs12" don't work ;(
    private static Optional<KeyStore> loadKeyStore(byte[] k, String type) {
        try {
            KeyStore ks = KeyStore.getInstance(type);
            ks.load(new ByteArrayInputStream(k), null);
            return Optional.of(ks);
        } catch (GeneralSecurityException | IOException e) {
            return Optional.empty();
        }
    }

    private static Optional<byte[]> readAndConvertImage(ByteArrayOutputStream baos) {
        try {
            BufferedImage sourceImage = ImageIO.read(new ByteArrayInputStream(baos.toByteArray()));
            boolean isWider = sourceImage.getWidth() > sourceImage.getHeight();
            // as defined in https://developer.apple.com/library/content/documentation/UserExperience/Conceptual/PassKit_PG/Creating.html#//apple_ref/doc/uid/TP40012195-CH4-SW8
            // logo max 160*50
            int finalWidth = isWider ? 160 : -1;
            int finalHeight = !isWider ? 50 : -1;
            Image thumb = sourceImage.getScaledInstance(finalWidth, finalHeight, Image.SCALE_SMOOTH);
            BufferedImage bufferedThumbnail = new BufferedImage(thumb.getWidth(null), thumb.getHeight(null), BufferedImage.TYPE_INT_RGB);
            bufferedThumbnail.getGraphics().drawImage(thumb, 0, 0, null);
            ByteArrayOutputStream logoPng = new ByteArrayOutputStream();
            ImageIO.write(bufferedThumbnail, "png", logoPng);
            return Optional.of(logoPng.toByteArray());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public Function<Map<String, String>, byte[]> receiptOrInvoiceFactory(Function<Triple<Event, Locale, Map<String, Object>>, Optional<byte[]>> pdfGenerator) {
        return (model) -> {
            String reservationId = model.get("reservationId");
            Event event = eventRepository.findById(Integer.valueOf(model.get("eventId"), 10));
            Locale language = Json.fromJson(model.get("language"), Locale.class);

            Map<String, Object> reservationEmailModel = Json.fromJson(model.get("reservationEmailModel"), new TypeReference<Map<String, Object>>() {});
            //FIXME hack: reservationEmailModel should be a minimal and typed container
            reservationEmailModel.put("event", event);
            Optional<byte[]> receipt = pdfGenerator.apply(Triple.of(event, language, reservationEmailModel));

            if(!receipt.isPresent()) {
                log.warn("was not able to generate the receipt for reservation id " + reservationId + " for locale " + language);
            }
            return receipt.orElse(null);
        };
    }

    public void sendTicketByEmail(Ticket ticket, Event event, Locale locale, PartialTicketTextGenerator textBuilder, TicketReservation reservation, TicketCategory ticketCategory) throws IOException {

        Organization organization = organizationRepository.getById(event.getOrganizationId());

        List<Mailer.Attachment> attachments = new ArrayList<>();
        attachments.add(CustomMessageManager.generateTicketAttachment(ticket, reservation, ticketCategory, organization));

        String encodedAttachments = encodeAttachments(attachments.toArray(new Mailer.Attachment[attachments.size()]));
        String subject = messageSource.getMessage("ticket-email-subject", new Object[]{event.getDisplayName()}, locale);
        String text = textBuilder.generate(ticket);
        String checksum = calculateChecksum(ticket.getEmail(), encodedAttachments, subject, text);
        String recipient = ticket.getEmail();
        //TODO handle HTML
        tx.execute(status -> emailMessageRepository.insert(event.getId(), recipient, null, subject, text, encodedAttachments, checksum, ZonedDateTime.now(UTC)));
    }

    public void sendSimpleEmail(Event event, String recipient, List<String> cc, String subject, TextTemplateGenerator textBuilder) {
        sendSimpleEmail(event, recipient, cc, subject, textBuilder, Collections.emptyList());
    }

    public void sendSimpleEmail(Event event, String recipient, String subject, TextTemplateGenerator textBuilder) {
        sendSimpleEmail(event, recipient, Collections.emptyList(), subject, textBuilder);
    }

    public void sendSimpleEmail(Event event, String recipient, String subject, TextTemplateGenerator textBuilder, List<Mailer.Attachment> attachments) {
        sendSimpleEmail(event, recipient, Collections.emptyList(), subject, textBuilder, attachments);
    }

    public void sendSimpleEmail(Event event, String recipient, List<String> cc, String subject, TextTemplateGenerator textBuilder, List<Mailer.Attachment> attachments) {

        String encodedAttachments = attachments.isEmpty() ? null : encodeAttachments(attachments.toArray(new Mailer.Attachment[attachments.size()]));
        String encodedCC = Json.toJson(cc);

        String text = textBuilder.generate();
        String checksum = calculateChecksum(recipient, encodedAttachments, subject, text);
        //in order to minimize the database size, it is worth checking if there is already another message in the table
        Optional<EmailMessage> existing = emailMessageRepository.findByEventIdAndChecksum(event.getId(), checksum);
        if(!existing.isPresent()) {
            emailMessageRepository.insert(event.getId(), recipient, encodedCC, subject, text, encodedAttachments, checksum, ZonedDateTime.now(UTC));
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
        mailer.send(event, message.getRecipient(), message.getCc(), message.getSubject(), message.getMessage(), Optional.empty(), decodeAttachments(message.getAttachments()));
        emailMessageRepository.updateStatusToSent(message.getEventId(), message.getChecksum(), ZonedDateTime.now(UTC), Collections.singletonList(IN_PROCESS.name()));
    }

    private String encodeAttachments(Mailer.Attachment... files) {
        return gson.toJson(files);
    }

    private Mailer.Attachment[] decodeAttachments(String input) {
        if(StringUtils.isBlank(input)) {
            return new Mailer.Attachment[0];
        }
        Mailer.Attachment[] attachments = gson.fromJson(input, Mailer.Attachment[].class);

        Set<Mailer.AttachmentIdentifier> alreadyPresents = Arrays.stream(attachments).map(Mailer.Attachment::getIdentifier).filter(Objects::nonNull).collect(Collectors.toSet());
        //
        List<Mailer.Attachment> toReinterpret = Arrays.stream(attachments)
            .filter(attachment -> attachment.getIdentifier() != null && !attachment.getIdentifier().reinterpretAs().isEmpty())
            .collect(Collectors.toList());

        List<Mailer.Attachment> generated = new ArrayList<>(Arrays.stream(attachments)
            .map(attachment -> this.transformAttachment(attachment, attachment.getIdentifier()))
            .filter(Objects::nonNull)
            .collect(Collectors.toList()));

        List<Mailer.Attachment> reinterpreted = new ArrayList<>();
        toReinterpret.forEach(attachment ->
            attachment.getIdentifier().reinterpretAs().stream()
                .filter(identifier -> !alreadyPresents.contains(identifier))
                .forEach(identifier -> reinterpreted.add(this.transformAttachment(attachment, identifier))
            )
        );

        generated.addAll(reinterpreted.stream().filter(Objects::nonNull).collect(Collectors.toList()));
        return generated.toArray(new Mailer.Attachment[generated.size()]);
    }

    private Mailer.Attachment transformAttachment(Mailer.Attachment attachment, Mailer.AttachmentIdentifier identifier) {
        if(identifier != null) {
            byte[] result = attachmentTransformer.get(identifier).apply(attachment.getModel());
            return result == null ? null : new Mailer.Attachment(identifier.fileName(attachment.getFilename()), result, identifier.contentType(attachment.getContentType()), null, null);
        } else {
            return attachment;
        }
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
            obj.addProperty("source", src.getSource() != null ? Base64.getEncoder().encodeToString(src.getSource()) : null);
            obj.addProperty("contentType", src.getContentType());
            obj.addProperty("identifier", src.getIdentifier() != null ? src.getIdentifier().name() : null);
            obj.addProperty("model", src.getModel() != null ? Json.toJson(src.getModel()) : null);
            return obj;
        }

        @Override
        public Mailer.Attachment deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            String filename = jsonObject.getAsJsonPrimitive("filename").getAsString();
            byte[] source =  jsonObject.has("source") ? Base64.getDecoder().decode(jsonObject.getAsJsonPrimitive("source").getAsString()) : null;
            String contentType = jsonObject.getAsJsonPrimitive("contentType").getAsString();
            Mailer.AttachmentIdentifier identifier =  jsonObject.has("identifier") ? Mailer.AttachmentIdentifier.valueOf(jsonObject.getAsJsonPrimitive("identifier").getAsString()) : null;
            Map<String, String> model = jsonObject.has("model")  ? Json.fromJson(jsonObject.getAsJsonPrimitive("model").getAsString(), new TypeReference<Map<String, String>>() {}) : null;
            return new Mailer.Attachment(filename, source, contentType, model, identifier);
        }
    }
}
