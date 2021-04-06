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

import alfio.extension.ExtensionService;
import alfio.extension.exception.AlfioScriptingException;
import alfio.manager.payment.PaymentSpecification;
import alfio.manager.support.extension.ExtensionCapability;
import alfio.manager.support.extension.ExtensionEvent;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.model.PromoCodeDiscount.CodeType;
import alfio.model.checkin.EventWithCheckInInfo;
import alfio.model.extension.*;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.system.ConfigurationKeys;
import alfio.model.user.Organization;
import alfio.repository.EventRepository;
import alfio.repository.TicketReservationRepository;
import alfio.repository.TransactionRepository;
import alfio.util.ClockProvider;
import alfio.util.MonetaryUtil;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.*;

import static alfio.manager.support.extension.ExtensionEvent.ONLINE_CHECK_IN_REDIRECT;
import static alfio.model.PromoCodeDiscount.DiscountType.PERCENTAGE;

@Component
@AllArgsConstructor
@Log4j2
public class ExtensionManager {

    private final ExtensionService extensionService;
    private final EventRepository eventRepository;
    private final TicketReservationRepository ticketReservationRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ConfigurationManager configurationManager;
    private final TransactionRepository transactionRepository;


    boolean isSupported(ExtensionCapability extensionCapability, PurchaseContext purchaseContext) {
        return extensionService.isCapabilitySupported(extensionCapability, purchaseContext);
    }

    Set<ExtensionCapability> getSupportedCapabilities(Set<ExtensionCapability> requested, PurchaseContext purchaseContext) {
        return extensionService.getSupportedCapabilities(requested, purchaseContext);
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
        Map<String, Object> payload = new HashMap<>();
        payload.put("metadata", metadata);
        payload.put("organization", organization);
        payload.put("baseUrl", configurationManager.getFor(ConfigurationKeys.BASE_URL, ConfigurationLevel.organization(organization.getId())).getRequiredValue());
        return syncCall(ExtensionEvent.EVENT_METADATA_UPDATE, event, payload, AlfioMetadata.class);
    }

    void handleReservationConfirmation(TicketReservation reservation, BillingDetails billingDetails, PurchaseContext purchaseContext) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reservation", reservation);
        payload.put("billingDetails", billingDetails);
        payload.put("additionalInfo", Map.of());
        transactionRepository.loadOptionalByReservationId(reservation.getId())
            .ifPresent(tr -> payload.put("transaction", tr));
        asyncCall(ExtensionEvent.RESERVATION_CONFIRMED,
                purchaseContext,
            payload);
    }

    void handleTicketAssignment(Ticket ticket, Map<String, List<String>> additionalInfo) {
        if(!ticket.hasBeenSold()) {
            return; // ignore tickets if the reservation is not yet confirmed
        }
        int eventId = ticket.getEventId();
        //int organizationId = eventRepository.findOrganizationIdByEventId(eventId);
        Event event = eventRepository.findById(eventId);
        asyncCall(ExtensionEvent.TICKET_ASSIGNED,
            event,
            Map.of("ticket", ticket, "additionalInfo", additionalInfo));
    }

    void handleWaitingQueueSubscription(WaitingQueueSubscription waitingQueueSubscription) {
        Event event = eventRepository.findById(waitingQueueSubscription.getEventId());
        asyncCall(ExtensionEvent.WAITING_QUEUE_SUBSCRIBED,
            event,
            Map.of("waitingQueueSubscription", waitingQueueSubscription, "additionalInfo", Map.of()));
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

        syncCall(ExtensionEvent.TICKET_CANCELLED, event, payload, Boolean.class);
    }

    void handleOfflineReservationsWillExpire(Event event, List<TicketReservationInfo> reservations) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reservations", reservations);
        asyncCall(ExtensionEvent.OFFLINE_RESERVATIONS_WILL_EXPIRE, event, payload);
    }

    void handleStuckReservations(Event event, List<String> stuckReservationsId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reservationIds", stuckReservationsId);
        asyncCall(ExtensionEvent.STUCK_RESERVATIONS, event, payload);
    }

    Optional<CustomEmailText> handleReservationEmailCustomText(PurchaseContext purchaseContext, TicketReservation reservation, TicketReservationAdditionalInfo additionalInfo) {
        Map<String, Object> payload = Map.of(
            "reservation", reservation,
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

    public Optional<CustomEmailText> handleTicketEmailCustomText(Event event, TicketReservation reservation, TicketReservationAdditionalInfo additionalInfo, List<TicketFieldValue> fields) {
        Map<String, Object> payload = Map.of(
            "reservation", reservation,
            "event", event,
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
        payload.put("reservationIds", reservationIds);
        payload.put("reservations", ticketReservationRepository.findByIds(reservationIds));

        syncCall(extensionEvent, purchaseContext, payload, Boolean.class);
    }

    public void handleCreditNoteGenerated(TicketReservation reservation, PurchaseContext purchaseContext, TotalPrice cost, Long billingDocumentId, Map<String, Object> contextData) {
        Map<String, Object> payload = new HashMap<>(contextData);
        payload.put("reservationId", reservation.getId());
        payload.put("reservation", reservation);
        payload.put("billingDetails", ticketReservationRepository.getBillingDetailsForReservation(reservation.getId()));
        payload.put("reservationCost", cost);
        payload.put("billingDocumentId", billingDocumentId);
        asyncCall(ExtensionEvent.CREDIT_NOTE_GENERATED, purchaseContext, payload);
    }

    public Optional<InvoiceGeneration> handleInvoiceGeneration(PaymentSpecification spec, TotalPrice reservationCost, BillingDetails billingDetails, Map<String, Object> contextData) {
        Map<String, Object> payload = new HashMap<>(contextData);
        payload.put("reservationId", spec.getReservationId());
        payload.put("email", spec.getEmail());
        payload.put("customerName", spec.getCustomerName());
        payload.put("userLanguage", spec.getLocale().getLanguage());
        payload.put("billingAddress", spec.getBillingAddress());
        payload.put("billingDetails", billingDetails);
        payload.put("customerReference", spec.getCustomerReference());
        payload.put("reservationCost", reservationCost);
        payload.put("invoiceRequested", spec.isInvoiceRequested());
        payload.put("vatCountryCode", billingDetails.getCountry());
        payload.put("vatNr", billingDetails.getTaxId());
        payload.put("vatStatus", spec.getVatStatus());

        return Optional.ofNullable(syncCall(ExtensionEvent.INVOICE_GENERATION, spec.getPurchaseContext(), payload, InvoiceGeneration.class));
    }

    public Optional<CreditNoteGeneration> handleCreditNoteGeneration(PurchaseContext purchaseContext,
                                                                     String reservationId,
                                                                     String invoiceNumber,
                                                                     Organization organization) {
        return Optional.ofNullable(syncCall(ExtensionEvent.CREDIT_NOTE_GENERATION, purchaseContext, Map.of(
            "reservationId", reservationId,
            "invoiceNumber", invoiceNumber,
            "organization", organization
        ), CreditNoteGeneration.class));
    }

    public Optional<String> handleOnlineCheckInLink(String originalUrl, Ticket ticket, EventWithCheckInInfo event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", event);
        payload.put("eventId", event.getId());
        payload.put("organizationId", event.getOrganizationId());
        payload.put("ticket", ticket);
        payload.put("originalURL", originalUrl);

        return Optional.ofNullable(extensionService.executeScriptsForEvent(ONLINE_CHECK_IN_REDIRECT.name(),
            toPath(event),
            payload,
            String.class));
    }

    // FIXME: should not depend only by event id!
    boolean handleTaxIdValidation(PurchaseContext purchaseContext, String taxIdNumber, String countryCode) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("taxIdNumber", taxIdNumber);
        payload.put("countryCode", countryCode);
        return Optional.ofNullable(syncCall(ExtensionEvent.TAX_ID_NUMBER_VALIDATION, purchaseContext, payload, Boolean.class)).orElse(false);
    }

    void handleTicketCheckedIn(Ticket ticket) {
        Map<String, Object> payload = new HashMap<>();
        Event event = eventRepository.findById(ticket.getEventId());
        payload.put("ticket", ticket);
        asyncCall(ExtensionEvent.TICKET_CHECKED_IN, event, payload);
    }

    void handleTicketRevertCheckedIn(Ticket ticket) {
        Map<String, Object> payload = new HashMap<>();
        Event event = eventRepository.findById(ticket.getEventId());
        payload.put("ticket", ticket);
        asyncCall(ExtensionEvent.TICKET_REVERT_CHECKED_IN, event, payload);
    }

    @Transactional(readOnly = true)
    public void handleReservationValidation(PurchaseContext purchaseContext, TicketReservation reservation, Object clientForm, BindingResult bindingResult) {
        Map<String, Object> payload = Map.of(
            "reservationId", reservation.getId(),
            "reservation", reservation,
            "form", clientForm,
            "jdbcTemplate", jdbcTemplate,
            "bindingResult", bindingResult
        );

        syncCall(ExtensionEvent.RESERVATION_VALIDATION, purchaseContext, payload, Void.class);
    }

    void handleReservationsCreditNoteIssuedForEvent(Event event, List<String> reservationIds) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reservationIds", reservationIds);
        payload.put("reservations", ticketReservationRepository.findByIds(reservationIds));

        syncCall(ExtensionEvent.RESERVATION_CREDIT_NOTE_ISSUED, event, payload, Boolean.class);
    }

    void handleRefund(PurchaseContext purchaseContext, TicketReservation reservation, TransactionAndPaymentInfo info) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reservation", reservation);
        payload.put("transaction", info.getTransaction());
        payload.put("paymentInfo", info.getPaymentInformation());
        asyncCall(ExtensionEvent.REFUND_ISSUED, purchaseContext, payload);
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
                Map.of("baseUrl", configurationManager.getFor(ConfigurationKeys.BASE_URL, ConfigurationLevel.organization(organizationId)).getRequiredValue(), "organizationId", organizationId),
                String.class));
        } catch (Exception ex) {
            log.error("got exception while generating OAuth2 State Param", ex);
            throw new IllegalStateException(ex);
        }
    }

    public Optional<PromoCodeDiscount> handleDynamicDiscount(Event event, Map<Integer, Long> quantityByCategory, String reservationId) {
        try {
            var values = new HashMap<String, Object>();
            values.put("quantityByCategory", quantityByCategory);
            values.put("reservationId", reservationId);
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
                null, CodeType.DYNAMIC, null));
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
        try {
            return extensionService.executeScriptsForEvent(extensionEvent.name(),
                toPath(purchaseContext),
                fillWithBasicInfo(payload, purchaseContext),
                clazz);
        } catch(AlfioScriptingException ex) {
            log.warn("Unexpected exception while executing script:", ex);
            return null;
        }
    }

    private Map<String, Object> fillWithBasicInfo(Map<String, ?> payload, PurchaseContext purchaseContext) {
        Map<String, Object> payloadCopy = new HashMap<>(payload);
        //FIXME ugly
        purchaseContext.event().ifPresent(event -> {
            payloadCopy.put("event", event);
            payloadCopy.put("eventId", event.getId());
        });
        payloadCopy.put("purchaseContext", purchaseContext);
        payloadCopy.put("organizationId", purchaseContext.getOrganizationId());
        return payloadCopy;
    }

    public static String toPath(EventAndOrganizationId event) {
        return "-" + event.getOrganizationId() + "-" + event.getId();
    }

    public static String toPath(PurchaseContext purchaseContext) {
        int organizationId = purchaseContext.getOrganizationId();
        return purchaseContext.event().map(e -> toPath((EventAndOrganizationId) e)).orElseGet(() -> "-" + organizationId);
    }

    public <T> Optional<T> executeCapability(ExtensionCapability capability,
                                   Map<String, String> params,
                                   PurchaseContext purchaseContext,
                                   Class<T> resultType) {
        return extensionService.executeCapability(capability,
            toPath(purchaseContext),
            fillWithBasicInfo(params, purchaseContext),
            resultType);
    }
}
