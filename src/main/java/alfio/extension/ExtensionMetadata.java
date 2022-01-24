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

package alfio.extension;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

import static java.util.Objects.requireNonNullElse;

@Getter
public class ExtensionMetadata {
    private final String id;
    private final String displayName;
    private final Integer version;
    private final boolean async;
    private final List<String> events;
    private final Parameters parameters;
    private final List<String> capabilities;
    private final List<CapabilityDetail> capabilityDetails;

    @JsonCreator
    public ExtensionMetadata(@JsonProperty("id") String id,
                             @JsonProperty("displayName") String displayName,
                             @JsonProperty("version") Integer version,
                             @JsonProperty("async") boolean async,
                             @JsonProperty("events") List<String> events,
                             @JsonProperty("parameters") Parameters parameters,
                             @JsonProperty("capabilities") List<String> capabilities,
                             @JsonProperty("capabilityDetails") List<CapabilityDetail> capabilityDetails) {
        this.id = id;
        this.displayName = displayName;
        this.version = version;
        this.async = async;
        this.events = requireNonNullElse(events, List.of());
        this.parameters = parameters;
        this.capabilities = requireNonNullElse(capabilities, List.of());
        this.capabilityDetails = requireNonNullElse(capabilityDetails, List.of());
    }

    @Getter
    public static class Parameters {
        private final List<Field> fields;
        private final List<String> configurationLevels;

        @JsonCreator
        public Parameters(@JsonProperty("fields") List<Field> fields,
                          @JsonProperty("configurationLevels") List<String> configurationLevels) {
            this.fields = fields;
            this.configurationLevels = configurationLevels;
        }
    }


    @Getter
    public static class Field {
        private final String name;
        private final String description;
        private final String type;
        private final boolean required;

        @JsonCreator
        public Field(@JsonProperty("name") String name,
                     @JsonProperty("description") String description,
                     @JsonProperty("type") String type,
                     @JsonProperty("required") boolean required) {
            this.name = name;
            this.description = description;
            this.type = type;
            this.required = required;
        }
    }

    @Getter
    public static class CapabilityDetail {
        private final String key;
        private final String label;
        private final String description;
        private final String selector;

        @JsonCreator
        public CapabilityDetail(@JsonProperty("key") String key,
                                @JsonProperty("label") String label,
                                @JsonProperty("description") String description,
                                @JsonProperty("selector") String selector) {
            this.key = key;
            this.label = label;
            this.description = description;
            this.selector = selector;
        }
    }
}
