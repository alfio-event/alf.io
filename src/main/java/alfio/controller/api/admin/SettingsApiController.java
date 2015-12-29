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
package alfio.controller.api.admin;

import alfio.manager.plugin.PluginManager;
import alfio.manager.system.ConfigurationManager;
import alfio.model.modification.ConfigurationModification;
import alfio.model.modification.PluginConfigOptionModification;
import alfio.model.plugin.PluginConfigOption;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.model.user.Organization;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.springframework.web.bind.annotation.RequestMethod.*;

@RestController
@RequestMapping("/admin/api")
public class SettingsApiController {

    private final ConfigurationManager configurationManager;
    private final PluginManager pluginManager;

    @Autowired
    public SettingsApiController(ConfigurationManager configurationManager, PluginManager pluginManager) {
        this.configurationManager = configurationManager;
        this.pluginManager = pluginManager;
    }

    @RequestMapping(value = "/configuration/load", method = GET)
    public Map<ConfigurationKeys.SettingCategory, List<Configuration>> loadConfiguration(Principal principal) {
        return configurationManager.loadAllSystemConfigurationIncludingMissing(principal.getName());
    }

    @RequestMapping(value = "/configuration/update", method = POST)
    public boolean updateConfiguration(@RequestBody ConfigurationModification configuration) {
        configurationManager.saveSystemConfiguration(ConfigurationKeys.fromString(configuration.getKey()), configuration.getValue());
        return true;
    }

    @RequestMapping(value = "/configuration/update-bulk", method = POST)
    public boolean updateConfiguration(@RequestBody Map<ConfigurationKeys.SettingCategory, List<ConfigurationModification>> input) {
        List<ConfigurationModification> list = Objects.requireNonNull(input).values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        configurationManager.saveAllSystemConfiguration(list);
        return true;
    }

    @RequestMapping(value = "/configuration/organizations/{organizationId}/load", method = GET)
    public Map<ConfigurationKeys.SettingCategory, List<Configuration>> loadOrganizationConfiguration(@PathVariable("organizationId") int organizationId, Principal principal) {
        return configurationManager.loadOrganizationConfig(organizationId, principal.getName());
    }

    @RequestMapping(value = "/configuration/organizations/{organizationId}/update", method = POST)
    public boolean updateOrganizationConfiguration(@PathVariable("organizationId") int organizationId,
                                                                     @RequestBody Map<ConfigurationKeys.SettingCategory, List<ConfigurationModification>> input, Principal principal) {
        configurationManager.saveAllOrganizationConfiguration(organizationId, input.values().stream().flatMap(Collection::stream).collect(Collectors.toList()), principal.getName());
        return true;
    }

    @RequestMapping(value = "/configuration/events/{eventId}/load", method = GET)
    public Map<ConfigurationKeys.SettingCategory, List<Configuration>> loadEventConfiguration(@PathVariable("eventId") int eventId, Principal principal) {
        return configurationManager.loadEventConfig(eventId, principal.getName());
    }

    @RequestMapping(value = "/configuration/organizations/{organizationId}/events/{eventId}/update", method = POST)
    public boolean updateEventConfiguration(@PathVariable("organizationId") int organizationId, @PathVariable("eventId") int eventId,
                                                    @RequestBody Map<ConfigurationKeys.SettingCategory, List<ConfigurationModification>> input, Principal principal) {
        configurationManager.saveEventConfiguration(eventId, organizationId, input.values().stream().flatMap(Collection::stream).collect(Collectors.toList()), principal.getName());
        return true;
    }

    @RequestMapping(value = "/configuration/events/{eventId}/categories/{categoryId}/update", method = POST)
    public boolean updateCategoryConfiguration(@PathVariable("categoryId") int categoryId, @PathVariable("eventId") int eventId,
                                                    @RequestBody Map<ConfigurationKeys.SettingCategory, List<ConfigurationModification>> input, Principal principal) {
        configurationManager.saveCategoryConfiguration(categoryId, eventId, input.values().stream().flatMap(Collection::stream).collect(Collectors.toList()), principal.getName());
        return true;
    }

    @RequestMapping(value = "/configuration/events/{eventId}/categories/{categoryId}/load", method = GET)
    public Map<ConfigurationKeys.SettingCategory, List<Configuration>> loadCategoryConfiguration(@PathVariable("eventId") int eventId, @PathVariable("categoryId") int categoryId, Principal principal) {
        return configurationManager.loadCategoryConfig(eventId, categoryId, principal.getName());
    }

    @RequestMapping(value = "/configuration/events/{eventId}/plugin/load", method = GET)
    public List<PluginConfigOption> loadPluginConfiguration(@PathVariable("eventId") int eventId, Principal principal) {
        return pluginManager.loadAllConfigOptions(eventId, principal.getName());
    }

    @RequestMapping(value = "/configuration/events/{eventId}/plugin/update-bulk", method = POST)
    public boolean updatePluginConfiguration(@PathVariable int eventId, @RequestBody List<PluginConfigOptionModification> input, Principal principal) {
        pluginManager.saveAllConfigOptions(eventId, Objects.requireNonNull(input), principal.getName());
        return true;
    }

    @RequestMapping(value = "/configuration/organization/{organizationId}/key/{key}", method = DELETE)
    public boolean deleteOrganizationLevelKey(@PathVariable("organizationId") int organizationId, @PathVariable("key") ConfigurationKeys key, Principal principal) {
        configurationManager.deleteOrganizationLevelByKey(key.getValue(), organizationId, principal.getName());
        return true;
    }

    @RequestMapping(value = "/configuration/event/{eventId}/key/{key}", method = DELETE)
    public boolean deleteEventLevelKey(@PathVariable("eventId") int eventId, @PathVariable("key") ConfigurationKeys key, Principal principal) {
        configurationManager.deleteEventLevelByKey(key.getValue(), eventId, principal.getName());
        return true;
    }

    @RequestMapping(value = "/configuration/event/{eventId}/category/{categoryId}/key/{key}", method = DELETE)
    public boolean deleteCategoryLevelKey(@PathVariable("eventId") int eventId, @PathVariable("categoryId") int categoryId, @PathVariable("key") ConfigurationKeys key, Principal principal) {
        configurationManager.deleteCategoryLevelByKey(key.getValue(), eventId, categoryId, principal.getName());
        return true;
    }

    @RequestMapping(value = "/configuration/key/{key}", method = DELETE)
    public boolean deleteKey(@PathVariable("key") String key) {
        configurationManager.deleteKey(key);
        return true;
    }

    @Data
    class OrganizationConfig {
        private final Organization organization;
        private final Map<ConfigurationKeys.SettingCategory, List<Configuration>> config;
    }
}
