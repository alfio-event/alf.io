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

import java.util.Collection;

/**
 * This is the base interface for developing external Alf.io plugins.
 * A plugin can be integrated either in the Pre-Confirmation or in the Post-Confirmation phases.
 */
public interface Plugin {

    String ENABLED_CONF_NAME = "enabled";

    /**
     * Returns an unique ID for this
     * @return
     */
    String getId();

    /**
     * Returns the plugin name. This is used mainly for informational purposes
     * @return the plugin name
     */
    String getName();

    /**
     * Returns the state of this plugin: whether or not the admin activated it
     * @param eventId the Event under examination
     * @return {@code true} if enabled, {@code false} otherwise
     */
    boolean isEnabled(int eventId);

    /**
     * Returns all the config options needed by the current plugin. This collection must contains at least one element
     * @param eventId the id of the event
     * @return the options, never {@code null}
     */
    Collection<PluginConfigOption> getConfigOptions(int eventId);

    /**
     * Triggers the installation procedure. In this phase, the plugin can safely write its configuration on the database.
     * @param eventId id of the event to be configured
     */
    void install(int eventId);
}
