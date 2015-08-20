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

import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
public class PluginLog {

    public enum Type {
        SUCCESS, ERROR, WARNING
    }

    private final int id;
    private final String eventName;
    private final int eventId;
    private final String pluginId;
    private final String description;
    private final Type type;
    private final ZonedDateTime timestamp;

    public PluginLog(@Column("id") int id,
                     @Column("short_name") String eventName,
                     @Column("event_id") int eventId,
                     @Column("plugin_id") String pluginId,
                     @Column("description") String description,
                     @Column("type") Type type,
                     @Column("event_ts") ZonedDateTime timestamp) {
        this.id = id;
        this.eventName = eventName;
        this.eventId = eventId;
        this.pluginId = pluginId;
        this.description = description;
        this.type = type;
        this.timestamp = timestamp;
    }
}
