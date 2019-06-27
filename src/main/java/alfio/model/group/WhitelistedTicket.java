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
package alfio.model.group;

import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;
import org.apache.commons.lang3.ObjectUtils;

@Getter
public class WhitelistedTicket {

    private final int groupMemberId;
    private final int groupLinkId;
    private final int ticketId;
    private final boolean requiresUniqueValue;

    public WhitelistedTicket(@Column("group_member_id_fk") int groupMemberId,
                             @Column("group_link_id_fk") int groupLinkId,
                             @Column("ticket_id_fk") int ticketId,
                             @Column("requires_unique_value") Boolean requiresUniqueValue) {
        this.groupMemberId = groupMemberId;
        this.groupLinkId = groupLinkId;
        this.ticketId = ticketId;
        this.requiresUniqueValue = ObjectUtils.firstNonNull(requiresUniqueValue, Boolean.FALSE);
    }

}
