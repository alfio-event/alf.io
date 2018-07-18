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

import alfio.model.whitelist.WhitelistConfiguration;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class WhitelistConfigurationModification {
    private final Integer id;
    private final int whitelistId;
    private final int eventId;
    private final Integer ticketCategoryId;
    private final WhitelistConfiguration.Type type;
    private final WhitelistConfiguration.MatchType matchType;
    private final Integer maxAllocation;

    @JsonCreator
    public WhitelistConfigurationModification(@JsonProperty("id") Integer id,
                                              @JsonProperty("whitelistId") int whitelistId,
                                              @JsonProperty("eventId") int eventId,
                                              @JsonProperty("ticketCategoryId") Integer ticketCategoryId,
                                              @JsonProperty("type") WhitelistConfiguration.Type type,
                                              @JsonProperty("matchType") WhitelistConfiguration.MatchType matchType,
                                              @JsonProperty("maxAllocation") Integer maxAllocation) {
        this.id = id;
        this.whitelistId = whitelistId;
        this.eventId = eventId;
        this.ticketCategoryId = ticketCategoryId;
        this.type = type;
        this.matchType = matchType;
        this.maxAllocation = maxAllocation;
    }
}
