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
package alfio.repository;


import alfio.model.Audit;
import alfio.util.Json;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

import java.util.Date;
import java.util.List;
import java.util.Map;

@QueryRepository
public interface AuditingRepository {

    @Query("insert into auditing(reservation_id, user_id, event_id, event_type, event_time, entity_type, entity_id, modifications) " +
        " values (:reservationId, :userId, :eventId, :eventType, :eventTime, :entityType, :entityId, :modifications)")
    int insert(@Bind("reservationId") String reservationId, @Bind("userId") Integer userId,
               @Bind("eventId") Integer eventId,
               @Bind("eventType") Audit.EventType eventType, @Bind("eventTime") Date eventTime,
               @Bind("entityType") Audit.EntityType entityType, @Bind("entityId") String entityId,
               @Bind("modifications") String modifications);


    default int insert(String reservationId, Integer userId, Integer eventId, Audit.EventType eventType, Date eventTime, Audit.EntityType entityType,
                       String entityId) {
        return this.insert(reservationId, userId, eventId, eventType, eventTime, entityType, entityId, (String) null);
    }

    default int insert(String reservationId, Integer userId, Integer eventId, Audit.EventType eventType, Date eventTime, Audit.EntityType entityType,
                       String entityId, List<Map<String, Object>> modifications) {
        String modificationJson = modifications == null ? null : Json.toJson(modifications);
        return this.insert(reservationId, userId, eventId, eventType, eventTime, entityType, entityId, modificationJson);
    }


    @Query("select * from auditing_user where reservation_id = :reservationId order by event_time asc")
    List<Audit> findAllForReservation(@Bind("reservationId") String reservationId);



    @Query("insert into auditing(reservation_id, user_id, event_id, event_type, event_time, entity_type, entity_id, modifications) " +
        " select tickets_reservation_id, null, event_id, 'UPDATE_TICKET_CATEGORY', current_timestamp, 'TICKET', concat('', id), null from ticket where category_id = :ticketCategoryId and tickets_reservation_id is not null")
    int insertUpdateTicketInCategoryId(@Bind("ticketCategoryId") int id);
}
