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

import alfio.model.Audit;
import alfio.model.Ticket;
import alfio.model.metadata.TicketMetadataContainer;
import alfio.repository.AuditingRepository;
import alfio.util.ObjectDiffUtil;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static alfio.model.Audit.EntityType.TICKET;

public class ReservationAuditingHelper {

    private final AuditingRepository auditingRepository;

    public ReservationAuditingHelper(AuditingRepository auditingRepository) {
        this.auditingRepository = auditingRepository;
    }

    public void auditUpdateMetadata(String reservationId,
                                     int ticketId,
                                     int eventId,
                                     TicketMetadataContainer newMetadata,
                                     TicketMetadataContainer oldMetadata) {
        List<Map<String, Object>> changes = ObjectDiffUtil.diff(oldMetadata, newMetadata, TicketMetadataContainer.class).stream()
            .map(this::processChange)
            .collect(Collectors.toList());

        auditingRepository.insert(reservationId, null, eventId, Audit.EventType.UPDATE_TICKET_METADATA, new Date(),
            TICKET, Integer.toString(ticketId), changes);
    }

    public void auditUpdateTicket(Ticket preUpdateTicket, Map<String, List<String>> preUpdateTicketFields, Ticket postUpdateTicket, Map<String, List<String>> postUpdateTicketFields, int eventId) {
        List<ObjectDiffUtil.Change> diffTicket = ObjectDiffUtil.diff(preUpdateTicket, postUpdateTicket);
        List<ObjectDiffUtil.Change> diffTicketFields = ObjectDiffUtil.diff(preUpdateTicketFields, postUpdateTicketFields);

        List<Map<String, Object>> changes = Stream.concat(diffTicket.stream(), diffTicketFields.stream())
            .map(this::processChange)
            .collect(Collectors.toList());

        auditingRepository.insert(preUpdateTicket.getTicketsReservationId(), null, eventId,
            Audit.EventType.UPDATE_TICKET, new Date(), TICKET, Integer.toString(preUpdateTicket.getId()), changes);
    }

    private HashMap<String, Object> processChange(ObjectDiffUtil.Change change) {
        var v = new HashMap<String, Object>();
        v.put("propertyName", change.getPropertyName());
        v.put("state", change.getState());
        v.put("oldValue", change.getOldValue());
        v.put("newValue", change.getNewValue());
        return v;
    }
}
