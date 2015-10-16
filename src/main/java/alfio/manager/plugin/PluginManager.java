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

import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.Ticket;
import alfio.model.TicketReservation;
import alfio.model.WaitingQueueSubscription;
import alfio.model.modification.PluginConfigOptionModification;
import alfio.model.plugin.PluginConfigOption;
import alfio.model.plugin.PluginLog;
import alfio.model.system.ComponentType;
import alfio.plugin.Plugin;
import alfio.plugin.ReservationConfirmationPlugin;
import alfio.plugin.TicketAssignmentPlugin;
import alfio.plugin.WaitingQueueSubscriptionPlugin;
import alfio.repository.EventRepository;
import alfio.repository.plugin.PluginConfigurationRepository;
import alfio.repository.plugin.PluginLogRepository;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class PluginManager implements ApplicationListener<ContextRefreshedEvent> {

    private final List<Plugin> plugins;
    private final PluginConfigurationRepository pluginConfigurationRepository;
    private final PluginLogRepository pluginLogRepository;
    private final EventRepository eventRepository;
    private final UserManager userManager;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Autowired
    public PluginManager(List<Plugin> plugins, PluginConfigurationRepository pluginConfigurationRepository, PluginLogRepository pluginLogRepository, EventRepository eventRepository, UserManager userManager) {
        this.plugins = plugins;
        this.pluginConfigurationRepository = pluginConfigurationRepository;
        this.pluginLogRepository = pluginLogRepository;
        this.eventRepository = eventRepository;
        this.userManager = userManager;
    }

    public void handleReservationConfirmation(TicketReservation reservation, int eventId) {
        executor.submit(() -> filterPlugins(plugins, eventId, ReservationConfirmationPlugin.class).forEach(p -> p.onReservationConfirmation(reservation, eventId)));
    }

    public void handleTicketAssignment(Ticket ticket) {
        executor.submit(() -> filterPlugins(plugins, ticket.getEventId(), TicketAssignmentPlugin.class).forEach(p -> p.onTicketAssignment(ticket)));
    }

    public void handleWaitingQueueSubscription(WaitingQueueSubscription waitingQueueSubscription) {
        executor.submit(() -> filterPlugins(plugins, waitingQueueSubscription.getEventId(), WaitingQueueSubscriptionPlugin.class).forEach(p -> p.onWaitingQueueSubscription(waitingQueueSubscription)));
    }

    public List<PluginConfigOption> loadAllConfigOptions(int eventId, String username) {
        if(!validateOwnership(eventId, username)) {
            return Collections.emptyList();
        }
        return pluginConfigurationRepository.loadByEventId(eventId);
    }

    public void saveAllConfigOptions(int eventId, List<PluginConfigOptionModification> input, String username) {
        Validate.isTrue(validateOwnership(eventId, username));
        input.forEach(m -> pluginConfigurationRepository.update(m.getPluginId(), eventId, m.getName(), m.getValue()));
    }

    private boolean validateOwnership(int eventId, String username) {
        return validateOwnership(eventRepository.findById(eventId), username);
    }

    private boolean validateOwnership(Event event, String username) {
        return userManager.isOwnerOfOrganization(userManager.findUserByUsername(username), event.getOrganizationId());
    }

    public List<PluginLog> loadAllLogMessages(String eventName, String username) {
        Event event = eventRepository.findByShortName(eventName);
        Validate.notNull(event, "invalid shortName");
        Validate.isTrue(validateOwnership(event, username), "Not Authorized");
        return pluginLogRepository.loadByEventId(event.getId());
    }

    private static <T extends Plugin> Stream<T> filterPlugins(List<Plugin> plugins, int eventId, Class<T> type) {
        return plugins.stream()
                .filter(type::isInstance)
                .filter(p -> p.isEnabled(eventId))
                .map(type::cast);
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        eventRepository.findAll()
            .stream()
            .filter(e -> e.getEnd().isBefore(ZonedDateTime.now(e.getZoneId())))
            .forEach(this::installPlugins);
    }

    public void installPlugins(Event event) {
        final int eventId = event.getId();
        plugins.stream()
            .filter(p -> !pluginConfigurationRepository.loadSingleOption(p.getId(), eventId, Plugin.ENABLED_CONF_NAME).isPresent())
            .forEach(p -> {
                pluginConfigurationRepository.insert(p.getId(), eventId, Plugin.ENABLED_CONF_NAME, "false", "Enabled", ComponentType.BOOLEAN);
                p.install(eventId);
            });
    }
}
