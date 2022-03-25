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
package alfio.model.metadata;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.Map;

@Getter
public class ConditionsLink {
    enum Type {
        TERMS_OF_PARTICIPATION, PRIVACY_POLICY, CUSTOM
    }

    private final Type type;
    private final Map<String, String> description; // needed only for CUSTOM
    private final String url;

    @JsonCreator
    public ConditionsLink(@JsonProperty("type") Type type,
                          @JsonProperty("description") Map<String, String> description,
                          @JsonProperty("url") String url) {
        this.type = type;
        this.description = description;
        this.url = url;
    }
}
