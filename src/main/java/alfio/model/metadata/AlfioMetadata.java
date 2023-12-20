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
    private static final AlfioMetadata EMPTY = new AlfioMetadata(null, Map.of(), List.of(), null, Map.of());
    private final OnlineConfiguration onlineConfiguration;
    // list of requirements for participants, e.g. software
    private final Map<String, String> requirementsDescriptions;
    private final List<ConditionsLink> conditionsToBeAccepted;
    private final String copiedFrom;
    private final Map<String, String> applicationData;

    @JsonCreator
    public AlfioMetadata(@JsonProperty("onlineConfiguration") OnlineConfiguration onlineConfiguration,
                         @JsonProperty("requirementsDescriptions") Map<String, String> requirementsDescriptions,
                         @JsonProperty("conditionsToBeAccepted") List<ConditionsLink> conditionsToBeAccepted,
                         @JsonProperty("copiedFrom") String copiedFrom,
                         @JsonProperty("applicationData") Map<String, String> applicationData) {
        this.onlineConfiguration = onlineConfiguration;
        this.requirementsDescriptions = requirementsDescriptions;
        this.conditionsToBeAccepted = conditionsToBeAccepted;
        this.copiedFrom = copiedFrom;
        this.applicationData = applicationData;
    }

    public static AlfioMetadata empty() {
        return EMPTY;
    }

    /**
     * Returns a merged version of the metadata.
     * The "copiedFrom" attribute will not be overridden by {@code other}
     * @param other metadata to merge
     * @return merged metadata
     */
    public AlfioMetadata merge(AlfioMetadata other) {
        return new AlfioMetadata(
            other.onlineConfiguration,
            other.requirementsDescriptions,
            other.conditionsToBeAccepted,
            copiedFrom, applicationData);
    }
}
