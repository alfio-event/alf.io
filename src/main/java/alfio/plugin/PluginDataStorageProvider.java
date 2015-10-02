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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Optional;

public class PluginDataStorageProvider {

    private final PluginConfigurationRepository pluginConfigurationRepository;
    private final PluginLogRepository pluginLogRepository;
    private final PlatformTransactionManager platformTransactionManager;

    public PluginDataStorageProvider(PluginConfigurationRepository pluginConfigurationRepository,
                                     PluginLogRepository pluginLogRepository,
                                     PlatformTransactionManager platformTransactionManager) {
        this.pluginConfigurationRepository = pluginConfigurationRepository;
        this.pluginLogRepository = pluginLogRepository;
        this.platformTransactionManager = platformTransactionManager;
    }

    public PluginDataStorage getDataStorage(String pluginId) {
        return new PluginDataStorage(pluginId, pluginConfigurationRepository, pluginLogRepository, new TransactionTemplate(platformTransactionManager));
    }


    public static class PluginDataStorage {
        private final String pluginId;
        private final PluginConfigurationRepository pluginConfigurationRepository;
        private final PluginLogRepository pluginLogRepository;
        private final TransactionTemplate tx;

        private PluginDataStorage(String pluginId,
                                  PluginConfigurationRepository pluginConfigurationRepository,
                                  PluginLogRepository pluginLogRepository, TransactionTemplate tx) {
            this.pluginId = pluginId;
            this.pluginConfigurationRepository = pluginConfigurationRepository;
            this.pluginLogRepository = pluginLogRepository;
            this.tx = tx;
        }

        public Optional<String> getConfigValue(String name, int eventId) {
            return pluginConfigurationRepository.loadSingleOption(pluginId, eventId, name).map(PluginConfigOption::getOptionValue);
        }

        public void insertConfigValue(int eventId, String name, String value, String description, ComponentType componentType) {
            pluginConfigurationRepository.insert(pluginId, eventId, name, value, description, componentType);
        }

        public void registerSuccess(String description, int eventId) {
            tx.execute(tc -> {
                pluginLogRepository.insertEvent(pluginId, eventId, description, PluginLog.Type.SUCCESS, ZonedDateTime.now(Clock.systemUTC()));
                return null;
            });
        }

        public void registerFailure(String description, int eventId) {
            tx.execute(tc -> {
                pluginLogRepository.insertEvent(pluginId, eventId, description, PluginLog.Type.ERROR, ZonedDateTime.now(Clock.systemUTC()));
                return null;
            });
        }

        public void registerWarning(String description, int eventId) {
            tx.execute(tc -> {
                pluginLogRepository.insertEvent(pluginId, eventId, description, PluginLog.Type.WARNING, ZonedDateTime.now(Clock.systemUTC()));
                return null;
            });
        }

    }
}
