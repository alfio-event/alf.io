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
    public Map<ConfigurationKeys.SettingCategory, List<Configuration>> updateConfiguration(@RequestBody ConfigurationModification configuration, Principal principal) {
        configurationManager.saveSystemConfiguration(ConfigurationKeys.fromValue(configuration.getKey()), configuration.getValue());
        return loadConfiguration(principal);
    }

    @RequestMapping(value = "/configuration/update-bulk", method = POST)
    public Map<ConfigurationKeys.SettingCategory, List<Configuration>> updateConfiguration(@RequestBody Map<ConfigurationKeys.SettingCategory, List<ConfigurationModification>> input,
                                                                                           Principal principal) {
        Objects.requireNonNull(input);
        List<ConfigurationModification> list = input.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        configurationManager.saveAllSystemConfiguration(list);
        return loadConfiguration(principal);
    }

    @RequestMapping(value = "/configuration/organizations/load", method = GET)
    public List<OrganizationConfig> loadOrganizationsConfiguration(Principal principal) {
        return configurationManager.loadAllOrganizationConfiguration(principal.getName()).entrySet()
            .stream()
            .map(e -> new OrganizationConfig(e.getKey(), e.getValue()))
            .collect(Collectors.toList());
    }

    @RequestMapping(value = "/configuration/organizations/{organizationId}/update", method = POST)
    public List<OrganizationConfig> updateOrganizationsConfiguration(@PathVariable("organizationId") int organizationId,
                                                                     @RequestBody Map<ConfigurationKeys.SettingCategory, List<ConfigurationModification>> input,
                                                                     Principal principal) {
        configurationManager.saveAllOrganizationConfiguration(organizationId, input.values().stream().flatMap(Collection::stream).collect(Collectors.toList()));
        return loadOrganizationsConfiguration(principal);
    }

    @RequestMapping(value = "/configuration/plugin/load", method = GET)
    public List<PluginConfigOption> loadPluginConfiguration() {
        return pluginManager.loadAllConfigOptions();
    }

    @RequestMapping(value = "/configuration/plugin/update-bulk", method = POST)
    public List<PluginConfigOption> updatePluginConfiguration(@RequestBody List<PluginConfigOptionModification> input) {
        Objects.requireNonNull(input);
        pluginManager.saveAllConfigOptions(input);
        return loadPluginConfiguration();
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
