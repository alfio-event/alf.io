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
package alfio.model;

import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
public class SponsorScan {

    public enum LeadStatus {
        COLD, WARM, HOT
    }

    private final int userId;
    private final ZonedDateTime timestamp;
    private final int eventId;
    private final int ticketId;
    private final String notes;
    private final LeadStatus leadStatus;
    private final String operator;


    public SponsorScan(@Column("user_id") int userId,
                       @Column("creation") ZonedDateTime timestamp,
                       @Column("event_id") int eventId,
                       @Column("ticket_id") int ticketId,
                       @Column("notes") String notes,
                       @Column("lead_status") LeadStatus leadStatus,
                       @Column("operator") String operator) {
        this.userId = userId;
        this.timestamp = timestamp;
        this.eventId = eventId;
        this.ticketId = ticketId;
        this.notes = notes;
        this.leadStatus = leadStatus;
        this.operator = operator;
    }
}
