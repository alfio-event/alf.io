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
import lombok.Getter;
import lombok.ToString;

import java.util.List;
import java.util.Map;

@Getter
@ToString
public class Poll {


    public enum PollStatus {
        DRAFT,
        OPEN,
        CLOSED
    }

    private final long id;
    private final PollStatus status;
    private final Map<String, String> title;
    private final Map<String, String> description;
    private final List<String> allowedTags;
    private final int order;
    private final int eventId;
    private final int organizationId;

    public Poll(@Column("id") long id,
                @Column("status") PollStatus status,
                @Column("title") @JSONData Map<String, String> title,
                @Column("description") @JSONData Map<String, String> description,
                @Column("allowed_tags") @Array List<String> allowedTags,
                @Column("poll_order") int order,
                @Column("event_id_fk") int eventId,
                @Column("organization_id_fk") int organizationId) {

        this.id = id;
        this.status = status;
        this.title = title;
        this.description = description;
        this.allowedTags = allowedTags;
        this.order = order;
        this.eventId = eventId;
        this.organizationId = organizationId;
    }


}
