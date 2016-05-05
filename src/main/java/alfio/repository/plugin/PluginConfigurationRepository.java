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

import alfio.model.plugin.PluginConfigOption;
import alfio.model.system.ComponentType;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

import java.util.List;
import java.util.Optional;

@QueryRepository
public interface PluginConfigurationRepository {

    @Query("select * from plugin_configuration")
    List<PluginConfigOption> loadAll();

    @Query("select * from plugin_configuration where plugin_id = :pluginId and event_id = :eventId")
    List<PluginConfigOption> loadByPluginIdAndEventId(@Bind("pluginId") String pluginId, @Bind("eventId") int eventId);

    @Query("select * from plugin_configuration where plugin_id = :pluginId and event_id = :eventId and conf_name = :name")
    Optional<PluginConfigOption> loadSingleOption(@Bind("pluginId") String pluginId, @Bind("eventId") int eventId, @Bind("name") String name);
    
    @Query("delete from plugin_configuration where plugin_id = :pluginId")
    int delete(@Bind("pluginId") String pluginId);

    @Query("insert into plugin_configuration(plugin_id, event_id, conf_name, conf_value, conf_description, conf_type) values (:pluginId, :eventId, :name, :value, :description, :type)")
    int insert(@Bind("pluginId") String pluginId, @Bind("eventId") int eventId, @Bind("name") String name, @Bind("value") String value, @Bind("description") String description, @Bind("type") ComponentType type);

    @Query("update plugin_configuration set conf_value = :value where plugin_id = :pluginId and event_id = :eventId and conf_name = :name")
    int update(@Bind("pluginId") String pluginId, @Bind("eventId") int eventId, @Bind("name") String name, @Bind("value") String value);

    @Query("select * from plugin_configuration where event_id = :eventId order by id")
    List<PluginConfigOption> loadByEventId(@Bind("eventId") int eventId);

}
