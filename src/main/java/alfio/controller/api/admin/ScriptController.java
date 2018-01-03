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

import alfio.manager.user.UserManager;
import alfio.model.ScriptSupport;
import alfio.scripting.Script;
import alfio.scripting.ScriptingService;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/admin/api")
public class ScriptController {

    private final ScriptingService scriptingService;
    private final UserManager userManager;


    @RequestMapping(value = "/scripting", method = RequestMethod.GET)
    public List<ScriptSupport> listAll(Principal principal) {
        ensureAdmin(principal);
        return scriptingService.listAll();
    }

    /*
    function getScriptMetadata() {
        return {async:true, events: ['RESERVATION_CONFIRMATION']};
    }

    function executeScript(event) {
        log.warn('hello from script with event: ' + event);
    }
     */
    @RequestMapping(value = "/scripting", method = RequestMethod.POST)
    public void createOrUpdate(@RequestBody Script script, Principal principal) {
        ensureAdmin(principal);
        scriptingService.createOrUpdate(script);
    }


    @RequestMapping(value = "/scripting/{path}/{name}", method = RequestMethod.DELETE)
    public void delete(@PathVariable("path") String path, @PathVariable("name") String name, Principal principal) {
        ensureAdmin(principal);
        scriptingService.delete(path, name);
    }

    @RequestMapping(value = "/scripting/{path}/{name}/toggle/{enable}", method = RequestMethod.POST)
    public void toggle(@PathVariable("path") String path, @PathVariable("name") String name, @PathVariable("enable") boolean enable, Principal principal) {
        ensureAdmin(principal);
        scriptingService.toggle(path, name, enable);
    }

    private void ensureAdmin(Principal principal) {
        Validate.isTrue(userManager.isAdmin(userManager.findUserByUsername(principal.getName())));
    }
}
