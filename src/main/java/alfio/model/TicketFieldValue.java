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

import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

@Getter
public class TicketFieldValue {

    private final int ticketId;
    private final long fieldConfigurationId;
    private final String name;
    private final String value;

    public TicketFieldValue(@Column("ticket_id_fk") int ticketId,
                            @Column("field_configuration_id_fk") long fieldConfigurationId,
                            @Column("field_name") String name,
                            @Column("field_value") String value) {
        this.ticketId = ticketId;
        this.fieldConfigurationId = fieldConfigurationId;
        this.name = name;
        this.value = value;
    }
}
