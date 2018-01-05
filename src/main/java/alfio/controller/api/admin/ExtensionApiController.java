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
import alfio.model.ExtensionSupport;
import alfio.extension.Extension;
import alfio.extension.ExtensionService;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.security.Principal;
import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/admin/api/extensions")
public class ExtensionApiController {

    private static final String SAMPLE_JS;

    static {
        try {
            SAMPLE_JS = new String(Files.readAllBytes(new File(ExtensionApiController.class.getResource("/alfio/extension/sample.js").toURI()).toPath()));
        } catch (URISyntaxException | IOException e) {
            throw new IllegalStateException("cannot read sample file", e);
        }
    }

    private final ExtensionService extensionService;
    private final UserManager userManager;


    @RequestMapping(value = "", method = RequestMethod.GET)
    public List<ExtensionSupport> listAll(Principal principal) {
        ensureAdmin(principal);
        return extensionService.listAll();
    }

    @RequestMapping(value = "/sample", method = RequestMethod.GET)
    public ExtensionSupport getSample() {
        return new ExtensionSupport("", "", null, true, true, SAMPLE_JS);
    }

    @RequestMapping(value = "", method = RequestMethod.POST)
    public void createOrUpdate(@RequestBody Extension script, Principal principal) {
        ensureAdmin(principal);
        extensionService.createOrUpdate(script);
    }

    @RequestMapping(value = "/{name}", method = RequestMethod.GET)
    public ResponseEntity<ExtensionSupport> loadSingle(@RequestParam("path") String path, @PathVariable("name") String name, Principal principal) throws UnsupportedEncodingException {
        ensureAdmin(principal);
        return extensionService.getSingle(URLDecoder.decode(path, "UTF-8"), name).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }


    @RequestMapping(value = "/{name}", method = RequestMethod.DELETE)
    public void delete(@RequestParam("path") String path, @PathVariable("name") String name, Principal principal) {
        ensureAdmin(principal);
        extensionService.delete(path, name);
    }

    @RequestMapping(value = "/{path}/{name}/toggle/{enable}", method = RequestMethod.POST)
    public void toggle(@PathVariable("path") String path, @PathVariable("name") String name, @PathVariable("enable") boolean enable, Principal principal) {
        ensureAdmin(principal);
        extensionService.toggle(path, name, enable);
    }

    private void ensureAdmin(Principal principal) {
        Validate.isTrue(userManager.isAdmin(userManager.findUserByUsername(principal.getName())));
    }
}
