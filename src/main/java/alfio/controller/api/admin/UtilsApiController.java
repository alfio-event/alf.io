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

import alfio.manager.EventNameManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@RestController
@RequestMapping("/admin/api/utils")
public class UtilsApiController {

    private final EventNameManager eventNameManager;

    @Autowired
    public UtilsApiController(EventNameManager eventNameManager) {
        this.eventNameManager = eventNameManager;
    }

    @RequestMapping(value = "/short-name/generate", method = GET)
    public String generateShortName(@RequestParam("displayName") String displayName) {
        return eventNameManager.generateShortName(displayName);
    }

    @RequestMapping(value = "/short-name/validate", method = POST)
    public boolean validateShortName(@RequestParam("shortName") String shortName, HttpServletResponse response) {
        boolean unique = eventNameManager.isUnique(shortName);
        if(!unique) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
        }
        return unique;
    }

}
