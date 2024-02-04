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

import alfio.config.authentication.support.OpenIdAlfioAuthentication;
import alfio.controller.form.ContactAndTicketsForm;
import alfio.controller.form.UpdateTicketOwnerForm;
import alfio.extension.ExtensionService;
import alfio.extension.exception.AlfioScriptingException;
import alfio.manager.payment.PaymentSpecification;
import alfio.manager.support.extension.ExtensionCapability;
import alfio.manager.support.extension.ExtensionEvent;
import alfio.manager.support.extension.ValidationErrorNotifier;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.model.PromoCodeDiscount.CodeType;
import alfio.model.checkin.EventWithCheckInInfo;
import alfio.model.extension.*;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.metadata.SubscriptionMetadata;
import alfio.model.metadata.TicketMetadata;
import alfio.model.metadata.TicketMetadataContainer;
import alfio.model.modification.EventModification;
import alfio.model.subscription.Subscription;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.model.system.ConfigurationKeys;
import alfio.model.user.Organization;
import alfio.model.user.PublicUserProfile;
import alfio.model.user.User;
import alfio.repository.*;
import alfio.util.ClockProvider;
import alfio.util.EventUtil;
import alfio.util.MonetaryUtil;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static alfio.extension.ExtensionService.toPath;
import static alfio.manager.support.extension.ExtensionEvent.*;
import static alfio.model.PromoCodeDiscount.DiscountType.PERCENTAGE;

@Component
@AllArgsConstructor
@Log4j2
public class ExtensionManager {

    private static final String TICKET = "ticket";
    private static final String EVENT_METADATA = "eventMetadata";
    private static final String ORGANIZATION = "organization";
    private static final String RESERVATION = "reservation";
    private static final String BILLING_DETAILS = "billingDetails";
    private static final String ADDITIONAL_INFO = "additionalInfo";
    private static final String RESERVATIONS = "reservations";
    private static final String RESERVATION_IDS = "reservationIds";
    private static final String ORGANIZATION_ID = "organizationId";
    private static final String RESERVATION_ID = "reservationId";
    private static final String EVENT = "event";
    private static final String REQUEST = "request";
    private static final String METADATA = "metadata";
    public static final String TICKET_METADATA = "ticketMetadata";
    private final ExtensionService extensionService;
    private final EventRepository eventRepository;
    private final TicketReservationRepository ticketReservationRepository;
    private final TicketRepository ticketRepository;
    private final ConfigurationManager configurationManager;
    private final TransactionRepository transactionRepository;
    private final TicketCategoryRepository ticketCategoryRepository;


    boolean isSupported(ExtensionCapability extensionCapability, PurchaseContext purchaseContext) {
        return extensionService.isCapabilitySupported(extensionCapability, purchaseContext);
    }

    Set<ExtensionCapabilitySummary> getSupportedCapabilities(Set<ExtensionCapability> requested, PurchaseContext purchaseContext) {
        return extensionService.getSupportedCapabilities(requested, purchaseContext);
    }

    void handleEventValidation(EventModification request) {
        Map<String, Object> payload = Map.of(REQUEST, request);
        syncCall(EVENT_VALIDATE_CREATION, null, payload, Boolean.class, false);
    }

    void handleEventSeatsUpdateValidation(Event event, int seatsNumber) {
        var request = new EventModification(event.getId(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            event.getOrganizationId(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            event.getRegularPrice(),
            event.getCurrency(),
            seatsNumber,
            event.getVat(),
            event.isVatIncluded(),
            event.getAllowedPaymentProxies(),
            null,
            event.isFreeOfCharge(),
            null,
            event.getLocales(),
            null,
            null,
            null,
            null);
        Map<String, Object> payload = Map.of(REQUEST, request);
        syncCall(EVENT_VALIDATE_SEATS_PRICES_UPDATE, event, payload, Boolean.class, false);
    }

    void handleEventSeatsPricesUpdateValidation(Event event, EventModification request) {
        Map<String, Object> payload = Map.of(REQUEST, request);
        syncCall(EVENT_VALIDATE_SEATS_PRICES_UPDATE, event, payload, Boolean.class, false);
    }

    void handleEventCreation(Event event) {
        Map<String, Object> payload = Collections.emptyMap();
        syncCall(ExtensionEvent.EVENT_CREATED, event, payload, Boolean.class);
        asyncCall(ExtensionEvent.EVENT_CREATED, event, payload);
    }

    void handleEventStatusChange(Event event, Event.Status status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", status.name());
        syncCall(ExtensionEvent.EVENT_STATUS_CHANGE, event, payload, Boolean.class);
        asyncCall(ExtensionEvent.EVENT_STATUS_CHANGE, event, payload);
    }

    AlfioMetadata handleMetadataUpdate(Event event, Organization organization, AlfioMetadata metadata) {
        Map<String, Object> payload = buildMetadataUpdatePayload(organization, metadata);
        return syncCall(ExtensionEvent.EVENT_METADATA_UPDATE, event, payload, AlfioMetadata.class, false);
    }


    Optional<AlfioMetadata> handleGenerateMeetingLinkCapability(Event event,
                                                                Organization organization,
                                                                AlfioMetadata existingMetadata,
                                                                Map<String, String> requestParams) {
        Map<String, Object> context = buildMetadataUpdatePayload(organization, existingMetadata);
        context.put(REQUEST, requestParams);
        return internalExecuteCapability(ExtensionCapability.GENERATE_MEETING_LINK, context, event, AlfioMetadata.class);
    }

    Optional<String> handleGenerateLinkCapability(Event event,
                                                  AlfioMetadata existingMetadata,
                                                  Map<String, String> requestParams) {
        Map<String, Object> context = new HashMap<>();
        context.put(METADATA, existingMetadata);
        context.put(REQUEST, requestParams);
        return internalExecuteCapability(ExtensionCapability.LINK_EXTERNAL_APPLICATION, context, event, String.class);
    }

    private Map<String, Object> buildMetadataUpdatePayload(Organization organization, AlfioMetadata metadata) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(METADATA, metadata);
        payload.put(ORGANIZATION, organization);
        payload.put("baseUrl", configurationManager.getFor(ConfigurationKeys.BASE_URL, ConfigurationLevel.organization(organization.getId())).getRequiredValue());
        return payload;
    }

    void handleReservationConfirmation(TicketReservation reservation, BillingDetails billingDetails, PurchaseContext purchaseContext) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(RESERVATION, reservation);
        payload.put(BILLING_DETAILS, billingDetails);
        payload.put(ADDITIONAL_INFO, Map.of());
        transactionRepository.loadOptionalByReservationId(reservation.getId())
            .ifPresent(tr -> payload.put("transaction", tr));
        asyncCall(ExtensionEvent.RESERVATION_CONFIRMED,
            purchaseContext,
            payload);
    }

    public void handleTicketAssignment(Ticket ticket,
                                       TicketCategory category,
                                       Map<String, List<String>> additionalInfo) {
        if(!ticket.hasBeenSold()) {
            return; // ignore tickets if the reservation is not yet confirmed
        }
        int eventId = ticket.getEventId();
        Event event = eventRepository.findById(eventId);
        asyncCall(ExtensionEvent.TICKET_ASSIGNED,
            event,
            Map.of(
                TICKET, ticket,
                ADDITIONAL_INFO, Objects.requireNonNullElse(additionalInfo, Map.of()),
                EVENT_METADATA, Objects.requireNonNullElseGet(eventRepository.getMetadataForEvent(event.getId()), AlfioMetadata::empty),
                "onlineAccessTicket", EventUtil.isAccessOnline(category, event)
            ));
    }

    void handleWaitingQueueSubscription(WaitingQueueSubscription waitingQueueSubscription) {
        Event event = eventRepository.findById(waitingQueueSubscription.getEventId());
        asyncCall(ExtensionEvent.WAITING_QUEUE_SUBSCRIBED,
            event,
            Map.of("waitingQueueSubscription", waitingQueueSubscription, ADDITIONAL_INFO, Map.of()));
    }

    void handleReservationsExpiredForEvent(PurchaseContext purchaseContext, Collection<String> reservationIdsToRemove) {
        handleReservationRemoval(purchaseContext, reservationIdsToRemove, ExtensionEvent.RESERVATION_EXPIRED);
    }

    void handleReservationsCancelledForEvent(PurchaseContext purchaseContext, Collection<String> reservationIdsToRemove) {
        handleReservationRemoval(purchaseContext, reservationIdsToRemove, ExtensionEvent.RESERVATION_CANCELLED);
    }

    void handleTicketCancelledForEvent(Event event, Collection<String> ticketUUIDs) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("ticketUUIDs", ticketUUIDs);
        payload.put(EVENT_METADATA, eventRepository.getMetadataForEvent(event.getId()));
        syncCall(ExtensionEvent.TICKET_CANCELLED, event, payload, Boolean.class);
    }

    void handleOfflineReservationsWillExpire(Event event, List<TicketReservationInfo> reservations) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(RESERVATIONS, reservations);
        asyncCall(ExtensionEvent.OFFLINE_RESERVATIONS_WILL_EXPIRE, event, payload);
    }

    void handleStuckReservations(Event event, List<String> stuckReservationsId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(RESERVATION_IDS, stuckReservationsId);
        asyncCall(ExtensionEvent.STUCK_RESERVATIONS, event, payload);
    }

    public Optional<CustomEmailText> handleReservationEmailCustomText(PurchaseContext purchaseContext, TicketReservation reservation, TicketReservationAdditionalInfo additionalInfo) {
        Map<String, Object> payload = Map.of(
            RESERVATION, reservation,
            "purchaseContext", purchaseContext,
            "billingData", additionalInfo
        );
        try {
            return Optional.ofNullable(syncCall(ExtensionEvent.CONFIRMATION_MAIL_CUSTOM_TEXT, purchaseContext, payload, CustomEmailText.class));
        } catch(Exception ex) {
            log.warn("Cannot get confirmation mail additional text", ex);
            return Optional.empty();
        }
    }

    public Optional<CustomEmailText> handleTicketEmailCustomText(Event event, TicketReservation reservation, TicketReservationAdditionalInfo additionalInfo, List<PurchaseContextFieldValue> fields) {
        Map<String, Object> payload = Map.of(
            RESERVATION, reservation,
            EVENT, event,
            "billingData", additionalInfo,
            "additionalFields", fields
        );
        try {
            return Optional.ofNullable(syncCall(ExtensionEvent.TICKET_MAIL_CUSTOM_TEXT, event, payload, CustomEmailText.class));
        } catch(Exception ex) {
            log.warn("Cannot get ticket mail additional text", ex);
            return Optional.empty();
        }
    }

    private void handleReservationRemoval(PurchaseContext purchaseContext, Collection<String> reservationIds, ExtensionEvent extensionEvent) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(RESERVATION_IDS, reservationIds);
        payload.put(RESERVATIONS, ticketReservationRepository.findByIds(reservationIds));

        syncCall(extensionEvent, purchaseContext, payload, Boolean.class);
    }

    public void handleCreditNoteGenerated(TicketReservation reservation, PurchaseContext purchaseContext, TotalPrice cost, Long billingDocumentId, Map<String, Object> contextData) {
        Map<String, Object> payload = new HashMap<>(contextData);
        payload.put(RESERVATION_ID, reservation.getId());
        payload.put(RESERVATION, reservation);
        payload.put(BILLING_DETAILS, ticketReservationRepository.getBillingDetailsForReservation(reservation.getId()));
        payload.put("reservationCost", cost);
        payload.put("billingDocumentId", billingDocumentId);
        asyncCall(ExtensionEvent.CREDIT_NOTE_GENERATED, purchaseContext, payload);
    }

    public Optional<InvoiceGeneration> handleInvoiceGeneration(PaymentSpecification spec, TotalPrice reservationCost, BillingDetails billingDetails, Map<String, Object> contextData) {
        Map<String, Object> payload = new HashMap<>(contextData);
        payload.put(RESERVATION_ID, spec.getReservationId());
        payload.put("email", spec.getEmail());
        payload.put("customerName", spec.getCustomerName());
        payload.put("userLanguage", spec.getLocale().getLanguage());
        payload.put("billingAddress", spec.getBillingAddress());
        payload.put(BILLING_DETAILS, billingDetails);
        payload.put("customerReference", spec.getCustomerReference());
        payload.put("reservationCost", reservationCost);
        payload.put("invoiceRequested", spec.isInvoiceRequested());
        payload.put("vatCountryCode", billingDetails.getCountry());
        payload.put("vatNr", billingDetails.getTaxId());
        payload.put("vatStatus", spec.getVatStatus());

        return Optional.ofNullable(syncCall(ExtensionEvent.INVOICE_GENERATION, spec.getPurchaseContext(), payload, InvoiceGeneration.class, false));
    }

    public Optional<CreditNoteGeneration> handleCreditNoteGeneration(PurchaseContext purchaseContext,
                                                                     String reservationId,
                                                                     String invoiceNumber,
                                                                     Organization organization) {
        return Optional.ofNullable(syncCall(ExtensionEvent.CREDIT_NOTE_GENERATION, purchaseContext, Map.of(
            RESERVATION_ID, reservationId,
            "invoiceNumber", invoiceNumber,
            ORGANIZATION, organization
        ), CreditNoteGeneration.class));
    }

    public Optional<String> handleOnlineCheckInLink(String originalUrl,
                                                    Ticket ticket,
                                                    EventWithCheckInInfo event,
                                                    Map<String, List<String>> additionalInfo) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(EVENT, event);
        payload.put("eventId", event.getId());
        payload.put(ORGANIZATION_ID, event.getOrganizationId());
        payload.put(TICKET, ticket);
        payload.put("originalURL", originalUrl);
        payload.put(ADDITIONAL_INFO, Objects.requireNonNullElse(additionalInfo, Map.of()));

        return Optional.ofNullable(extensionService.executeScriptsForEvent(ONLINE_CHECK_IN_REDIRECT.name(),
            toPath(event),
            payload,
            String.class));
    }

    boolean handleTaxIdValidation(PurchaseContext purchaseContext, String taxIdNumber, String countryCode) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("taxIdNumber", taxIdNumber);
        payload.put("countryCode", countryCode);
        return Optional.ofNullable(syncCall(ExtensionEvent.TAX_ID_NUMBER_VALIDATION, purchaseContext, payload, Boolean.class)).orElse(false);
    }

    void handleTicketCheckedIn(Ticket ticket) {
        Map<String, Object> payload = new HashMap<>();
        Event event = eventRepository.findById(ticket.getEventId());
        payload.put(TICKET, ticket);
        asyncCall(ExtensionEvent.TICKET_CHECKED_IN, event, payload);
    }

    void handleTicketRevertCheckedIn(Ticket ticket) {
        Map<String, Object> payload = new HashMap<>();
        Event event = eventRepository.findById(ticket.getEventId());
        payload.put(TICKET, ticket);
        asyncCall(ExtensionEvent.TICKET_REVERT_CHECKED_IN, event, payload);
    }

    public void handleReservationValidation(PurchaseContext purchaseContext, TicketReservation reservation, Object clientForm, BindingResult bindingResult) {
        Map<String, Object> payload = Map.of(
            RESERVATION_ID, reservation.getId(),
            RESERVATION, reservation,
            "form", clientForm,
            "bindingResult", bindingResult
        );

        syncCall(ExtensionEvent.RESERVATION_VALIDATION, purchaseContext, payload, Void.class);
    }

    public void handleTicketUpdateValidation(PurchaseContext purchaseContext, UpdateTicketOwnerForm form, BindingResult bindingResult, String keyPrefix) {
        Map<String, Object> payload = Map.of(
            "form", form,
            "validationErrorNotifier", new ValidationErrorNotifier(bindingResult, keyPrefix)
        );

        syncCall(TICKET_UPDATE_VALIDATION, purchaseContext, payload, Void.class);
    }

    public void handleUserProfileValidation(Object clientForm, BindingResult bindingResult) {
        Map<String, Object> payload = Map.of(
            "form", clientForm,
            "bindingResult", bindingResult
        );

        syncCall(ExtensionEvent.PUBLIC_USER_PROFILE_VALIDATION, null, payload, Void.class);
    }

    void handleEventHeaderUpdate(Event event, Organization organization) {
        Map<String, Object> payload = Map.of(
            EVENT_METADATA, eventRepository.getMetadataForEvent(event.getId()),
            ORGANIZATION, organization
        );
        asyncCall(ExtensionEvent.EVENT_HEADER_UPDATED, event, payload);
    }

    void handleReservationsCreditNoteIssuedForEvent(Event event, List<String> reservationIds) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(RESERVATION_IDS, reservationIds);
        payload.put(RESERVATIONS, ticketReservationRepository.findByIds(reservationIds));

        syncCall(ExtensionEvent.RESERVATION_CREDIT_NOTE_ISSUED, event, payload, Boolean.class);
    }

    void handleRefund(PurchaseContext purchaseContext, TicketReservation reservation, TransactionAndPaymentInfo info) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(RESERVATION, reservation);
        payload.put("transaction", info.getTransaction());
        payload.put("paymentInfo", info.getPaymentInformation());
        asyncCall(ExtensionEvent.REFUND_ISSUED, purchaseContext, payload);
    }

    /**
     * returns the keys to save from the given map
     *
     * @param purchaseContext the current Purchase Context
     * @param userAdditionalData user data to filter
     * @param userProfile existing user profile, may be null
     * @return the keys to persist, or {@code null}
     */
    public Optional<List<AdditionalInfoItem>> filterAdditionalInfoToSave(PurchaseContext purchaseContext,
                                                               Map<String, List<String>> userAdditionalData,
                                                               PublicUserProfile userProfile) {
        var payload = new HashMap<String, Object>();
        payload.put("userAdditionalData", userAdditionalData);
        payload.put("userProfile", userProfile);
        var result = syncCall(ExtensionEvent.USER_ADDITIONAL_INFO_FILTER, purchaseContext, payload, AdditionalInfoFilterResult.class);
        if(result != null) {
            return Optional.of(result.getItems());
        }
        return Optional.empty();
    }

    public boolean handlePdfTransformation(String html, PurchaseContext purchaseContext, OutputStream outputStream) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("html", html);
        try {
            PdfGenerationResult response = syncCall(ExtensionEvent.PDF_GENERATION, purchaseContext, payload, PdfGenerationResult.class);
            if(response == null || response.isEmpty()) {
                return false;
            }
            Path tempFilePath = Paths.get(response.getTempFilePath());
            if(Files.exists(tempFilePath)) {
                Files.copy(tempFilePath, outputStream);
                Files.delete(tempFilePath);
                return true;
            }
            return false;
        } catch(Exception e) {
            return false;
        }
    }

    public Optional<String> generateOAuth2StateParam(int organizationId) {
        try {
            return Optional.ofNullable(extensionService.executeScriptsForEvent(ExtensionEvent.OAUTH2_STATE_GENERATION.name(),
                "-" + organizationId,
                Map.of("baseUrl", configurationManager.getFor(ConfigurationKeys.BASE_URL, ConfigurationLevel.organization(organizationId)).getRequiredValue(), ORGANIZATION_ID, organizationId),
                String.class));
        } catch (Exception ex) {
            log.error("got exception while generating OAuth2 State Param", ex);
            throw new IllegalStateException(ex);
        }
    }

    public Optional<PromoCodeDiscount> handleDynamicDiscount(Event event, Map<Integer, Long> quantityByCategory, String reservationId) {
        try {
            var values = new HashMap<String, Object>();
            values.put("quantityByCategory", quantityByCategory.entrySet().stream()
                .map(entry -> new QuantityByCategoryId(entry.getKey(), entry.getValue().intValue())).collect(Collectors.toList()));
            values.put(RESERVATION_ID, reservationId);
            var dynamicDiscountResult = syncCall(ExtensionEvent.DYNAMIC_DISCOUNT_APPLICATION, event,
                values, DynamicDiscount.class);
            if(dynamicDiscountResult == null || dynamicDiscountResult.getDiscountType() == PromoCodeDiscount.DiscountType.NONE) {
                return Optional.empty();
            }
            var now = ZonedDateTime.now(ClockProvider.clock());
            // dynamic discount is supposed to return a formatted amount in the event's currency
            var discountAmount = new BigDecimal(dynamicDiscountResult.getAmount());
            int discountAmountInCents = dynamicDiscountResult.getDiscountType() == PERCENTAGE ? discountAmount.intValue() : MonetaryUtil.unitToCents(discountAmount, event.getCurrency());
            return Optional.of(new PromoCodeDiscount(Integer.MIN_VALUE, dynamicDiscountResult.getCode(), event.getId(),
                event.getOrganizationId(), now.minusSeconds(1), event.getBegin().withZoneSameInstant(now.getZone()), discountAmountInCents,
                dynamicDiscountResult.getDiscountType(), null, null, null,
                null, CodeType.DYNAMIC, null, event.getCurrency()));
        } catch(Exception ex) {
            log.warn("got exception while firing DYNAMIC_DISCOUNT_APPLICATION event", ex);
        }
        return Optional.empty();
    }

    private void asyncCall(ExtensionEvent extensionEvent, PurchaseContext event, Map<String, Object> payload) {
        extensionService.executeScriptAsync(extensionEvent.name(),
            toPath(event),
            fillWithBasicInfo(payload, event));
    }

    private <T> T syncCall(ExtensionEvent extensionEvent, PurchaseContext purchaseContext, Map<String, Object> payload, Class<T> clazz) {
        return syncCall(extensionEvent, purchaseContext, payload, clazz, true);
    }

    private <T> T syncCall(ExtensionEvent extensionEvent, PurchaseContext purchaseContext, Map<String, Object> payload, Class<T> clazz, boolean ignoreErrors) {
        try {
            return extensionService.executeScriptsForEvent(extensionEvent.name(),
                toPath(purchaseContext),
                fillWithBasicInfo(payload, purchaseContext),
                clazz);
        } catch(AlfioScriptingException ex) {
            log.warn("Unexpected exception while executing script:", ex);
            if(!ignoreErrors) {
                throw new IllegalStateException(ex);
            }
        }
        return null;
    }

    private Map<String, Object> fillWithBasicInfo(Map<String, ?> payload, PurchaseContext purchaseContext) {
        Map<String, Object> payloadCopy = new HashMap<>(payload);
        if(purchaseContext != null) {
            //FIXME ugly
            purchaseContext.event().ifPresent(event -> {
                payloadCopy.put(EVENT, event);
                payloadCopy.put("eventId", event.getId());
            });
            payloadCopy.put("purchaseContext", purchaseContext);
            payloadCopy.put(ORGANIZATION_ID, purchaseContext.getOrganizationId());
        }
        return payloadCopy;
    }

    public <T> Optional<T> executeCapability(ExtensionCapability capability,
                                             Map<String, String> params,
                                             PurchaseContext purchaseContext,
                                             Class<T> resultType) {
        var contextParams = new HashMap<String, Object>();
        contextParams.put(REQUEST, params);
        return internalExecuteCapability(capability, contextParams, purchaseContext, resultType);
    }

    private <T> Optional<T> internalExecuteCapability(ExtensionCapability capability,
                                                      Map<String, Object> contextParams,
                                                      PurchaseContext purchaseContext,
                                                      Class<T> resultType) {
        return extensionService.executeCapability(capability,
            toPath(purchaseContext),
            fillWithBasicInfo(contextParams, purchaseContext),
            resultType);
    }

    public void handlePublicUserSignUp(User user) {
        asyncCall(PUBLIC_USER_SIGN_UP, null, Map.of("user", user));
    }

    public void handlePublicUserDelete(OpenIdAlfioAuthentication authentication, User user) {
        syncCall(PUBLIC_USER_DELETE, null, Map.of("user", user, "subject", authentication.getPrincipal()), Void.class);
    }

    public Optional<TicketMetadata> handleCustomOnlineJoinUrl(Event event,
                                                              Ticket ticket,
                                                              Map<String, List<String>> ticketAdditionalInfo) {
        var ticketMetadataContainer = Objects.requireNonNullElseGet(ticketRepository.getTicketMetadata(ticket.getId()), TicketMetadataContainer::empty);
        var context = new HashMap<String, Object>();
        var key = ExtensionEvent.CUSTOM_ONLINE_JOIN_URL.name();
        context.put(TICKET, ticket);
        context.put(ADDITIONAL_INFO, ticketAdditionalInfo);
        var existingMetadata = ticketMetadataContainer.getMetadataForKey(key);
        existingMetadata.ifPresent(m -> context.put(TICKET_METADATA, m));
        var result = Optional.ofNullable(syncCall(ExtensionEvent.CUSTOM_ONLINE_JOIN_URL, event, context, TicketMetadata.class, false));
        result.ifPresent(m -> {
            // we update the value only if it's changed
            boolean changed = existingMetadata.isEmpty() || !existingMetadata.get().equals(m);
            if(changed && ticketMetadataContainer.putMetadata(key, m)) {
                ticketRepository.updateTicketMetadata(ticket.getId(), ticketMetadataContainer);
            }
        });
        return result.or(() -> existingMetadata);
    }

    public Optional<TicketMetadata> handleTicketAssignmentMetadata(TicketWithMetadataAttributes ticketWithMetadata,
                                                                   Event event,
                                                                   Map<String, List<String>> additionalInfo) {

        var context = new HashMap<String, Object>();
        context.put(TICKET, ticketWithMetadata.getTicket());
        context.put(TICKET_METADATA, ticketWithMetadata.getMetadata().getMetadataForKey(TicketMetadataContainer.GENERAL).orElseGet(TicketMetadata::empty));
        context.put(ADDITIONAL_INFO, Objects.requireNonNullElse(additionalInfo, Map.of()));
        return Optional.ofNullable(syncCall(ExtensionEvent.TICKET_ASSIGNED_GENERATE_METADATA, event, context, TicketMetadata.class, false));
    }

    public Optional<SubscriptionMetadata> handleSubscriptionAssignmentMetadata(Subscription subscription,
                                                                               SubscriptionDescriptor descriptor,
                                                                               SubscriptionMetadata subscriptionMetadata,
                                                                               Map<String, List<String>> additionalInfo) {
        var context = new HashMap<String, Object>();
        context.put("subscription", subscription);
        context.put(METADATA, Objects.requireNonNullElseGet(subscriptionMetadata, SubscriptionMetadata::empty));
        context.put("subscriptionDescriptor", descriptor);
        context.put(ADDITIONAL_INFO, additionalInfo);
        return Optional.ofNullable(syncCall(ExtensionEvent.SUBSCRIPTION_ASSIGNED_GENERATE_METADATA, descriptor, context, SubscriptionMetadata.class, false));
    }

    public Optional<CustomTaxPolicy> handleCustomTaxPolicy(PurchaseContext purchaseContext,
                                                           String reservationId,
                                                           ContactAndTicketsForm form,
                                                           TotalPrice reservationCost) {
        if (!purchaseContext.ofType(PurchaseContext.PurchaseContextType.event) || !reservationCost.requiresPayment()) {
            return Optional.empty();
        }
        var event = (Event) purchaseContext;
        var categoriesById = ticketCategoryRepository.findCategoriesInReservation(reservationId).stream()
            .collect(Collectors.toMap(TicketCategory::getId, Function.identity()));
        var ticketInfoById = ticketRepository.findBasicTicketInfoForReservation(event.getId(), reservationId).stream()
            .collect(Collectors.toMap(TicketInfo::getTicketUuid, Function.identity()));
        var context = new HashMap<String, Object>();
        context.put(EVENT, event);
        context.put(RESERVATION_ID, reservationId);
        context.put("reservationForm", form);
        context.put("categoriesById", categoriesById);
        context.put("ticketInfoByUuid", ticketInfoById);
        return Optional.ofNullable(syncCall(CUSTOM_TAX_POLICY_APPLICATION, event, context, CustomTaxPolicy.class, false));
    }
}
