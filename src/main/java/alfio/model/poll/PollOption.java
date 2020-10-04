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

import alfio.model.support.JSONData;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

import java.util.Map;

@Getter
public class PollOption {
    private final Long id;
    private final Long pollId;
    private final Map<String, String> title;
    private final Map<String, String> description;

    public PollOption(@Column("id") Long id,
                      @Column("poll_id_fk") Long pollId,
                      @Column("title") @JSONData Map<String, String> title,
                      @Column("description") @JSONData Map<String, String> description) {
        this.id = id;
        this.pollId = pollId;
        this.title = title;
        this.description = description;
    }
}
