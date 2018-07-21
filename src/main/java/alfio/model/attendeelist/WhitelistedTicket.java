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
package alfio.model.attendeelist;

import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

@Getter
public class WhitelistedTicket {

    private final int attendeeListItemId;
    private final int attendeeListConfigurationId;
    private final int ticketId;
    private final boolean requiresUniqueValue;

    public WhitelistedTicket(@Column("attendee_list_item_id_fk") int attendeeListItemId,
                             @Column("attendee_list_configuration_id_fk") int attendeeListConfigurationId,
                             @Column("ticket_id_fk") int ticketId,
                             @Column("requires_unique_value") Boolean requiresUniqueValue) {
        this.attendeeListItemId = attendeeListItemId;
        this.attendeeListConfigurationId = attendeeListConfigurationId;
        this.ticketId = ticketId;
        this.requiresUniqueValue = requiresUniqueValue != null ? requiresUniqueValue : false;
    }

}
