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
import alfio.model.*;
import alfio.model.extension.InvoiceGeneration;
import alfio.repository.EventRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@AllArgsConstructor
public class ExtensionManager {

    private final ExtensionService extensionService;
    private final EventRepository eventRepository;

    public enum ExtensionEvent {
        RESERVATION_CONFIRMED,
        RESERVATION_CANCELLED,
        TICKET_CANCELLED,
        RESERVATION_EXPIRED,
        TICKET_ASSIGNED,
        WAITING_QUEUE_SUBSCRIBED,
        INVOICE_GENERATION,
        //
        STUCK_RESERVATIONS,
        OFFLINE_RESERVATIONS_WILL_EXPIRE,
        EVENT_CREATED,
        EVENT_STATUS_CHANGE,
        WEB_API_HOOK,
        TICKET_CHECKED_IN,
        TICKET_REVERT_CHECKED_IN
    }

    public void handleEventCreation(Event event) {
        Map<String, Object> payload = Collections.singletonMap("event", event);
        syncCall(ExtensionEvent.EVENT_CREATED, event.getId(), event.getOrganizationId(), payload, Boolean.class);
        asyncCall(ExtensionEvent.EVENT_CREATED, event.getId(), event.getOrganizationId(), payload);
    }

    public void handleEventStatusChange(Event event, Event.Status status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", event);
        payload.put("status", status.name());
        syncCall(ExtensionEvent.EVENT_STATUS_CHANGE, event.getId(), event.getOrganizationId(), payload, Boolean.class);
        asyncCall(ExtensionEvent.EVENT_STATUS_CHANGE, event.getId(), event.getOrganizationId(), payload);
    }

    public void handleReservationConfirmation(TicketReservation reservation, int eventId) {
        int organizationId = eventRepository.findOrganizationIdByEventId(eventId);

        asyncCall(ExtensionEvent.RESERVATION_CONFIRMED,
            eventId,
            organizationId,
            Collections.singletonMap("reservation", reservation));
    }

    public void handleTicketAssignment(Ticket ticket) {
        int eventId = ticket.getEventId();
        int organizationId = eventRepository.findOrganizationIdByEventId(eventId);

        asyncCall(ExtensionEvent.TICKET_ASSIGNED,
            eventId,
            organizationId,
            Collections.singletonMap("ticket", ticket));
    }

    public void handleWaitingQueueSubscription(WaitingQueueSubscription waitingQueueSubscription) {
        int organizationId = eventRepository.findOrganizationIdByEventId(waitingQueueSubscription.getEventId());

        asyncCall(ExtensionEvent.WAITING_QUEUE_SUBSCRIBED,
            waitingQueueSubscription.getEventId(),
            organizationId,
            Collections.singletonMap("waitingQueueSubscription", waitingQueueSubscription));
    }

    public void handleReservationsExpiredForEvent(Event event, Collection<String> reservationIdsToRemove) {
        handleReservationRemoval(event, reservationIdsToRemove, ExtensionEvent.RESERVATION_EXPIRED);
    }

    public void handleReservationsCancelledForEvent(Event event, Collection<String> reservationIdsToRemove) {
        handleReservationRemoval(event, reservationIdsToRemove, ExtensionEvent.RESERVATION_CANCELLED);
    }

    public void handleTicketCancelledForEvent(Event event, Collection<String> ticketUUIDs) {
        int organizationId = event.getOrganizationId();
        int eventId = event.getId();

        Map<String, Object> payload = new HashMap<>();
        payload.put("event", event);
        payload.put("ticketUUIDs", ticketUUIDs);

        syncCall(ExtensionEvent.TICKET_CANCELLED, eventId, organizationId, payload, Boolean.class);
    }

    public void handleOfflineReservationsWillExpire(Event event, List<TicketReservationInfo> reservations) {
        int organizationId = eventRepository.findOrganizationIdByEventId(event.getOrganizationId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", event);
        payload.put("reservations", reservations);
        asyncCall(ExtensionEvent.OFFLINE_RESERVATIONS_WILL_EXPIRE, event.getId(), organizationId, payload);
    }

    public void handleStuckReservations(Event event, List<String> stuckReservationsId) {
        int organizationId = event.getOrganizationId();
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", event);
        payload.put("reservationIds", stuckReservationsId);
        asyncCall(ExtensionEvent.STUCK_RESERVATIONS, event.getId(), organizationId, payload);
    }

    private void handleReservationRemoval(Event event, Collection<String> reservationIds, ExtensionEvent extensionEvent) {
        int organizationId = event.getOrganizationId();
        int eventId = event.getId();

        Map<String, Object> payload = new HashMap<>();
        payload.put("event", event);
        payload.put("reservationIds", reservationIds);

        syncCall(extensionEvent, eventId, organizationId, payload, Boolean.class);
    }

    public Optional<InvoiceGeneration> handleInvoiceGeneration(Event event, String reservationId, String email, CustomerName customerName, Locale userLanguage,
                                                     String billingAddress, TotalPrice reservationCost, boolean invoiceRequested,
                                                     String vatCountryCode, String vatNr, PriceContainer.VatStatus vatStatus) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", event);
        payload.put("reservationId", reservationId);
        payload.put("email", email);
        payload.put("customerName", customerName);
        payload.put("userLanguage", userLanguage);
        payload.put("billingAddress", billingAddress);
        payload.put("reservationCost", reservationCost);
        payload.put("invoiceRequested", invoiceRequested);
        payload.put("vatCountryCode", vatCountryCode);
        payload.put("vatNr", vatNr);
        payload.put("vatStatus", vatStatus);

        return Optional.ofNullable(syncCall(ExtensionEvent.INVOICE_GENERATION, event.getId(), event.getOrganizationId(), payload, InvoiceGeneration.class));
    }

    public void handleTicketCheckedIn(Ticket ticket) {
        Map<String, Object> payload = new HashMap<>();
        Event event = eventRepository.findById(ticket.getEventId());
        payload.put("ticket", ticket);
        payload.put("event", event);
        asyncCall(ExtensionEvent.TICKET_CHECKED_IN, ticket.getEventId(), event.getOrganizationId(), payload);
    }

    public void handleTicketRevertCheckedIn(Ticket ticket) {
        Map<String, Object> payload = new HashMap<>();
        Event event = eventRepository.findById(ticket.getEventId());
        payload.put("ticket", ticket);
        payload.put("event", event);
        asyncCall(ExtensionEvent.TICKET_REVERT_CHECKED_IN, ticket.getEventId(), event.getOrganizationId(), payload);
    }


    private void asyncCall(ExtensionEvent event, int eventId, int organizationId, Map<String, Object> payload) {
        Map<String, Object> payloadCopy = new HashMap<>(payload);
        payloadCopy.put("eventId", eventId);
        payloadCopy.put("organizationId", organizationId);

        extensionService.executeScriptAsync(event.name(),
            toPath(organizationId, eventId), payload);
    }

    private <T> T syncCall(ExtensionEvent event, int eventId, int organizationId, Map<String, Object> payload, Class<T> clazz) {
        Map<String, Object> payloadCopy = new HashMap<>(payload);
        payloadCopy.put("eventId", eventId);
        payloadCopy.put("organizationId", organizationId);
        return extensionService.executeScriptsForEvent(event.name(), toPath(eventId, organizationId), payload, clazz);
    }


    public static String toPath(int organizationId, int eventId) {
        return "-" + organizationId + "-" + eventId;
    }

}
