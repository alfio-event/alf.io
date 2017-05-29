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
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

import java.util.Date;

@QueryRepository
public interface AuditingRepository {

    @Query("insert into auditing(reservation_id, user_id, event_type, event_time, entity_type, entity_id, modifications) " +
        " values (:reservationId, :userId, :eventType, :eventTime, :entityType, :entityId, :modifications)")
    int insert(@Bind("reservationId") String reservationId, @Bind("userId") Integer userId,
               @Bind("eventType") Audit.EventType eventType, @Bind("eventTime")Date eventTime,
               @Bind("entityType") Audit.EntityType entityType, @Bind("entityId") String entityId,
               @Bind("modifications") String modifications);
}
