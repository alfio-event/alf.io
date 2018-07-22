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
public class GroupMember {

    private final int id;
    private final int groupId;
    private final String value;
    private final String description;


    public GroupMember(@Column("id") int id,
                       @Column("a_group_id_fk") int groupId,
                       @Column("value") String value,
                       @Column("description") String description) {
        this.id = id;
        this.groupId = groupId;
        this.value = value;
        this.description = description;
    }
}
