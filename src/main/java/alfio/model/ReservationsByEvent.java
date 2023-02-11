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

import alfio.model.support.ReservationInfo;
import alfio.util.Json;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;

public class ReservationsByEvent {

    private final int eventId;
    private final String eventShortName;
    private final String displayName;
    private final List<ReservationInfo> reservations;

    public ReservationsByEvent(@Column("event_id") int eventId,
                               @Column("event_short_name") String eventShortName,
                               @Column("event_display_name") String displayName,
                               @Column("reservations") String reservations) {
        this.eventId = eventId;
        this.eventShortName = eventShortName;
        this.displayName = displayName;
        this.reservations = Json.fromJson(reservations, new TypeReference<>() {});
    }

    public int getEventId() {
        return eventId;
    }

    public String getEventShortName() {
        return eventShortName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<ReservationInfo> getReservations() {
        return reservations;
    }
}
