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

import alfio.manager.AccessService;
import alfio.manager.system.ConfigurationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/admin/api/system/api-key")
public class SystemApiKeyApiController {
    private static final Logger log = LoggerFactory.getLogger(SystemApiKeyApiController.class);
    private final ConfigurationManager configurationManager;
    private final AccessService accessService;

    public SystemApiKeyApiController(ConfigurationManager configurationManager, AccessService accessService) {
        this.configurationManager = configurationManager;
        this.accessService = accessService;
    }

    @GetMapping()
    public ResponseEntity<String> retrieveApiKey(Principal principal) {
        accessService.ensureAdmin(principal);
        try {
            return ResponseEntity.ok(configurationManager.retrieveSystemApiKey(false));
        } catch(RuntimeException e) {
            log.error("Error while retrieving system API Key", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping()
    public ResponseEntity<String> rotateApiKey(Principal principal) {
        accessService.ensureAdmin(principal);
        try {
            return ResponseEntity.ok(configurationManager.retrieveSystemApiKey(true));
        } catch(RuntimeException e) {
            log.error("Error while rotating system API Key", e);
            return ResponseEntity.internalServerError().build();
        }
    }

}
