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
package alfio.model.api.v1.admin.subscription;

import alfio.controller.form.ReadOnlyAdditionalFieldsContainer;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.collections4.MapUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class Owner implements ReadOnlyAdditionalFieldsContainer {

    private final UUID subscriptionId;
    private final Map<String, List<String>> additional;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final Map<String, String> metadata;

    public Owner(@JsonProperty("additional") Map<String, List<String>> additional,
                 @JsonProperty("subscriptionId") UUID subscriptionId,
                 @JsonProperty("firstName") String firstName,
                 @JsonProperty("lastName") String lastName,
                 @JsonProperty("email") String email,
                 @JsonProperty("metadata") Map<String, String> metadata) {
        this.additional = additional;
        this.subscriptionId = subscriptionId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.metadata = Objects.requireNonNullElse(metadata, Map.of());
    }

    public Map<String, List<String>> getAdditional() {
        return additional;
    }

    public boolean hasAdditionalInfo() {
        return MapUtils.isNotEmpty(additional);
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
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

    public static Owner empty() {
        return new Owner(Map.of(), null, null, null, null, null);
    }
}