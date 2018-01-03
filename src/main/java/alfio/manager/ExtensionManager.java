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

import alfio.model.Ticket;
import alfio.model.TicketReservation;
import alfio.model.WaitingQueueSubscription;
import alfio.repository.EventRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.scripting.ScriptingService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
@AllArgsConstructor
public class ExtensionManager {

    private final ScriptingService scriptingService;
    private final EventRepository eventRepository;
    private final TicketCategoryRepository ticketCategoryRepository;

    public enum ExtensionEvent {
        RESERVATION_CONFIRMATION,
        TICKET_ASSIGNMENT,
        WAITING_QUEUE_SUBSCRIPTION,
        INVOICE_GENERATION
    }


    public void handleReservationConfirmation(TicketReservation reservation, int eventId) {
        int organizationId = eventRepository.findOrganizationIdByEventId(eventId);

        asyncCall(ExtensionEvent.RESERVATION_CONFIRMATION,
            eventId,
            organizationId,
            Collections.singletonMap("reservation", reservation));
    }

    public void handleTicketAssignment(Ticket ticket) {
        int eventId = ticket.getEventId();
        int organizationId = eventRepository.findOrganizationIdByEventId(eventId);

        asyncCall(ExtensionEvent.TICKET_ASSIGNMENT,
            eventId,
            organizationId,
            Collections.singletonMap("ticket", ticket));
    }

    public void handleWaitingQueueSubscription(WaitingQueueSubscription waitingQueueSubscription) {
        int organizationId = eventRepository.findOrganizationIdByEventId(waitingQueueSubscription.getEventId());

        asyncCall(ExtensionEvent.WAITING_QUEUE_SUBSCRIPTION,
            waitingQueueSubscription.getEventId(),
            organizationId,
            Collections.singletonMap("waitingQueueSubscription", waitingQueueSubscription));
    }

    private void asyncCall(ExtensionEvent event, int eventId, int organizationId, Map<String, Object> payload) {
        Map<String, Object> payloadCopy = new HashMap<>(payload);
        payloadCopy.put("eventId", eventId);
        payloadCopy.put("organizationId", organizationId);

        scriptingService.executeScriptAsync(event.name(),
            toPath(organizationId, eventId), payload);
    }


    public static String toPath(int organizationId, int eventId) {
        return organizationId + "/" + eventId;
    }

}
