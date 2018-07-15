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

import alfio.manager.WhitelistManager;
import alfio.manager.user.UserManager;
import alfio.model.modification.WhitelistModification;
import alfio.model.whitelist.Whitelist;
import alfio.util.OptionalWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/admin/api/whitelist")
@RequiredArgsConstructor
public class AttendeeListApiController {

    private final WhitelistManager whitelistManager;
    private final UserManager userManager;

    @GetMapping("/{organizationId}")
    public ResponseEntity<List<Whitelist>> loadAllWhitelistsForOrganization(@PathVariable("organizationId") int organizationId, Principal principal) {
        if(notOwner(principal.getName(), organizationId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(whitelistManager.getAllForOrganization(organizationId));
    }

    @GetMapping("/{organizationId}/detail/{listId}")
    public ResponseEntity<WhitelistModification> loadDetail(@PathVariable("organizationId") int organizationId, @PathVariable("listId") int listId, Principal principal) {
        if(notOwner(principal.getName(), organizationId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return whitelistManager.loadComplete(listId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{organizationId}/new")
    public ResponseEntity<Integer> createNew(@PathVariable("organizationId") int organizationId, @RequestBody WhitelistModification request, Principal principal) {
        if(notOwner(principal.getName(), organizationId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if(request.getOrganizationId() != organizationId) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(whitelistManager.createNew(request));
    }


    private boolean notOwner(String username, int organizationId) {
        return !OptionalWrapper.optionally(() -> userManager.findUserByUsername(username))
            .filter(user -> userManager.isOwnerOfOrganization(user, organizationId))
            .isPresent();
    }

}
