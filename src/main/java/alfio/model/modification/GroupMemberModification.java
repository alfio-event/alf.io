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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Optional;

@Getter
public class GroupMemberModification {
    private final Integer id;
    private final String value;
    private final String description;

    @JsonCreator
    public GroupMemberModification(@JsonProperty("id") Integer id,
                                   @JsonProperty("value") String value,
                                   @JsonProperty("description") String description) {
        this.id = id;
        this.value = Optional.ofNullable(value).map(s -> s.strip().toLowerCase()).orElse(null);
        this.description = description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (!(o instanceof GroupMemberModification)) {
            return false;
        }

        GroupMemberModification that = (GroupMemberModification) o;

        return new EqualsBuilder()
            .append(id, that.id)
            .append(value, that.value)
            .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(id)
            .append(value)
            .toHashCode();
    }
}
