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
import alfio.model.Event;
import alfio.model.PurchaseContext;
import alfio.model.support.JSONData;
import alfio.util.Json;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

import java.time.ZonedDateTime;
import java.util.Collection;
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

    default int insert(String reservationId, Integer userId, PurchaseContext p, Audit.EventType eventType, Date eventTime, Audit.EntityType entityType,
                       String entityId) {
        var eventId = p.event().map(Event::getId).orElse(null);
        return this.insert(reservationId, userId, eventId, eventType, eventTime, entityType, entityId, (String) null);
    }

    default int insert(String reservationId, Integer userId, PurchaseContext p, Audit.EventType eventType, Date eventTime, Audit.EntityType entityType,
                       String entityId, List<Map<String, Object>> modifications) {
        var eventId = p.event().map(Event::getId).orElse(null);
        return insert(reservationId, userId, eventId, eventType, eventTime, entityType, entityId, modifications);
    }


    @Query("select * from auditing_user where reservation_id = :reservationId order by event_time asc")
    List<Audit> findAllForReservation(@Bind("reservationId") String reservationId);

    @Query("select count(*) from auditing_user where reservation_id = :reservationId and event_type = :eventType")
    Integer countAuditsOfTypeForReservation(@Bind("reservationId") String reservationId, @Bind("eventType") Audit.EventType eventType);

    @Query("select count(*) from auditing_user where reservation_id = :reservationId and entity_id = :ticketId::text and event_type = :eventType")
    Integer countAuditsOfTypeForTicket(@Bind("reservationId") String reservationId,
                                       @Bind("ticketId") int ticketId,
                                       @Bind("eventType") Audit.EventType eventType);

    @Query("select count(*) from auditing_user where reservation_id = :reservationId and event_type in (:eventTypes) and date_trunc('day', :referenceDate::timestamp) = date_trunc('day', event_time)")
    Integer countAuditsOfTypesInTheSameDay(@Bind("reservationId") String reservationId, @Bind("eventTypes") Collection<String> eventTypes, @Bind("referenceDate") ZonedDateTime date);

    @Query("insert into auditing(reservation_id, user_id, event_id, event_type, event_time, entity_type, entity_id, modifications) " +
        " select tickets_reservation_id, null, event_id, 'UPDATE_TICKET_CATEGORY', current_timestamp, 'TICKET', concat('', id), null from ticket where category_id = :ticketCategoryId and tickets_reservation_id is not null")
    int insertUpdateTicketInCategoryId(@Bind("ticketCategoryId") int id);

    @Query("insert into auditing(reservation_id, user_id, event_id, event_type, event_time, entity_type, entity_id, modifications) " +
        " select tickets_reservation_id, null, event_id, 'TAG_TICKET', current_timestamp, 'TICKET', concat('', id), :modifications from ticket where id in (:ticketIds)")
    int registerTicketTag(@Bind("ticketIds") List<Integer> ids, @Bind("modifications")  @JSONData List<Map<String, Object>> modifications);

    @Query("insert into auditing(reservation_id, user_id, event_id, event_type, event_time, entity_type, entity_id, modifications) " +
        " select tickets_reservation_id, null, event_id, 'UNTAG_TICKET', current_timestamp, 'TICKET', concat('', id), :modifications from ticket where id in (:ticketIds)")
    int registerTicketUntag(@Bind("ticketIds") List<Integer> ids, @Bind("modifications") @JSONData List<Map<String, Object>> modifications);
}
