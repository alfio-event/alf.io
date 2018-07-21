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

import alfio.manager.AttendeeListManager;
import alfio.manager.EventManager;
import alfio.manager.user.UserManager;
import alfio.model.attendeelist.AttendeeList;
import alfio.model.attendeelist.AttendeeListConfiguration;
import alfio.model.modification.AttendeeListConfigurationModification;
import alfio.model.modification.AttendeeListModification;
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
@RequestMapping("/admin/api/attendee-list")
@RequiredArgsConstructor
public class AttendeeListApiController {

    private final AttendeeListManager attendeeListManager;
    private final UserManager userManager;
    private final EventManager eventManager;

    @GetMapping("/{organizationId}")
    public ResponseEntity<List<AttendeeList>> loadAllAttendeeListsForOrganization(@PathVariable("organizationId") int organizationId, Principal principal) {
        if(notOwner(principal.getName(), organizationId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(attendeeListManager.getAllForOrganization(organizationId));
    }

    @GetMapping("/{organizationId}/detail/{listId}")
    public ResponseEntity<AttendeeListModification> loadDetail(@PathVariable("organizationId") int organizationId, @PathVariable("listId") int listId, Principal principal) {
        if(notOwner(principal.getName(), organizationId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return attendeeListManager.loadComplete(listId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{organizationId}/new")
    public ResponseEntity<Integer> createNew(@PathVariable("organizationId") int organizationId, @RequestBody AttendeeListModification request, Principal principal) {
        if(notOwner(principal.getName(), organizationId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if(request.getOrganizationId() != organizationId) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(attendeeListManager.createNew(request));
    }

    @GetMapping("/event/{eventName}")
    public ResponseEntity<AttendeeListConfiguration> findActiveList(@PathVariable("eventName") String eventName,
                                                                    Principal principal) {
        return eventManager.getOptionalByName(eventName, principal.getName())
            .map(event -> {
                Optional<AttendeeListConfiguration> configuration = attendeeListManager.getConfigurationsForEvent(event.getId()).stream()
                    .filter(c -> c.getTicketCategoryId() == null)
                    .findFirst();
                return configuration.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/event/{eventName}/category/{categoryId}")
    public ResponseEntity<AttendeeListConfiguration> findActiveList(@PathVariable("eventName") String eventName,
                                                                    @PathVariable("categoryId") int categoryId,
                                                                    Principal principal) {
        return eventManager.getOptionalByName(eventName, principal.getName())
            .map(event -> {
                Optional<AttendeeListConfiguration> configuration = attendeeListManager.findConfigurations(event.getId(), categoryId).stream().findFirst();
                return configuration.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{listId}/link")
    public ResponseEntity<Integer> linkList(@PathVariable("listId") int listId, @RequestBody AttendeeListConfigurationModification body, Principal principal) {
        if(body == null || listId != body.getAttendeeListId()) {
            return ResponseEntity.badRequest().build();
        }

        return optionally(() -> eventManager.getSingleEventById(body.getEventId(), principal.getName()))
            .map(event -> {
                Optional<AttendeeListConfiguration> existing = attendeeListManager.getConfigurationsForEvent(event.getId())
                    .stream()
                    .filter(c -> c.getAttendeeListId() == listId && Objects.equals(body.getTicketCategoryId(), c.getTicketCategoryId()))
                    .findFirst();
                AttendeeListConfiguration conf;
                if(existing.isPresent()) {
                    conf = attendeeListManager.updateConfiguration(existing.get().getId(), body);
                } else {
                    conf = attendeeListManager.createConfiguration(listId, event.getId(), body);
                }
                return ResponseEntity.ok(conf.getId());
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{organizationId}/link/{configurationId}")
    public ResponseEntity<String> unlinkList(@PathVariable("organizationId") int organizationId, @PathVariable("configurationId") int configurationId, Principal principal) {
        if(optionally(() -> userManager.findUserByUsername(principal.getName())).filter(u -> userManager.isOwnerOfOrganization(u, organizationId)).isPresent()) {
            attendeeListManager.disableConfiguration(configurationId);
            return ResponseEntity.ok("OK");
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    private boolean notOwner(String username, int organizationId) {
        return !optionally(() -> userManager.findUserByUsername(username))
            .filter(user -> userManager.isOwnerOfOrganization(user, organizationId))
            .isPresent();
    }

}
