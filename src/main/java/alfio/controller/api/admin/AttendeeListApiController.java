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

import alfio.manager.EventManager;
import alfio.manager.WhitelistManager;
import alfio.manager.user.UserManager;
import alfio.model.modification.WhitelistConfigurationModification;
import alfio.model.modification.WhitelistModification;
import alfio.model.whitelist.Whitelist;
import alfio.model.whitelist.WhitelistConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static alfio.util.OptionalWrapper.optionally;

@RestController
@RequestMapping("/admin/api/whitelist")
@RequiredArgsConstructor
public class AttendeeListApiController {

    private final WhitelistManager whitelistManager;
    private final UserManager userManager;
    private final EventManager eventManager;

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

    @GetMapping("/event/{eventName}")
    public ResponseEntity<WhitelistConfiguration> findActiveList(@PathVariable("eventName") String eventName,
                                                                 Principal principal) {
        return eventManager.getOptionalByName(eventName, principal.getName())
            .map(event -> {
                Optional<WhitelistConfiguration> configuration = whitelistManager.getConfigurationsForEvent(event.getId()).stream()
                    .filter(c -> c.getTicketCategoryId() == null)
                    .findFirst();
                return configuration.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/event/{eventName}/category/{categoryId}")
    public ResponseEntity<WhitelistConfiguration> findActiveList(@PathVariable("eventName") String eventName,
                                                                 @PathVariable("categoryId") int categoryId,
                                                                 Principal principal) {
        return eventManager.getOptionalByName(eventName, principal.getName())
            .map(event -> {
                Optional<WhitelistConfiguration> configuration = whitelistManager.findConfigurations(event.getId(), categoryId).stream().findFirst();
                return configuration.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{listId}/link")
    public ResponseEntity<Integer> linkList(@PathVariable("listId") int listId, @RequestBody WhitelistConfigurationModification body, Principal principal) {
        if(body == null || listId != body.getWhitelistId()) {
            return ResponseEntity.badRequest().build();
        }

        return optionally(() -> eventManager.getSingleEventById(body.getEventId(), principal.getName()))
            .map(event -> {
                Optional<WhitelistConfiguration> existing = whitelistManager.getConfigurationsForEvent(event.getId())
                    .stream()
                    .filter(c -> c.getWhitelistId() == listId && Objects.equals(body.getTicketCategoryId(), c.getTicketCategoryId()))
                    .findFirst();
                WhitelistConfiguration conf;
                if(existing.isPresent()) {
                    conf = whitelistManager.updateConfiguration(existing.get().getId(), body);
                } else {
                    conf = whitelistManager.createConfiguration(listId, event.getId(), body);
                }
                return ResponseEntity.ok(conf.getId());
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private boolean notOwner(String username, int organizationId) {
        return !optionally(() -> userManager.findUserByUsername(username))
            .filter(user -> userManager.isOwnerOfOrganization(user, organizationId))
            .isPresent();
    }

}
