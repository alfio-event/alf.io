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

import alfio.model.metadata.AlfioMetadata;
import alfio.model.metadata.JoinLink;
import alfio.model.metadata.OnlineConfiguration;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
public class MetadataModification {

    private final List<CallLinkModification> callLinks;
    private final Map<String, String> requirementsDescriptions;

    @JsonCreator
    public MetadataModification(@JsonProperty("callLinks") List<CallLinkModification> callLinks,
                                @JsonProperty("requirementDescriptions") Map<String, String> requirementsDescriptions) {
        this.callLinks = callLinks;
        this.requirementsDescriptions = requirementsDescriptions;
    }

    public boolean isValid() {
        return callLinks != null && !callLinks.isEmpty() && callLinks.stream().allMatch(CallLinkModification::isValid);
    }

    public AlfioMetadata toMetadataObj() {
        return new AlfioMetadata(
            new OnlineConfiguration(callLinks.stream().map(CallLinkModification::toCallLink).collect(Collectors.toList())),
            requirementsDescriptions,
            List.of(),
            null,
            Map.of());
    }

    @Getter
    public static class CallLinkModification {
        private final String link;
        private final DateTimeModification validFrom;
        private final DateTimeModification validTo;


        @JsonCreator
        public CallLinkModification(@JsonProperty("link") String link,
                                    @JsonProperty("validFrom") DateTimeModification validFrom,
                                    @JsonProperty("validTo") DateTimeModification validTo) {
            this.link = link;
            this.validFrom = validFrom;
            this.validTo = validTo;
        }

        public boolean isValid() {
            return validFrom != null && validTo != null && validFrom.isBefore(validTo);
        }

        public JoinLink toCallLink() {
            return new JoinLink(link, validFrom.toLocalDateTime(), validTo.toLocalDateTime(), null);
        }
    }
}
