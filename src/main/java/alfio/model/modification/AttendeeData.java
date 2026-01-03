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

import alfio.model.CustomerName;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

public class AttendeeData {
    private final String firstName;
    private final String lastName;
    private final String email;
    private final Map<String, String> metadata;
    private final Map<String, List<String>> additional;
    private final String externalReference;
    private final AttendeeResources resources;

    @JsonCreator
    public AttendeeData(@JsonProperty("firstName") String firstName,
                        @JsonProperty("lastName") String lastName,
                        @JsonProperty("email") String email,
                        @JsonProperty("externalReference") String externalReference,
                        @JsonProperty("metadata") Map<String, String> metadata,
                        @JsonProperty("additional") Map<String, List<String>> additional) {
        this(firstName, lastName, email, externalReference, metadata, additional, AttendeeResources.empty());
    }

    public AttendeeData(String firstName,
                        String lastName,
                        String email,
                        String externalReference,
                        Map<String, String> metadata,
                        Map<String, List<String>> additional,
                        AttendeeResources resources) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.externalReference = externalReference;
        this.metadata = metadata;
        this.additional = additional;
        this.resources = resources;
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

    public String getFullName() {
        if (firstName != null && lastName != null) {
            return new CustomerName(null, firstName, lastName, true).getFullName();
        }
        return null;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public Map<String, List<String>> getAdditional() {
        return additional;
    }

    public boolean hasMetadata() {
        return metadata != null;
    }

    public boolean hasAdditionalData() {
        return additional != null;
    }

    public boolean hasContactData() {
        return StringUtils.isNotBlank(firstName)
            || StringUtils.isNotBlank(lastName)
            || StringUtils.isNotBlank(email);
    }

    public AttendeeResources getResources() {
        return resources;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public static AttendeeData empty() {
        return new AttendeeData(null, null, null, null, null, null);
    }
}
