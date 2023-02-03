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
package alfio.model.api.v1.admin;

import alfio.model.audit.ScanAudit;
import alfio.model.modification.AttendeeData;
import alfio.model.support.JSONData;
import alfio.util.Json;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;

public class CheckInLogEntry {
    private final String ticketId;
    private final AttendeeData attendeeData;
    private final List<ScanAudit> audit;

    public CheckInLogEntry(@Column("t_uuid") String ticketId,
                           @Column("attendee_data") @JSONData AttendeeData attendeeData,
                           @Column("scans") String scansAsString) {
        this.ticketId = ticketId;
        this.attendeeData = attendeeData;
        this.audit = Json.fromJson(scansAsString, new TypeReference<>() {});
    }

    public String getTicketId() {
        return ticketId;
    }

    public AttendeeData getAttendeeData() {
        return attendeeData;
    }

    public List<ScanAudit> getAudit() {
        return audit;
    }
}
