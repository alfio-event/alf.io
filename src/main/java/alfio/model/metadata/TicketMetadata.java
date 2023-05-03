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

import alfio.model.LocalizedContent;
import alfio.util.EventUtil;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNullElse;

@Getter
public class TicketMetadata {

    /**
     * Alternative Link configuration. If present, it will override Alf.io's default join link.
     * This would allow seamless integration with external, invitation-based virtual conference systems
     */
    private final JoinLink joinLink;

    /**
     * Localized description for the custom join link.
     * E.g. {"en": "The online conference will take place on -insert-name-here-, please click on the link above to sign up"}
     */
    private final Map<String, String> linkDescription;

    /**
     * Additional attributes that might be relevant to the ticket
     */
    private final Map<String, String> attributes;

    @JsonCreator
    public TicketMetadata(@JsonProperty("joinLink") JoinLink joinLink,
                          @JsonProperty("joinMessages") Map<String, String> linkDescription,
                          @JsonProperty("attributes") Map<String, String> attributes) {
        this.joinLink = joinLink;
        this.attributes = requireNonNullElse(attributes, Map.of());
        this.linkDescription = requireNonNullElse(linkDescription, Map.of());
    }

    public String getLocalizedDescription(String lang, LocalizedContent fallback) {
        return EventUtil.getLocalizedMessage(linkDescription, lang, fallback);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TicketMetadata that = (TicketMetadata) o;
        return Objects.equals(joinLink, that.joinLink) && linkDescription.equals(that.linkDescription) && attributes.equals(that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(joinLink, linkDescription, attributes);
    }

    public static TicketMetadata empty() {
        return new TicketMetadata(null, null, Map.of());
    }
    public TicketMetadata withAttributes(Map<String, String> attributes) {
        return new TicketMetadata(joinLink, Map.copyOf(linkDescription), Map.copyOf(attributes));
    }

    public static TicketMetadata copyOf(TicketMetadata src) {
        if (src != null) {
            return new TicketMetadata(src.joinLink, Map.copyOf(src.linkDescription), Map.copyOf(src.attributes));
        }
        return null;
    }
}
