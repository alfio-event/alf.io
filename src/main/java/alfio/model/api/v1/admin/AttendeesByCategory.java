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

import alfio.model.modification.AttendeeData;
import alfio.model.modification.ReservationRequest;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class AttendeesByCategory implements ReservationRequest {

    private final Integer ticketCategoryId;
    private final Integer quantity;
    private final List<AttendeeData> attendees;
    // only for backwards compatibility
    private final List<Map<String, String>> metadata;

    @JsonCreator
    public AttendeesByCategory(@JsonProperty("ticketCategoryId") Integer ticketCategoryId,
                               @JsonProperty("quantity") Integer quantity,
                               @JsonProperty("attendees") List<AttendeeData> attendees,
                               @JsonProperty("metadata") List<Map<String, String>> metadata) {
        this.ticketCategoryId = ticketCategoryId;
        this.quantity = quantity;
        this.attendees = attendees;
        this.metadata = metadata;
    }

    @Override
    public Integer getTicketCategoryId() {
        return ticketCategoryId;
    }

    @Override
    public Integer getQuantity() {
        return quantity;
    }

    @Override
    public List<AttendeeData> getAttendees() {
        if (attendees != null && !attendees.isEmpty()) {
            return attendees;
        }
        return ReservationRequest.metadataToAttendeesList(getQuantity(), metadata);
    }

    @Override
    public List<Map<String, String>> getMetadata() {
        return metadata;
    }
}
