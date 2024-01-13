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
package alfio.model.modification;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static alfio.util.MiscUtils.getAtIndexOrNull;

public interface ReservationRequest {
    Integer getTicketCategoryId();
    Integer getQuantity();
    List<Map<String, String>> getMetadata();

    default List<AttendeeData> getAttendees() {
        return metadataToAttendeesList(getQuantity(), getMetadata());
    }

    static List<AttendeeData> metadataToAttendeesList(Integer quantity, List<Map<String, String>> metadata) {
        int q = Objects.requireNonNullElse(quantity, 0);
        return Stream.iterate(0, s -> s+1)
            .limit(q)
            .map(i -> new AttendeeData(null, null, null, getAtIndexOrNull(metadata, i), Map.of()))
            .collect(Collectors.toList());
    }
}
