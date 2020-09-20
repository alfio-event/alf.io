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

import alfio.model.poll.PollOption;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.apache.commons.collections4.MapUtils;

import java.util.Map;

@Getter
public class PollOptionModification {

    private final Long id;
    private final Map<String, String> title;
    private final Map<String, String> description;

    @JsonCreator
    public PollOptionModification(@JsonProperty("id") Long id,
                                  @JsonProperty("title") Map<String, String> title,
                                  @JsonProperty("description") Map<String, String> description) {
        this.id = id;
        this.title = title;
        this.description = description;
    }

    public boolean isValid(boolean update) {
        return (!update || id != null) && MapUtils.isNotEmpty(title);
    }

    public static PollOptionModification from(PollOption option) {
        return new PollOptionModification(option.getId(), option.getTitle(), option.getDescription());
    }
}
