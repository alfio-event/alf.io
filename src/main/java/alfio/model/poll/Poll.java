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
package alfio.model.poll;

import alfio.model.support.Array;
import alfio.model.support.JSONData;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;

import java.util.List;
import java.util.Map;


public record Poll(@Column("id") long id,
                   @Column("status") PollStatus status,
                   @Column("title") @JSONData Map<String, String> title,
                   @Column("description") @JSONData Map<String, String> description,
                   @Column("allowed_tags") @Array List<String> allowedTags,
                   @Column("poll_order") int order,
                   @Column("event_id_fk") int eventId,
                   @Column("organization_id_fk") int organizationId) {


    public enum PollStatus {
        DRAFT,
        OPEN,
        CLOSED
    }
}
