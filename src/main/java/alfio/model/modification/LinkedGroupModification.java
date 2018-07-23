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

import alfio.model.group.LinkedGroup;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class LinkedGroupModification {
    private final Integer id;
    private final int groupId;
    private final int eventId;
    private final Integer ticketCategoryId;
    private final LinkedGroup.Type type;
    private final LinkedGroup.MatchType matchType;
    private final Integer maxAllocation;

    @JsonCreator
    public LinkedGroupModification(@JsonProperty("id") Integer id,
                                   @JsonProperty("groupId") int groupId,
                                   @JsonProperty("eventId") int eventId,
                                   @JsonProperty("ticketCategoryId") Integer ticketCategoryId,
                                   @JsonProperty("type") LinkedGroup.Type type,
                                   @JsonProperty("matchType") LinkedGroup.MatchType matchType,
                                   @JsonProperty("maxAllocation") Integer maxAllocation) {
        this.id = id;
        this.groupId = groupId;
        this.eventId = eventId;
        this.ticketCategoryId = ticketCategoryId;
        this.type = type;
        this.matchType = matchType;
        this.maxAllocation = maxAllocation;
    }
}
