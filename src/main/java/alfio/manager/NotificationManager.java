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
import alfio.manager.i18n.MessageSourceManager;
import alfio.manager.support.CustomMessageManager;
import alfio.manager.support.PartialTicketTextGenerator;
import alfio.manager.support.TemplateGenerator;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.system.Mailer;
import alfio.model.*;
import alfio.model.system.ConfigurationKeys;
import alfio.model.user.Organization;
import alfio.repository.*;
import alfio.repository.user.OrganizationRepository;
import alfio.util.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.gson.*;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static alfio.model.EmailMessage.Status.*;
import static alfio.model.system.ConfigurationKeys.BASE_URL;

@Component
@Log4j2
public class NotificationManager {

    private final Mailer mailer;
    private final MessageSourceManager messageSourceManager;
    private final EmailMessageRepository emailMessageRepository;
    private final TransactionTemplate tx;
    private final EventRepository eventRepository;
    private final OrganizationRepository organizationRepository;
    private final ConfigurationManager configurationManager;
    private final Gson gson;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final ClockProvider clockProvider;

    private final EnumMap<Mailer.AttachmentIdentifier, Function<Map<String, String>, byte[]>> attachmentTransformer;

    @Autowired
    public NotificationManager(Mailer mailer,
                               MessageSourceManager messageSourceManager,
                               PlatformTransactionManager transactionManager,
                               EmailMessageRepository emailMessageRepository,
                               EventRepository eventRepository,
                               EventDescriptionRepository eventDescriptionRepository,
                               OrganizationRepository organizationRepository,
                               ConfigurationManager configurationManager,
                               FileUploadManager fileUploadManager,
                               TemplateManager templateManager,
                               TicketReservationRepository ticketReservationRepository,
                               TicketCategoryRepository ticketCategoryRepository,
                               PassKitManager passKitManager,
                               TicketRepository ticketRepository,
                               TicketFieldRepository ticketFieldRepository,
                               AdditionalServiceItemRepository additionalServiceItemRepository,
                               ExtensionManager extensionManager,
                               ClockProvider clockProvider) {
        this.messageSourceManager = messageSourceManager;
        this.mailer = mailer;
        this.emailMessageRepository = emailMessageRepository;
        this.eventRepository = eventRepository;
        this.organizationRepository = organizationRepository;
        this.ticketCategoryRepository = ticketCategoryRepository;
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_NESTED);
        this.tx = new TransactionTemplate(transactionManager, definition);
        this.configurationManager = configurationManager;
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Mailer.Attachment.class, new AttachmentConverter());
        this.gson = builder.create();
        this.clockProvider = clockProvider;
        attachmentTransformer = new EnumMap<>(Mailer.AttachmentIdentifier.class);
        attachmentTransformer.put(Mailer.AttachmentIdentifier.CALENDAR_ICS, generateICS(eventRepository, eventDescriptionRepository, ticketCategoryRepository, organizationRepository, messageSourceManager));
        attachmentTransformer.put(Mailer.AttachmentIdentifier.RECEIPT_PDF, receiptOrInvoiceFactory(eventRepository,
            payload -> TemplateProcessor.buildReceiptPdf(payload.getLeft(), fileUploadManager, payload.getMiddle(), templateManager, payload.getRight(), extensionManager)));
        attachmentTransformer.put(Mailer.AttachmentIdentifier.INVOICE_PDF, receiptOrInvoiceFactory(eventRepository,
            payload -> TemplateProcessor.buildInvoicePdf(payload.getLeft(), fileUploadManager, payload.getMiddle(), templateManager, payload.getRight(), extensionManager)));
        attachmentTransformer.put(Mailer.AttachmentIdentifier.CREDIT_NOTE_PDF, receiptOrInvoiceFactory(eventRepository,
            payload -> TemplateProcessor.buildCreditNotePdf(payload.getLeft(), fileUploadManager, payload.getMiddle(), templateManager, payload.getRight(), extensionManager)));
        attachmentTransformer.put(Mailer.AttachmentIdentifier.PASSBOOK, passKitManager::getPass);
        Function<Ticket, List<TicketFieldConfigurationDescriptionAndValue>> retrieveFieldValues = EventUtil.retrieveFieldValues(ticketRepository, ticketFieldRepository, additionalServiceItemRepository);
        attachmentTransformer.put(Mailer.AttachmentIdentifier.TICKET_PDF, generateTicketPDF(eventRepository, organizationRepository, configurationManager, fileUploadManager, templateManager, ticketReservationRepository, retrieveFieldValues, extensionManager));
    }

    private static Function<Map<String, String>, byte[]> generateTicketPDF(EventRepository eventRepository,
                                                                           OrganizationRepository organizationRepository,
                                                                           ConfigurationManager configurationManager,
                                                                           FileUploadManager fileUploadManager,
                                                                           TemplateManager templateManager,
                                                                           TicketReservationRepository ticketReservationRepository,
                                                                           Function<Ticket, List<TicketFieldConfigurationDescriptionAndValue>> retrieveFieldValues,
                                                                           ExtensionManager extensionManager) {
        return model -> {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Ticket ticket = Json.fromJson(model.get("ticket"), Ticket.class);
            try {
                TicketReservation reservation = ticketReservationRepository.findReservationById(ticket.getTicketsReservationId());
                TicketCategory ticketCategory = Json.fromJson(model.get("ticketCategory"), TicketCategory.class);
                Event event = eventRepository.findById(ticket.getEventId());
                Organization organization = organizationRepository.getById(Integer.valueOf(model.get("organizationId"), 10));
                TemplateProcessor.renderPDFTicket(LocaleUtil.forLanguageTag(ticket.getUserLanguage()), event, reservation,
                    ticket, ticketCategory, organization, templateManager, fileUploadManager,
                    configurationManager.getShortReservationID(event, reservation), baos, retrieveFieldValues, extensionManager);
            } catch (IOException e) {
                log.warn("was not able to generate ticket pdf for ticket with id" + ticket.getId(), e);
            }
            return baos.toByteArray();
        };
    }

    private static Function<Map<String, String>, byte[]> generateICS(EventRepository eventRepository, EventDescriptionRepository eventDescriptionRepository, 
    		TicketCategoryRepository ticketCategoryRepository, OrganizationRepository organizationRepository, MessageSourceManager messageSourceManager) {
    	
        return model -> {
            Event event;
            Locale locale;
            Integer categoryId;
            if(model.containsKey("eventId")) {
                //legacy branch, now we generate the ics as a reinterpreted ticket
                event = eventRepository.findById(Integer.valueOf(model.get("eventId"), 10));
                locale = Json.fromJson(model.get("locale"), Locale.class);
                categoryId = null;
            } else {
                Ticket ticket = Json.fromJson(model.get("ticket"), Ticket.class);
                event = eventRepository.findById(ticket.getEventId());
                locale = LocaleUtil.forLanguageTag(ticket.getUserLanguage());
                categoryId = ticket.getCategoryId();
            }
            Organization organization = organizationRepository.getById(event.getOrganizationId());
            TicketCategory category = Optional.ofNullable(categoryId).map(ticketCategoryRepository::getById).orElse(null);
            String description = eventDescriptionRepository.findDescriptionByEventIdTypeAndLocale(event.getId(), EventDescription.EventDescriptionType.DESCRIPTION, locale.getLanguage()).orElse("");
            if(model.containsKey("onlineCheckInUrl")) { // special case: online event
                var messageSource = messageSourceManager.getMessageSourceForEvent(event);
                description = description + buildOnlineCheckInInformation(messageSource).apply(model, locale);
            }
            return EventUtil.getIcalForEvent(event, category, description, organization).orElse(null);
        };
    }

    private static BiFunction<Map<String,String>, Locale, String> buildOnlineCheckInInformation(MessageSource messageSource) {
        return (model, locale) -> "\n\n******************************************\n" +
            messageSource.getMessage("email.event.online.important-information", null, locale) + "\n\n" +
            messageSource.getMessage("event.location.online", null, locale) + "\n" +
            messageSource.getMessage("email.event.online.check-in", null, locale) + "\n" +
            model.get("onlineCheckInUrl") + "\n\n" +
            MustacheCustomTag.renderToTextCommonmark(model.getOrDefault("prerequisites", ""));
    }

    public String buildOnlineCheckInText(Map<String, String> model, Locale locale, MessageSource messageSource) {
        return buildOnlineCheckInInformation(messageSource).apply(model, locale);
    }

    private static Function<Map<String, String>, byte[]> receiptOrInvoiceFactory(EventRepository eventRepository, Function<Triple<Event, Locale, Map<String, Object>>, Optional<byte[]>> pdfGenerator) {
        return model -> {
            String reservationId = model.get("reservationId");
            Event event = eventRepository.findById(Integer.valueOf(model.get("eventId"), 10));
            Locale language = Json.fromJson(model.get("language"), Locale.class);

            Map<String, Object> reservationEmailModel = Json.fromJson(model.get("reservationEmailModel"), new TypeReference<>() {
            });
            //FIXME hack: reservationEmailModel should be a minimal and typed container
            reservationEmailModel.put("event", event);
            Optional<byte[]> receipt = pdfGenerator.apply(Triple.of(event, language, reservationEmailModel));

            if(receipt.isEmpty()) {
                log.warn("was not able to generate the receipt for reservation id " + reservationId + " for locale " + language);
            }
            return receipt.orElse(null);
        };
    }

    public void sendTicketByEmail(Ticket ticket,
                                  Event event,
                                  Locale locale,
                                  PartialTicketTextGenerator textBuilder,
                                  TicketReservation reservation,
                                  TicketCategory ticketCategory) {

        Organization organization = organizationRepository.getById(event.getOrganizationId());

        List<Mailer.Attachment> attachments = new ArrayList<>();
        if(event.getFormat() == Event.EventFormat.ONLINE) { // generate only calendar invitation
            var baseUrl = configurationManager.getFor(BASE_URL, ConfigurationLevel.event(event)).getRequiredValue();
            var alfioMetadata = eventRepository.getMetadataForEvent(event.getId());
            var eventMetadata = Optional.ofNullable(alfioMetadata.getRequirementsDescriptions()).flatMap(m -> Optional.ofNullable(m.get(locale.getLanguage())));
            var categoryMetadata = Optional.ofNullable(ticketCategoryRepository.getMetadata(event.getId(), ticketCategory.getId()).getRequirementsDescriptions()).flatMap(m -> Optional.ofNullable(m.get(locale.getLanguage())));
            if (alfioMetadata == null
                || alfioMetadata.getAttributes() == null
                || !alfioMetadata.getAttributes().containsKey(Event.EventOccurrence.CARNET.toString())) {
                //attachment not needed for CARNET events
                attachments.add(CustomMessageManager.generateCalendarAttachmentForOnlineEvent(ticket,
                    reservation, ticketCategory, organization, TicketReservationManager.ticketOnlineCheckInUrl(event, ticket, baseUrl),
                    categoryMetadata.or(() -> eventMetadata).orElse("")));
            }
        } else {
            attachments.add(CustomMessageManager.generateTicketAttachment(ticket, reservation, ticketCategory, organization));
        }

        String displayName = event.getDisplayName();

        String encodedAttachments = encodeAttachments(attachments.toArray(new Mailer.Attachment[0]));
        String subject = messageSourceManager.getMessageSourceForEvent(event).getMessage("ticket-email-subject", new Object[]{displayName}, locale);
        var renderedTemplate = textBuilder.generate(ticket);
        String checksum = calculateChecksum(ticket.getEmail(), encodedAttachments, subject, renderedTemplate);
        String recipient = ticket.getEmail();
        
        tx.execute(status -> {
            emailMessageRepository.findIdByEventIdAndChecksum(event.getId(), checksum).ifPresentOrElse(
                // see issue #967
                id -> emailMessageRepository.updateStatusToWaitingWithHtml(event.getId(), id, renderedTemplate.getHtmlPart()),
                () -> emailMessageRepository.insert(event.getId(), reservation.getId(), recipient, null, subject, renderedTemplate.getTextPart(), renderedTemplate.getHtmlPart(), encodedAttachments, checksum, ZonedDateTime.now(clockProvider.getClock()))
            );
            return null;
        });
    }

    public void sendSimpleEmail(EventAndOrganizationId event, String reservationId, String recipient, List<String> cc, String subject, TemplateGenerator textBuilder) {
        sendSimpleEmail(event, reservationId, recipient, cc, subject, textBuilder, Collections.emptyList());
    }

    public List<String> getCCForEventOrganizer(EventAndOrganizationId event) {
        var systemNotificationCC = configurationManager.getFor(ConfigurationKeys.MAIL_SYSTEM_NOTIFICATION_CC, ConfigurationLevel.event(event)).getValueOrDefault("");
        return Stream.of(StringUtils.split(systemNotificationCC, ','))
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.toList());
    }

    public void sendSimpleEmail(EventAndOrganizationId event, String reservationId, String recipient, String subject, TemplateGenerator textBuilder) {
        sendSimpleEmail(event, reservationId, recipient, Collections.emptyList(), subject, textBuilder);
    }

    public void sendSimpleEmail(EventAndOrganizationId event, String reservationId, String recipient, String subject, TemplateGenerator textBuilder, List<Mailer.Attachment> attachments) {
        sendSimpleEmail(event, reservationId, recipient, Collections.emptyList(), subject, textBuilder, attachments);
    }

    public void sendSimpleEmail(EventAndOrganizationId event, String reservationId, String recipient, List<String> cc, String subject, TemplateGenerator textBuilder, List<Mailer.Attachment> attachments) {

        String encodedAttachments = attachments.isEmpty() ? null : encodeAttachments(attachments.toArray(new Mailer.Attachment[0]));
        String encodedCC = Json.toJson(cc);

        var renderedTemplate = textBuilder.generate();
        String checksum = calculateChecksum(recipient, encodedAttachments, subject, renderedTemplate);
        //in order to minimize the database size, it is worth checking if there is already another message in the table
        Optional<Integer> existing = emailMessageRepository.findIdByEventIdAndChecksum(event.getId(), checksum);

        existing.ifPresentOrElse(id ->
            //see issue #967
            emailMessageRepository.updateStatusToWaitingWithHtml(event.getId(), id, renderedTemplate.getHtmlPart())
            ,
            () -> emailMessageRepository.insert(event.getId(), reservationId, recipient, encodedCC, subject, renderedTemplate.getTextPart(), renderedTemplate.getHtmlPart(), encodedAttachments, checksum, ZonedDateTime.now(clockProvider.getClock())));
    }

    public Pair<Integer, List<LightweightMailMessage>> loadAllMessagesForEvent(int eventId, Integer page, String search) {
        final int pageSize = 50;
        int offset = page == null ? 0 : page * pageSize;
        String toSearch = StringUtils.trimToNull(search);
        toSearch = toSearch == null ? null : ("%" + toSearch + "%");
        return Pair.of(emailMessageRepository.countFindByEventId(eventId, toSearch), emailMessageRepository.findByEventId(eventId, offset, pageSize, toSearch));
    }

    public List<LightweightMailMessage> loadAllMessagesForReservationId(int eventId, String reservationId) {
        return emailMessageRepository.findByEventIdAndReservationId(eventId, reservationId);
    }

    public Optional<EmailMessage> loadSingleMessageForEvent(int eventId, int messageId) {
        return emailMessageRepository.findByEventIdAndMessageId(eventId, messageId);
    }

    @Transactional
    public int sendWaitingMessages() {
        Date now = new Date();

        emailMessageRepository.setToRetryOldInProcess(DateUtils.addHours(now, -1));

        AtomicInteger counter = new AtomicInteger();

        eventRepository.findAllActiveIds(ZonedDateTime.now(clockProvider.getClock()))
            .stream()
            .flatMap(id -> emailMessageRepository.loadIdsWaitingForProcessing(id, now).stream())
            .distinct()
            .forEach(messageId -> counter.addAndGet(processMessage(messageId)));

        //processing promo codes
        emailMessageRepository.loadIdsPromoCodesForProcessing(now)
            .forEach(messageId -> counter.addAndGet(processMessage(messageId)));

        return counter.get();
    }

    private int processMessage(int messageId) {
        EmailMessage message = emailMessageRepository.findById(messageId);
        EventAndOrganizationId event = eventRepository.findEventAndOrganizationIdById(message.getEventId());
        if(message.getAttempts() >= configurationManager.getFor(ConfigurationKeys.MAIL_ATTEMPTS_COUNT, ConfigurationLevel.event(event)).getValueAsIntOrDefault(10)) {
            tx.execute(status -> emailMessageRepository.updateStatusAndAttempts(messageId, ERROR.name(), message.getAttempts(), Arrays.asList(IN_PROCESS.name(), WAITING.name(), RETRY.name())));
            log.warn("Message with id " + messageId + " will be discarded");
            return 0;
        }


        try {
            int result = Optional.ofNullable(tx.execute(status -> emailMessageRepository.updateStatus(messageId, message.getEventId(), message.getChecksum(), IN_PROCESS.name(), Arrays.asList(WAITING.name(), RETRY.name(), PROMO_CODE.name())))).orElse(0);
            if(result > 0) {
                return Optional.ofNullable(tx.execute(status -> {
                    sendMessage(event, message);
                    return 1;
                })).orElse(0);
            } else {
                log.debug("no messages have been updated on DB for the following criteria: eventId: {}, checksum: {}", message.getEventId(), message.getChecksum());
            }
        } catch(Exception e) {
            tx.execute(status -> emailMessageRepository.updateStatusAndAttempts(message.getId(), RETRY.name(), DateUtils.addMinutes(new Date(), message.getAttempts() + 1), message.getAttempts() + 1, Arrays.asList(IN_PROCESS.name(), WAITING.name(), RETRY.name(), PROMO_CODE.name())));
            log.warn("could not send message: ",e);
        }
        return 0;
    }

    private void sendMessage(EventAndOrganizationId event, EmailMessage message) {
        String displayName = eventRepository.getDisplayNameById(message.getEventId());
        mailer.send(event, message.getStatus() == PROMO_CODE ? "Info" : displayName, message.getRecipient(), message.getCc(), message.getSubject(), message.getMessage(), Optional.ofNullable(message.getHtmlMessage()), decodeAttachments(message.getAttachments()));
        emailMessageRepository.updateStatusToSent(message.getEventId(), message.getChecksum(), ZonedDateTime.now(clockProvider.getClock()), Collections.singletonList(IN_PROCESS.name()));
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

        List<Mailer.Attachment> generated = Arrays.stream(attachments)
            .map(attachment -> this.transformAttachment(attachment, attachment.getIdentifier()))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        List<Mailer.Attachment> reinterpreted = new ArrayList<>();
        toReinterpret.forEach(attachment ->
            attachment.getIdentifier().reinterpretAs().stream()
                .filter(identifier -> !alreadyPresents.contains(identifier))
                .forEach(identifier -> reinterpreted.add(this.transformAttachment(attachment, identifier))
            )
        );

        generated.addAll(reinterpreted.stream().filter(Objects::nonNull).collect(Collectors.toList()));
        return generated.toArray(new Mailer.Attachment[0]);
    }

    private Mailer.Attachment transformAttachment(Mailer.Attachment attachment, Mailer.AttachmentIdentifier identifier) {
        if(identifier != null) {
            byte[] result = attachmentTransformer.get(identifier).apply(attachment.getModel());
            return result == null ? null : new Mailer.Attachment(identifier.fileName(attachment.getFilename()), result, identifier.contentType(attachment.getContentType()), null, null);
        } else {
            return attachment;
        }
    }

    private static String calculateChecksum(String recipient, String attachments, String subject, RenderedTemplate renderedTemplate)  {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(recipient.getBytes(StandardCharsets.UTF_8));
            digest.update(subject.getBytes(StandardCharsets.UTF_8));
            Optional.ofNullable(attachments).ifPresent(v -> digest.update(v.getBytes(StandardCharsets.UTF_8)));
            digest.update(renderedTemplate.getTextPart().getBytes(StandardCharsets.UTF_8));
            if(renderedTemplate.isMultipart()) {
                digest.update(renderedTemplate.getHtmlPart().getBytes(StandardCharsets.UTF_8));
            }
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
        public Mailer.Attachment deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
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
