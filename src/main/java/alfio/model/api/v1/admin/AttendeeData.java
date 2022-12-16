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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

class AttendeeData {
    private final String firstName;
    private final String lastName;
    private final String email;
    private final Map<String, String> metadata;

    @JsonCreator
    AttendeeData(@JsonProperty("firstName") String firstName,
                 @JsonProperty("lastName") String lastName,
                 @JsonProperty("email") String email,
                 @JsonProperty("metadata") Map<String, String> metadata) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.metadata = metadata;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }
}
