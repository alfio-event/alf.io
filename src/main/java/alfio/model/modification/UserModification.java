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

import alfio.model.user.User;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Getter
public class UserModification {

    private final Integer id;
    private final int organizationId;
    private final String role;
    private final String username;
    private final String firstName;
    private final String lastName;
    private final String emailAddress;
    private final User.Type type;
    private final Long validTo;
    private final String description;

    @JsonCreator
    public UserModification(@JsonProperty("id") Integer id,
                            @JsonProperty("organizationId") int organizationId,
                            @JsonProperty("role") String role,
                            @JsonProperty("username") String username,
                            @JsonProperty("firstName") String firstName,
                            @JsonProperty("lastName") String lastName,
                            @JsonProperty("emailAddress") String emailAddress,
                            @JsonProperty("type") User.Type type,
                            @JsonProperty("validTo") Long validTo,
                            @JsonProperty("description") String description) {
        this.id = id;
        this.organizationId = organizationId;
        this.role = role;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.emailAddress = emailAddress;
        this.type = type;
        this.validTo = validTo;
        this.description = description;
    }

    public ZonedDateTime getValidToAsDateTime() {
        return validTo == null ? null : ZonedDateTime.ofInstant(Instant.ofEpochSecond(validTo), ZoneId.of("UTC"));
    }
}
