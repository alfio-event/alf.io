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
package alfio.manager.plugin;

import alfio.model.Ticket;
import alfio.model.TicketReservation;
import alfio.model.system.ComponentType;
import alfio.plugin.Plugin;
import alfio.plugin.ReservationConfirmationPlugin;
import alfio.plugin.TicketAssignmentPlugin;
import alfio.repository.plugin.PluginConfigurationRepository;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

@Component
public class PluginManager implements ApplicationListener<ContextRefreshedEvent> {

    private final List<Plugin> plugins;
    private final PluginConfigurationRepository pluginConfigurationRepository;
    private final Executor executor = Executors.newCachedThreadPool();

    @Autowired
    public PluginManager(List<Plugin> plugins, PluginConfigurationRepository pluginConfigurationRepository) {
        this.plugins = plugins;
        this.pluginConfigurationRepository = pluginConfigurationRepository;
    }

    public void handleReservationConfirmation(TicketReservation reservation) {
        filterPlugins(plugins, ReservationConfirmationPlugin.class)
                .forEach(p -> executor.execute(() -> p.onReservationConfirmation(reservation)));
    }

    public void handleTicketAssignment(Ticket ticket) {
        filterPlugins(plugins, TicketAssignmentPlugin.class)
                .forEach(p -> executor.execute(() -> p.onTicketAssignment(ticket)));
    }

    private static <T extends Plugin> Stream<T> filterPlugins(List<Plugin> plugins, Class<T> type) {
        return plugins.stream()
                .filter(type::isInstance)
                .filter(Plugin::isEnabled)
                .map(type::cast);
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        plugins.stream()
                .filter(p -> !pluginConfigurationRepository.loadSingleOption(p.getId(), Plugin.ENABLED_CONF_NAME).isPresent())
                .forEach(p -> pluginConfigurationRepository.insert(p.getId(), Plugin.ENABLED_CONF_NAME, "false", "Enabled", ComponentType.BOOLEAN));
    }
}
