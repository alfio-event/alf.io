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
package alfio.model;

import alfio.manager.support.extension.ExtensionCapability;
import alfio.model.support.EnumTypeAsString;
import alfio.model.support.JSONData;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;
import java.util.Objects;

@Getter
public class ExtensionCapabilitySummary {

    private final ExtensionCapability capability;
    private final List<ExtensionCapabilityDetails> details;

    public ExtensionCapabilitySummary(@Column("capability") @EnumTypeAsString ExtensionCapability capability,
                                      @Column("capability_detail") @JSONData List<ExtensionCapabilityDetails> details) {
        this.capability = capability;
        this.details = details;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExtensionCapabilitySummary that = (ExtensionCapabilitySummary) o;
        return capability == that.capability && Objects.equals(details, that.details);
    }

    @Override
    public int hashCode() {
        return Objects.hash(capability, details);
    }

    @Getter
    public static class ExtensionCapabilityDetails {
        private final String label;
        private final String description;
        private final String selector;

        @JsonCreator
        public ExtensionCapabilityDetails(@JsonProperty("label") String label,
                                          @JsonProperty("description") String description,
                                          @JsonProperty("selector") String selector) {
            this.label = label;
            this.description = description;
            this.selector = selector;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ExtensionCapabilityDetails that = (ExtensionCapabilityDetails) o;
            return label.equals(that.label)
                && Objects.equals(description, that.description)
                && Objects.equals(selector, that.selector);
        }

        @Override
        public int hashCode() {
            return Objects.hash(label, description, selector);
        }
    }
}
