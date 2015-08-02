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
package alfio.plugin;

import alfio.model.plugin.PluginConfigOption;
import alfio.model.plugin.PluginLog;
import alfio.model.system.ComponentType;
import alfio.repository.plugin.PluginConfigurationRepository;
import alfio.repository.plugin.PluginLogRepository;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Optional;

public class PluginDataStorageProvider {

    private final PluginConfigurationRepository pluginConfigurationRepository;
    private final PluginLogRepository pluginLogRepository;

    public PluginDataStorageProvider(PluginConfigurationRepository pluginConfigurationRepository,
                                     PluginLogRepository pluginLogRepository) {
        this.pluginConfigurationRepository = pluginConfigurationRepository;
        this.pluginLogRepository = pluginLogRepository;
    }

    public PluginDataStorage getDataStorage(String pluginId) {
        return new PluginDataStorage(pluginId, pluginConfigurationRepository, pluginLogRepository);
    }


    public static class PluginDataStorage {
        private final String pluginId;
        private final PluginConfigurationRepository pluginConfigurationRepository;
        private final PluginLogRepository pluginLogRepository;

        private PluginDataStorage(String pluginId,
                                  PluginConfigurationRepository pluginConfigurationRepository,
                                  PluginLogRepository pluginLogRepository) {
            this.pluginId = pluginId;
            this.pluginConfigurationRepository = pluginConfigurationRepository;
            this.pluginLogRepository = pluginLogRepository;
        }

        public Optional<String> getConfigValue(String name) {
            return pluginConfigurationRepository.loadSingleOption(pluginId, name).map(PluginConfigOption::getOptionValue);
        }

        public void insertConfigValue(String name, String value, String description, ComponentType componentType) {
            pluginConfigurationRepository.insert(pluginId, name, value, description, componentType);
        }

        public void registerSuccess(String description, int eventId) {
            pluginLogRepository.insertEvent(pluginId, eventId, description, PluginLog.Type.SUCCESS, ZonedDateTime.now(Clock.systemUTC()));
        }

        public void registerFailure(String description, int eventId) {
            pluginLogRepository.insertEvent(pluginId, eventId, description, PluginLog.Type.ERROR, ZonedDateTime.now(Clock.systemUTC()));
        }

    }
}
