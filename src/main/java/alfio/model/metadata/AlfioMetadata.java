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
package alfio.model.metadata;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class AlfioMetadata {
    private final List<String> tags;
    private final Map<String, Object> attributes; //for supporting nested attribute? bah..
    private final OnlineConfiguration onlineConfiguration;
    // list of requirements for participants, e.g. software
    private final Map<String, String> requirementsDescriptions;
    private final List<ConditionsLink> conditionsToBeAccepted;

    @JsonCreator
    public AlfioMetadata(@JsonProperty("tags") List<String> tags,
                         @JsonProperty("onlineConfiguration") OnlineConfiguration onlineConfiguration,
                         @JsonProperty("requirementsDescriptions") Map<String, String> requirementsDescriptions,
                         @JsonProperty("conditionsToBeAccepted") List<ConditionsLink> conditionsToBeAccepted,
                         @JsonProperty("attributes") Map<String, Object> attributes
        ) {
        this.tags = tags;
        this.attributes = attributes;
        this.onlineConfiguration = onlineConfiguration;
        this.requirementsDescriptions = requirementsDescriptions;
        this.conditionsToBeAccepted = conditionsToBeAccepted;
    }

    public static AlfioMetadata empty() {
        return new AlfioMetadata(List.of(), null, Map.of(), List.of(), Map.of());
    }
}
