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
package alfio.model.support;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TicketInfo {
    private final String id;
    private final String firstName;
    private final String lastName;
    private final String type;
    private final String status;

    @JsonCreator
    private TicketInfo(@JsonProperty("id") String id,
                       @JsonProperty("firstName") String firstName,
                       @JsonProperty("lastName") String lastName,
                       @JsonProperty("type") String type,
                       @JsonProperty("status") String status) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.type = type;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getType() {
        return type;
    }

    public String getStatus() {
        return status;
    }
}
