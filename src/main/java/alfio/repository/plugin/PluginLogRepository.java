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

import alfio.model.plugin.PluginLog;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

import java.time.ZonedDateTime;
import java.util.List;

@QueryRepository
public interface PluginLogRepository {

    @Query("insert into plugin_log (plugin_id, event_id, description, type, event_ts) values(:pluginId, :eventId, :description, :type, :eventTs)")
    int insertEvent(@Bind("pluginId") String pluginId, @Bind("eventId") int eventId, @Bind("description") String description, @Bind("type") PluginLog.Type type, @Bind("eventTs") ZonedDateTime timestamp);

    @Query("select pl.id, pl.plugin_id, pl.event_id, pl.description, pl.type, pl.event_ts, e.short_name from plugin_log pl, event e where pl.event_id = e.id order by pl.event_ts asc")
    List<PluginLog> loadAll();

    @Query("select pl.id, pl.plugin_id, pl.event_id, pl.description, pl.type, pl.event_ts, e.short_name from plugin_log pl, event e where pl.event_id = :eventId and pl.event_id = e.id  order by pl.event_ts asc")
    List<PluginLog> loadByEventId(@Bind("eventId") int eventId);
}
