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
import alfio.manager.payment.PaymentSpecification;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.model.extension.CustomEmailText;
import alfio.model.extension.InvoiceGeneration;
import alfio.model.extension.PdfGenerationResult;
import alfio.model.system.ConfigurationKeys;
import alfio.repository.EventRepository;
import alfio.repository.TicketReservationRepository;
import alfio.repository.TransactionRepository;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

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


    public enum ExtensionEvent {
        RESERVATION_CONFIRMED,
        RESERVATION_CANCELLED,
        RESERVATION_CREDIT_NOTE_ISSUED,
        TICKET_CANCELLED,
        RESERVATION_EXPIRED,
        TICKET_ASSIGNED,
        WAITING_QUEUE_SUBSCRIBED,
        INVOICE_GENERATION,
        TAX_ID_NUMBER_VALIDATION,
        RESERVATION_VALIDATION,
        //
        STUCK_RESERVATIONS,
        OFFLINE_RESERVATIONS_WILL_EXPIRE,
        EVENT_CREATED,
        EVENT_STATUS_CHANGE,
        WEB_API_HOOK,
        TICKET_CHECKED_IN,
        TICKET_REVERT_CHECKED_IN,
        PDF_GENERATION,
        STRIPE_CONNECT_STATE_GENERATION,

        CONFIRMATION_MAIL_CUSTOM_TEXT,
        TICKET_MAIL_CUSTOM_TEXT,
        REFUND_ISSUED
    }

    void handleEventCreation(Event event) {
        Map<String, Object> payload = Collections.emptyMap();
        syncCall(ExtensionEvent.EVENT_CREATED, event, event.getOrganizationId(), payload, Boolean.class);
        asyncCall(ExtensionEvent.EVENT_CREATED, event, event.getOrganizationId(), payload);
    }

    void handleEventStatusChange(Event event, Event.Status status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", status.name());
        syncCall(ExtensionEvent.EVENT_STATUS_CHANGE, event, event.getOrganizationId(), payload, Boolean.class);
        asyncCall(ExtensionEvent.EVENT_STATUS_CHANGE, event, event.getOrganizationId(), payload);
    }

    void handleReservationConfirmation(TicketReservation reservation, BillingDetails billingDetails, int eventId) {
        int organizationId = eventRepository.findOrganizationIdByEventId(eventId);
        Event event = eventRepository.findById(eventId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("reservation", reservation);
        payload.put("billingDetails", billingDetails);
        transactionRepository.loadOptionalByReservationId(reservation.getId())
            .ifPresent(tr -> payload.put("transaction", tr));
        asyncCall(ExtensionEvent.RESERVATION_CONFIRMED,
            event,
            organizationId,
            payload);
    }

    void handleTicketAssignment(Ticket ticket) {
        int eventId = ticket.getEventId();
        int organizationId = eventRepository.findOrganizationIdByEventId(eventId);
        Event event = eventRepository.findById(eventId);
        asyncCall(ExtensionEvent.TICKET_ASSIGNED,
            event,
            organizationId,
            Collections.singletonMap("ticket", ticket));
    }

    void handleWaitingQueueSubscription(WaitingQueueSubscription waitingQueueSubscription) {
        int organizationId = eventRepository.findOrganizationIdByEventId(waitingQueueSubscription.getEventId());

        Event event = eventRepository.findById(waitingQueueSubscription.getEventId());
        asyncCall(ExtensionEvent.WAITING_QUEUE_SUBSCRIBED,
            event,
            organizationId,
            Collections.singletonMap("waitingQueueSubscription", waitingQueueSubscription));
    }

    void handleReservationsExpiredForEvent(Event event, Collection<String> reservationIdsToRemove) {
        handleReservationRemoval(event, reservationIdsToRemove, ExtensionEvent.RESERVATION_EXPIRED);
    }

    void handleReservationsCancelledForEvent(Event event, Collection<String> reservationIdsToRemove) {
        handleReservationRemoval(event, reservationIdsToRemove, ExtensionEvent.RESERVATION_CANCELLED);
    }

    void handleTicketCancelledForEvent(Event event, Collection<String> ticketUUIDs) {
        int organizationId = event.getOrganizationId();

        Map<String, Object> payload = new HashMap<>();
        payload.put("ticketUUIDs", ticketUUIDs);

        syncCall(ExtensionEvent.TICKET_CANCELLED, event, organizationId, payload, Boolean.class);
    }

    void handleOfflineReservationsWillExpire(Event event, List<TicketReservationInfo> reservations) {
        int organizationId = eventRepository.findOrganizationIdByEventId(event.getOrganizationId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("reservations", reservations);
        asyncCall(ExtensionEvent.OFFLINE_RESERVATIONS_WILL_EXPIRE, event, organizationId, payload);
    }

    void handleStuckReservations(Event event, List<String> stuckReservationsId) {
        int organizationId = event.getOrganizationId();
        Map<String, Object> payload = new HashMap<>();
        payload.put("reservationIds", stuckReservationsId);
        asyncCall(ExtensionEvent.STUCK_RESERVATIONS, event, organizationId, payload);
    }

    Optional<CustomEmailText> handleReservationEmailCustomText(Event event, TicketReservation reservation, TicketReservationAdditionalInfo additionalInfo) {
        Map<String, Object> payload = Map.of(
            "reservation", reservation,
            "event", event,
            "billingData", additionalInfo
        );
        try {
            return Optional.ofNullable(syncCall(ExtensionEvent.CONFIRMATION_MAIL_CUSTOM_TEXT, event, event.getOrganizationId(), payload, CustomEmailText.class));
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
            return Optional.ofNullable(syncCall(ExtensionEvent.TICKET_MAIL_CUSTOM_TEXT, event, event.getOrganizationId(), payload, CustomEmailText.class));
        } catch(Exception ex) {
            log.warn("Cannot get ticket mail additional text", ex);
            return Optional.empty();
        }
    }

    private void handleReservationRemoval(Event event, Collection<String> reservationIds, ExtensionEvent extensionEvent) {
        int organizationId = event.getOrganizationId();

        Map<String, Object> payload = new HashMap<>();
        payload.put("reservationIds", reservationIds);
        payload.put("reservations", ticketReservationRepository.findByIds(reservationIds));

        syncCall(extensionEvent, event, organizationId, payload, Boolean.class);
    }

    public Optional<InvoiceGeneration> handleInvoiceGeneration(PaymentSpecification spec, TotalPrice reservationCost, BillingDetails billingDetails) {
        Map<String, Object> payload = new HashMap<>();
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

        return Optional.ofNullable(syncCall(ExtensionEvent.INVOICE_GENERATION, spec.getEvent(), spec.getEvent().getOrganizationId(), payload, InvoiceGeneration.class));
    }

    boolean handleTaxIdValidation(int eventId, String taxIdNumber, String countryCode) {
        Event event = eventRepository.findById(eventId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("taxIdNumber", taxIdNumber);
        payload.put("countryCode", countryCode);
        return Optional.ofNullable(syncCall(ExtensionEvent.TAX_ID_NUMBER_VALIDATION, event, event.getOrganizationId(), payload, Boolean.class)).orElse(false);
    }

    void handleTicketCheckedIn(Ticket ticket) {
        Map<String, Object> payload = new HashMap<>();
        Event event = eventRepository.findById(ticket.getEventId());
        payload.put("ticket", ticket);
        asyncCall(ExtensionEvent.TICKET_CHECKED_IN, event, event.getOrganizationId(), payload);
    }

    void handleTicketRevertCheckedIn(Ticket ticket) {
        Map<String, Object> payload = new HashMap<>();
        Event event = eventRepository.findById(ticket.getEventId());
        payload.put("ticket", ticket);
        asyncCall(ExtensionEvent.TICKET_REVERT_CHECKED_IN, event, event.getOrganizationId(), payload);
    }

    @Transactional(readOnly = true)
    public void handleReservationValidation(Event event, TicketReservation reservation, Object clientForm, BindingResult bindingResult) {
        Map<String, Object> payload = Map.of(
            "reservationId", reservation.getId(),
            "reservation", reservation,
            "form", clientForm,
            "jdbcTemplate", jdbcTemplate,
            "bindingResult", bindingResult
        );

        syncCall(ExtensionEvent.RESERVATION_VALIDATION, event, event.getOrganizationId(), payload, Void.class);
    }

    void handleReservationsCreditNoteIssuedForEvent(Event event, List<String> reservationIds) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reservationIds", reservationIds);
        payload.put("reservations", ticketReservationRepository.findByIds(reservationIds));

        syncCall(ExtensionEvent.RESERVATION_CREDIT_NOTE_ISSUED, event, event.getOrganizationId(), payload, Boolean.class);
    }

    void handleRefund(Event event, TicketReservation reservation, TransactionAndPaymentInfo info) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reservation", reservation);
        payload.put("transaction", info.getTransaction());
        payload.put("paymentInfo", info.getPaymentInformation());
        asyncCall(ExtensionEvent.REFUND_ISSUED, event, event.getOrganizationId(), payload);
    }

    public boolean handlePdfTransformation(String html, Event event, OutputStream outputStream) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("html", html);
        try {
            PdfGenerationResult response = syncCall(ExtensionEvent.PDF_GENERATION, event, event.getOrganizationId(), payload, PdfGenerationResult.class);
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

    public Optional<String> generateStripeConnectStateParam(int organizationId) {
        return Optional.ofNullable(extensionService.executeScriptsForEvent(ExtensionEvent.STRIPE_CONNECT_STATE_GENERATION.name(),
            "-" + organizationId,
            Map.of("baseUrl", configurationManager.getFor(ConfigurationKeys.BASE_URL, ConfigurationLevel.organization(organizationId)).getRequiredValue(), "organizationId", organizationId),
            String.class));
    }


    private void asyncCall(ExtensionEvent extensionEvent, Event event, int organizationId, Map<String, Object> payload) {
        extensionService.executeScriptAsync(extensionEvent.name(),
            toPath(organizationId, event.getId()),
            fillWithBasicInfo(payload, event, organizationId));
    }

    private <T> T syncCall(ExtensionEvent extensionEvent, Event event, int organizationId, Map<String, Object> payload, Class<T> clazz) {
        return extensionService.executeScriptsForEvent(extensionEvent.name(),
            toPath(organizationId, event.getId()),
            fillWithBasicInfo(payload, event, organizationId),
            clazz);
    }

    private Map<String, Object> fillWithBasicInfo(Map<String, Object> payload, Event event, int organizationId) {
        Map<String, Object> payloadCopy = new HashMap<>(payload);
        payloadCopy.put("event", event);
        payloadCopy.put("eventId", event.getId());
        payloadCopy.put("organizationId", organizationId);
        return payloadCopy;
    }


    public static String toPath(int organizationId, int eventId) {
        return "-" + organizationId + "-" + eventId;
    }

}
