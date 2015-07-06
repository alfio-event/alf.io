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
package alfio.model.system;

import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
public class EventMigration {

    public enum Status {
        WAITING, PENDING, COMPLETE
    }

    private final int id;
    private final int eventId;
    private final String currentVersion;
    private final ZonedDateTime buildTimestamp;
    private final Status status;

    public EventMigration(@Column("id") int id,
                          @Column("event_id") int eventId,
                          @Column("current_version") String currentVersion,
                          @Column("build_ts") ZonedDateTime buildTimestamp,
                          @Column("status") String status) {
        this.id = id;
        this.eventId = eventId;
        this.currentVersion = currentVersion;
        this.buildTimestamp = buildTimestamp;
        this.status = Status.valueOf(status);
    }
}
