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
package alfio.repository.audit;

import alfio.manager.support.CheckInStatus;
import alfio.model.audit.ScanAudit;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

import java.time.ZonedDateTime;
import java.util.List;

@QueryRepository
public interface ScanAuditRepository {
    @Query("insert into scan_audit(ticket_uuid, event_id_fk, scan_ts, username, check_in_status, operation) values(:ticketUuid, :eventId, :scanTs, :username, :status, :operation)")
    Integer insert(@Bind("ticketUuid") String ticketUuid,
                   @Bind("eventId") int eventId,
                   @Bind("scanTs") ZonedDateTime timestamp,
                   @Bind("username") String username,
                   @Bind("status") CheckInStatus checkInStatus,
                   @Bind("operation") ScanAudit.Operation operation);

    @Query("select * from scan_audit where event_id_fk = :eventId")
    List<ScanAudit> findAllForEvent(@Bind("eventId") int eventId);

}