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
package alfio.model.system;

import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

@Getter
public class ConfigurationKeyValuePathLevel {

    private final ConfigurationKeys configurationKey;
    private final String value;
    private final ConfigurationPathLevel configurationPathLevel;

    public ConfigurationKeyValuePathLevel(@Column("c_key") String key,
                                          @Column("c_value") String value,
                                          @Column("configuration_path_level") ConfigurationPathLevel configurationPathLevel) {
        this.configurationKey = ConfigurationKeys.safeValueOf(key);
        this.value = value;
        this.configurationPathLevel = configurationPathLevel;
    }
}
