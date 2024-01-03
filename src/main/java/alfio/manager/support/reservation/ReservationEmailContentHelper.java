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
package alfio.manager.support.reservation;

import alfio.controller.support.TemplateProcessor;
import alfio.manager.BillingDocumentManager;
import alfio.manager.ExtensionManager;
import alfio.manager.NotificationManager;
import alfio.manager.i18n.MessageSourceManager;
import alfio.manager.support.ConfirmationEmailConfiguration;
import alfio.manager.support.IncompatibleStateException;
import alfio.manager.support.PartialTicketTextGenerator;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.system.Mailer;
import alfio.model.*;
import alfio.model.extension.CustomEmailText;
import alfio.model.metadata.SubscriptionMetadata;
import alfio.model.subscription.Subscription;
import alfio.model.subscription.UsageDetails;
import alfio.model.system.ConfigurationKeys;
import alfio.model.user.Organization;
import alfio.repository.*;
import alfio.repository.user.OrganizationRepository;
import alfio.util.*;
import alfio.util.checkin.TicketCheckInUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

import static alfio.model.BillingDocument.Type.INVOICE;
import static alfio.model.BillingDocument.Type.RECEIPT;
import static alfio.model.system.ConfigurationKeys.*;
import static alfio.util.ReservationUtil.reservationUrl;
import static alfio.util.TemplateManager.METADATA_ATTRIBUTES_KEY;
import static java.util.stream.Collectors.*;
import static org.springframework.http.MediaType.APPLICATION_PDF;

@Component
public class ReservationEmailContentHelper {

    private static final String RESERVATION_ID = "reservationId";
    private final ConfigurationManager configurationManager;
    private final NotificationManager notificationManager;
    private final SubscriptionRepository subscriptionRepository;
    private final MessageSourceManager messageSourceManager;
    private final OrderSummaryGenerator orderSummaryGenerator;
    private final TicketReservationRepository ticketReservationRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final PurchaseContextFieldRepository purchaseContextFieldRepository;
    private final OrganizationRepository organizationRepository;
    private final TicketRepository ticketRepository;
    private final TemplateManager templateManager;
    private final BillingDocumentManager billingDocumentManager;
    private final ExtensionManager extensionManager;
    private final EventRepository eventRepository;


    public ReservationEmailContentHelper(ConfigurationManager configurationManager,
                                         NotificationManager notificationManager,
                                         SubscriptionRepository subscriptionRepository,
                                         MessageSourceManager messageSourceManager,
                                         OrderSummaryGenerator orderSummaryGenerator,
                                         TicketReservationRepository ticketReservationRepository,
                                         TicketCategoryRepository ticketCategoryRepository,
                                         PurchaseContextFieldRepository purchaseContextFieldRepository,
                                         OrganizationRepository organizationRepository,
                                         TicketRepository ticketRepository,
                                         TemplateManager templateManager,
                                         BillingDocumentManager billingDocumentManager,
                                         ExtensionManager extensionManager,
                                         EventRepository eventRepository) {
        this.configurationManager = configurationManager;
        this.notificationManager = notificationManager;
        this.subscriptionRepository = subscriptionRepository;
        this.messageSourceManager = messageSourceManager;
        this.orderSummaryGenerator = orderSummaryGenerator;
        this.ticketReservationRepository = ticketReservationRepository;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.purchaseContextFieldRepository = purchaseContextFieldRepository;
        this.organizationRepository = organizationRepository;
        this.ticketRepository = ticketRepository;
        this.templateManager = templateManager;
        this.billingDocumentManager = billingDocumentManager;
        this.extensionManager = extensionManager;
        this.eventRepository = eventRepository;
    }


    public void sendConfirmationEmail(PurchaseContext purchaseContext, TicketReservation ticketReservation, Locale language, String username) {
        String reservationId = ticketReservation.getId();
        checkIfFinalized(reservationId);
        OrderSummary summary = orderSummaryGenerator.orderSummaryForReservationId(reservationId, purchaseContext);

        List<Mailer.Attachment> attachments;
        if (configurationManager.canAttachBillingDocumentToConfirmationEmail(purchaseContext)) { // https://github.com/alfio-event/alf.io/issues/573
            attachments = generateAttachmentForConfirmationEmail(purchaseContext, ticketReservation, language, summary, username);
        } else{
            attachments = List.of();
        }
        var vat = getVAT(purchaseContext);

        List<ConfirmationEmailConfiguration> configurations = new ArrayList<>();
        if(purchaseContext.ofType(PurchaseContext.PurchaseContextType.subscription)) {
            var firstSubscription = subscriptionRepository.findSubscriptionsByReservationId(reservationId).stream().findFirst().orElseThrow();
            boolean sendSeparateEmailToOwner = !Objects.equals(firstSubscription.getEmail(), ticketReservation.getEmail());
            var metadata = Objects.requireNonNullElseGet(subscriptionRepository.getSubscriptionMetadata(firstSubscription.getId()), SubscriptionMetadata::empty);
            Map<String, Object> initialModel = Map.of(
                "pin", firstSubscription.getPin(),
                "subscriptionId", firstSubscription.getId(),
                "includePin", metadata.getConfiguration().isDisplayPin(),
                "fullName", firstSubscription.getFirstName() + " " + firstSubscription.getLastName(),
                METADATA_ATTRIBUTES_KEY, metadata.getProperties());
            var model = prepareModelForReservationEmail(purchaseContext, ticketReservation, vat, summary, List.of(), initialModel);
            var subscriptionAttachments = new ArrayList<>(attachments);
            var subscriptionAttachment = generateSubscriptionAttachment(firstSubscription);
            subscriptionAttachments.add(subscriptionAttachment);
            configurations.add(new ConfirmationEmailConfiguration(TemplateResource.CONFIRMATION_EMAIL_SUBSCRIPTION, firstSubscription.getEmail(), model, sendSeparateEmailToOwner ? List.of(subscriptionAttachment) : subscriptionAttachments));
            if(sendSeparateEmailToOwner) {
                var separateModel = new HashMap<>(model);
                separateModel.put("includePin", false);
                separateModel.put("fullName", ticketReservation.getFullName());
                configurations.add(new ConfirmationEmailConfiguration(TemplateResource.CONFIRMATION_EMAIL_SUBSCRIPTION, ticketReservation.getEmail(), separateModel, subscriptionAttachments));
            }
        } else {
            var model = prepareModelForReservationEmail(purchaseContext, ticketReservation, vat, summary, ticketRepository.findTicketsInReservation(ticketReservation.getId()), Map.of());
            configurations.add(new ConfirmationEmailConfiguration(TemplateResource.CONFIRMATION_EMAIL, ticketReservation.getEmail(), model, attachments));
        }

        var messageSource = messageSourceManager.getMessageSourceFor(purchaseContext);
        var localizedType = messageSource.getMessage("purchase-context."+purchaseContext.getType(), null, language);
        configurations.forEach(configuration -> {
            notificationManager.sendSimpleEmail(purchaseContext, ticketReservation.getId(), configuration.getEmailAddress(), messageSource.getMessage("reservation-email-subject",
                    new Object[]{ configurationManager.getShortReservationID(purchaseContext, ticketReservation), purchaseContext.getTitle().get(language.getLanguage()), localizedType}, language),
                () -> templateManager.renderTemplate(purchaseContext, configuration.getTemplateResource(), configuration.getModel(), language),
                configuration.getAttachments());
        });
    }

    private Mailer.Attachment generateSubscriptionAttachment(Subscription subscription) {
        var model = new HashMap<String, String>();
        model.put("subscriptionId", subscription.getId().toString());
        return new Mailer.Attachment("subscription_" + subscription.getId() + ".pdf", null, APPLICATION_PDF.toString(), model, Mailer.AttachmentIdentifier.SUBSCRIPTION_PDF);
    }

    private List<Mailer.Attachment> generateAttachmentForConfirmationEmail(PurchaseContext purchaseContext,
                                                                           TicketReservation ticketReservation,
                                                                           Locale language,
                                                                           OrderSummary summary,
                                                                           String username) {
        if(mustGenerateBillingDocument(summary, ticketReservation)) { //#459 - include PDF invoice in reservation email
            BillingDocument.Type type = ticketReservation.getHasInvoiceNumber() ? INVOICE : RECEIPT;
            return billingDocumentManager.generateBillingDocumentAttachment(purchaseContext, ticketReservation, language, type, username, summary);
        }
        return List.of();
    }

    public void sendReservationCompleteEmailToOrganizer(PurchaseContext purchaseContext, TicketReservation ticketReservation, Locale language, String username) {
        String reservationId = ticketReservation.getId();

        checkIfFinalized(reservationId);

        Organization organization = organizationRepository.getById(purchaseContext.getOrganizationId());
        List<String> cc = notificationManager.getCCForEventOrganizer(purchaseContext);

        Map<String, Object> reservationEmailModel = prepareModelForReservationEmail(purchaseContext, ticketReservation);

        OrderSummary summary = orderSummaryGenerator.orderSummaryForReservationId(reservationId, purchaseContext);

        List<Mailer.Attachment> attachments = Collections.emptyList();

        if (!configurationManager.canGenerateReceiptOrInvoiceToCustomer(purchaseContext) || configurationManager.isInvoiceOnly(purchaseContext)) { // https://github.com/alfio-event/alf.io/issues/573
            attachments = generateAttachmentForConfirmationEmail(purchaseContext, ticketReservation, language, summary, username);
        }


        String shortReservationID = configurationManager.getShortReservationID(purchaseContext, ticketReservation);
        notificationManager.sendSimpleEmail(purchaseContext, null, organization.getEmail(), cc, "Reservation complete " + shortReservationID,
            () -> templateManager.renderTemplate(purchaseContext, TemplateResource.CONFIRMATION_EMAIL_FOR_ORGANIZER, reservationEmailModel, language),
            attachments);
    }

    private static boolean mustGenerateBillingDocument(OrderSummary summary, TicketReservation ticketReservation) {
        return !summary.getFree() && (!summary.getNotYetPaid() || (summary.getWaitingForPayment() && ticketReservation.isInvoiceRequested()));
    }

    public List<Mailer.Attachment> generateBillingDocumentAttachment(PurchaseContext purchaseContext,
                                                                      TicketReservation ticketReservation,
                                                                      Locale language,
                                                                      Map<String, Object> billingDocumentModel,
                                                                      BillingDocument.Type documentType) {
        Map<String, String> model = new HashMap<>();
        model.put(RESERVATION_ID, ticketReservation.getId());
        purchaseContext.event().ifPresent(event -> model.put("eventId", Integer.toString(event.getId())));
        model.put("language", Json.toJson(language));
        model.put("reservationEmailModel", Json.toJson(billingDocumentModel));//ticketReservation.getHasInvoiceNumber()
        switch (documentType) {
            case INVOICE:
                return Collections.singletonList(new Mailer.Attachment("invoice.pdf", null, APPLICATION_PDF.getType(), model, Mailer.AttachmentIdentifier.INVOICE_PDF));
            case RECEIPT:
                return Collections.singletonList(new Mailer.Attachment("receipt.pdf", null, APPLICATION_PDF.getType(), model, Mailer.AttachmentIdentifier.RECEIPT_PDF));
            case CREDIT_NOTE:
                return Collections.singletonList(new Mailer.Attachment("credit-note.pdf", null, APPLICATION_PDF.getType(), model, Mailer.AttachmentIdentifier.CREDIT_NOTE_PDF));
            default:
                throw new IllegalStateException(documentType+" is not supported");
        }
    }

    public String getReservationEmailSubject(PurchaseContext purchaseContext, Locale reservationLanguage, String key, String id) {
        return messageSourceManager.getMessageSourceFor(purchaseContext)
            .getMessage(key, new Object[]{id, purchaseContext.getDisplayName()}, reservationLanguage);
    }

    public Map<String, Object> prepareModelForReservationEmail(PurchaseContext purchaseContext,
                                                               TicketReservation reservation,
                                                               Optional<String> vat,
                                                               OrderSummary summary,
                                                               List<Ticket> ticketsToInclude,
                                                               Map<String, Object> initialOptions) {
        Organization organization = organizationRepository.getById(purchaseContext.getOrganizationId());
        String baseUrl = configurationManager.baseUrl(purchaseContext);
        var reservationId = reservation.getId();
        String reservationUrl = reservationUrl(reservation, purchaseContext, configurationManager);
        String reservationShortID = configurationManager.getShortReservationID(purchaseContext, reservation);

        var bankingInfo = configurationManager.getFor(Set.of(INVOICE_ADDRESS, BANK_ACCOUNT_NR, BANK_ACCOUNT_OWNER), ConfigurationLevel.purchaseContext(purchaseContext));
        Optional<String> invoiceAddress = bankingInfo.get(INVOICE_ADDRESS).getValue();
        Optional<String> bankAccountNr = bankingInfo.get(BANK_ACCOUNT_NR).getValue();
        Optional<String> bankAccountOwner = bankingInfo.get(BANK_ACCOUNT_OWNER).getValue();

        Map<Integer, List<Ticket>> ticketsByCategory = ticketsToInclude
            .stream()
            .collect(groupingBy(Ticket::getCategoryId));
        final List<TicketWithCategory> ticketsWithCategory = ReservationUtil.collectTicketsWithCategory(ticketsByCategory, ticketCategoryRepository);
        Map<String, Object> baseModel = new HashMap<>();
        baseModel.putAll(initialOptions);
        baseModel.putAll(extensionManager.handleReservationEmailCustomText(purchaseContext, reservation, ticketReservationRepository.getAdditionalInfo(reservationId))
            .map(CustomEmailText::toMap)
            .orElse(Map.of()));
        Map<String, Object> model = TemplateResource.prepareModelForConfirmationEmail(organization, purchaseContext, reservation, vat, ticketsWithCategory, summary, baseUrl, reservationUrl, reservationShortID, invoiceAddress, bankAccountNr, bankAccountOwner, baseModel);
        boolean euBusiness = StringUtils.isNotBlank(reservation.getVatCountryCode()) && StringUtils.isNotBlank(reservation.getVatNr())
            && configurationManager.getForSystem(ConfigurationKeys.EU_COUNTRIES_LIST).getRequiredValue().contains(reservation.getVatCountryCode())
            && PriceContainer.VatStatus.isVatExempt(reservation.getVatStatus());
        model.put("euBusiness", euBusiness);
        model.put("publicId", configurationManager.getPublicReservationID(purchaseContext, reservation));
        model.put("invoicingAdditionalInfo", ticketReservationRepository.getAdditionalInfo(reservationId).getInvoicingAdditionalInfo());
        if(purchaseContext.getType() == PurchaseContext.PurchaseContextType.event) {
            var event = purchaseContext.event().orElseThrow();
            model.put("displayLocation", ticketsWithCategory.stream()
                .noneMatch(tc -> EventUtil.isAccessOnline(tc.getCategory(), event)));
        } else {
            model.put("displayLocation", false);
        }
        if(ticketReservationRepository.hasSubscriptionApplied(reservationId)) {
            model.put("displaySubscriptionUsage", true);
            var subscription = subscriptionRepository.findAppliedSubscriptionByReservationId(reservationId).orElseThrow();
            if(subscription.getMaxEntries() > -1) {
                var subscriptionUsageDetails = UsageDetails.fromSubscription(subscription, ticketRepository.countSubscriptionUsage(subscription.getId(), null));
                model.put("subscriptionUsageDetails", subscriptionUsageDetails);
                model.put("subscriptionUrl", reservationUrl(reservation, purchaseContext, configurationManager));
            }
        }
        return model;
    }

    public void sendTicketByEmail(Ticket ticket, Locale locale, Event event, PartialTicketTextGenerator confirmationTextBuilder) {
        TicketReservation reservation = ticketReservationRepository.findReservationById(ticket.getTicketsReservationId());
        checkIfFinalized(reservation.getId());
        TicketCategory ticketCategory = ticketCategoryRepository.getByIdAndActive(ticket.getCategoryId(), event.getId());
        notificationManager.sendTicketByEmail(ticket, event, locale, confirmationTextBuilder, reservation, ticketCategory, () -> retrieveAttendeeAdditionalInfoForTicket(ticket));
    }

    private void checkIfFinalized(String reservationId) {
        if (!Boolean.TRUE.equals(ticketReservationRepository.checkIfFinalized(reservationId))) {
            throw new IncompatibleStateException("Reservation was confirmed but not finalized yet. Cannot send emails.");
        }
    }

    public Map<String, List<String>> retrieveAttendeeAdditionalInfoForTicket(Ticket ticket) {
        return purchaseContextFieldRepository.findNameAndValue(ticket.getId())
            .stream()
            .collect(groupingBy(FieldNameAndValue::getName, mapping(FieldNameAndValue::getValue, toList())));
    }

    public Map<String, List<String>> retrieveAttendeeAdditionalInfoForSubscription(UUID subscriptionId) {
        return purchaseContextFieldRepository.findNameAndValue(subscriptionId)
            .stream()
            .collect(groupingBy(FieldNameAndValue::getName, mapping(FieldNameAndValue::getValue, toList())));
    }

    public PartialTicketTextGenerator getTicketEmailGenerator(Event event,
                                                              TicketReservation ticketReservation,
                                                              Locale ticketLanguage,
                                                              Map<String, List<String>> additionalInfo) {
        return ticket -> {
            Organization organization = organizationRepository.getById(event.getOrganizationId());
            String ticketUrl = ReservationUtil.ticketUpdateUrl(event, ticket, configurationManager);
            var ticketCategory = ticketCategoryRepository.getById(ticket.getCategoryId());

            var initialModel = new HashMap<>(extensionManager.handleTicketEmailCustomText(event, ticketReservation, ticketReservationRepository.getAdditionalInfo(ticketReservation.getId()), purchaseContextFieldRepository.findAllByTicketId(ticket.getId()))
                .map(CustomEmailText::toMap)
                .orElse(Map.of()));
            if(EventUtil.isAccessOnline(ticketCategory, event)) {
                initialModel.putAll(TicketCheckInUtil.getOnlineCheckInInfo(
                    extensionManager,
                    eventRepository,
                    ticketCategoryRepository,
                    configurationManager,
                    event,
                    ticketLanguage,
                    ticket,
                    ticketCategory,
                    additionalInfo
                ));
            }
            var baseUrl = StringUtils.removeEnd(configurationManager.getFor(BASE_URL, ConfigurationLevel.event(event)).getRequiredValue(), "/");
            var calendarUrl = UriComponentsBuilder.fromUriString(baseUrl + "/api/v2/public/event/{eventShortName}/calendar/{currentLang}")
                .queryParam("type", "google")
                .build(Map.of("eventShortName", event.getShortName(), "currentLang", ticketLanguage.getLanguage()))
                .toString();
            return TemplateProcessor.buildPartialEmail(event, organization, ticketReservation, ticketCategory, templateManager, baseUrl, ticketUrl, calendarUrl, ticketLanguage, initialModel).generate(ticket);
        };
    }

    public Optional<String> getVAT(PurchaseContext purchaseContext) {
        return configurationManager.getFor(VAT_NR, purchaseContext.getConfigurationLevel()).getValue();
    }

    public Map<String, Object> prepareModelForReservationEmail(PurchaseContext purchaseContext, TicketReservation reservation) {
        Optional<String> vat = getVAT(purchaseContext);
        OrderSummary summary = orderSummaryGenerator.orderSummaryForReservationId(reservation.getId(), purchaseContext);
        return prepareModelForReservationEmail(purchaseContext, reservation, vat, summary, ticketRepository.findTicketsInReservation(reservation.getId()), Map.of());
    }
}
