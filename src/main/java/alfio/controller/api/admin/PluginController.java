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
import alfio.model.plugin.PluginLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/admin/api")
public class PluginController {

    private final PluginManager pluginManager;

    @Autowired
    public PluginController(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    @RequestMapping(value = "/events/{eventName}/plugin/log", method = RequestMethod.GET)
    public List<PluginLog> loadAllLogMessages(@PathVariable("eventName") String eventName, Principal principal) {
        return pluginManager.loadAllLogMessages(eventName, principal.getName());
    }

}
