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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@Getter
public class OrganizationModification {

    private final Integer id;
    private final String name;
    private final String email;
    private final String description;
    private final String externalId;
    private final String slug;

    @JsonCreator
    public OrganizationModification(@JsonProperty("id") Integer id,
                                    @JsonProperty("name") String name,
                                    @JsonProperty("email") String email,
                                    @JsonProperty("description") String description,
                                    @JsonProperty("externalId") String externalId,
                                    @JsonProperty("slug") String slug) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.description = description;
        this.externalId = externalId;
        this.slug = slug;
    }

    @JsonIgnore
    public boolean isValid(boolean create) {
        return (create || (id != null && id > 0))
            && StringUtils.isNotEmpty(name)
            && StringUtils.isNotEmpty(email)
            && StringUtils.isNotEmpty(description);
    }
}
