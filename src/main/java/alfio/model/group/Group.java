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

@Getter
public class Group {
    private final int id;
    private final String name;
    private final String description;
    private final Integer organizationId;
    private final boolean active;


    public Group(@Column("id") int id,
                 @Column("name") String name,
                 @Column("description") String description,
                 @Column("organization_id_fk") Integer organizationId,
                 @Column("active") boolean active) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.organizationId = organizationId;
        this.active = active;
    }
}
