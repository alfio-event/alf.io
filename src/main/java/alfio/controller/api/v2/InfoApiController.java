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
package alfio.controller.api.v2;

import alfio.controller.api.v2.model.AlfioInfo;
import alfio.manager.system.ConfigurationManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/v2/")
public class InfoApiController {

    private final ConfigurationManager configurationManager;

    public InfoApiController(ConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
    }

    @GetMapping("info")
    public AlfioInfo getInfo(HttpSession session) {
        return configurationManager.getInfo(session);
    }
}
