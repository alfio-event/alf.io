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
package alfio.repository.plugin;

import alfio.model.plugin.PluginEvent;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

import java.time.ZonedDateTime;
import java.util.List;

@QueryRepository
public interface PluginEventRepository {

    @Query("insert into plugin_event (plugin_id, description, type, event_ts) values(:pluginId, :description, :type, :event_ts)")
    int insertEvent(@Bind("pluginId") String pluginId, @Bind("description") String description, @Bind("type") PluginEvent.Type type, @Bind("eventTs") ZonedDateTime timestamp);

    @Query("select * from plugin_event")
    List<PluginEvent> loadAll();
}
