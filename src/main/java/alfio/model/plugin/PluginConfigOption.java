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
package alfio.model.plugin;

import alfio.model.system.ComponentType;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Data;

/**
 * The configuration descriptor.
 * Each configurable plugin should define a set of PluginConfigOptions
 */
@Data
public class PluginConfigOption {
    private final String pluginId;
    private final int eventId;
    private final String optionName;
    private final String optionValue;
    private final String description;
    private final ComponentType componentType;


    public PluginConfigOption(@Column("plugin_id") String pluginId,
                              @Column("event_id") int eventId,
                              @Column("conf_name") String optionName,
                              @Column("conf_value") String optionValue,
                              @Column("conf_description") String description,
                              @Column("conf_type") ComponentType componentType) {
        this.pluginId = pluginId;
        this.eventId = eventId;
        this.optionName = optionName;
        this.optionValue = optionValue;
        this.description = description;
        this.componentType = componentType;
    }

    public String getKey() {
        return optionName;
    }

    public String getValue() {
        return optionValue;
    }
}
