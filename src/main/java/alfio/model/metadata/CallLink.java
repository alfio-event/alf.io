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

import java.time.LocalDateTime;

@Getter
public class CallLink {
    private final String link;
    private final LocalDateTime validFrom;
    private final LocalDateTime validTo;

    @JsonCreator
    public CallLink(@JsonProperty("link") String link,
                    @JsonProperty("validFrom") LocalDateTime validFrom,
                    @JsonProperty("validTo") LocalDateTime validTo) {
        this.link = link;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }
}
