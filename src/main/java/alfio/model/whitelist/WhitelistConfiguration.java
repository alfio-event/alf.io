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
package alfio.model.whitelist;

import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

@Getter
public class WhitelistConfiguration {


    public enum Type {
        ONCE_PER_VALUE, LIMITED_QUANTITY, UNLIMITED
    }

    public enum MatchType {
        FULL, EMAIL_DOMAIN
    }

    private final int id;
    private final int whitelistId;
    private final Integer eventId;
    private final Integer ticketCategoryId;
    private final Type type;
    private final MatchType matchType;
    private final Integer maxAllocation;

    public WhitelistConfiguration(@Column("id") int id,
                                  @Column("whitelist_id_fk") int whitelistId,
                                  @Column("event_id_fk") Integer eventId,
                                  @Column("ticket_category_id_fk") Integer ticketCategoryId,
                                  @Column("type") Type type,
                                  @Column("match_type") MatchType matchType,
                                  @Column("max_allocation") Integer maxAllocation) {
        this.id = id;
        this.whitelistId = whitelistId;
        this.eventId = eventId;
        this.ticketCategoryId = ticketCategoryId;
        this.type = type;
        this.matchType = matchType;
        this.maxAllocation = maxAllocation;
    }
}
