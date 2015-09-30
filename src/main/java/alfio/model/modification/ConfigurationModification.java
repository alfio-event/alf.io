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

import alfio.model.system.Configuration;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class ConfigurationModification {
    private final Integer id;
    private final String key;
    private final String value;

    @JsonCreator
    public ConfigurationModification(@JsonProperty("id") Integer id,
                                     @JsonProperty("key") String key,
                                     @JsonProperty("value") String value) {
        this.id = id;
        this.key = key;
        this.value = value;
    }

    public static ConfigurationModification fromConfiguration(Configuration in) {
        return new ConfigurationModification(in.getId(), in.getKey(), in.getValue());
    }
}
