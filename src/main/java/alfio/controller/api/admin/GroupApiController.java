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
import alfio.manager.EventManager;
import alfio.manager.GroupManager;
import alfio.manager.GroupManager.DuplicateGroupItemException;
import alfio.model.EventAndOrganizationId;
import alfio.model.group.Group;
import alfio.model.group.LinkedGroup;
import alfio.model.modification.GroupModification;
import alfio.model.modification.LinkedGroupModification;
import alfio.model.result.ErrorCode;
import alfio.model.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("/admin/api/group")
@RequiredArgsConstructor
public class GroupApiController {

    private final GroupManager groupManager;
    private final EventManager eventManager;
    private final AccessService accessService;

    @ExceptionHandler(DuplicateGroupItemException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleDuplicateGroupItemException(DuplicateGroupItemException exc) {
        return exc.getMessage();
    }

    @GetMapping("/for/{organizationId}")
    public ResponseEntity<List<Group>> loadAllGroupsForOrganization(@PathVariable("organizationId") int organizationId, @RequestParam(name = "showAll", defaultValue = "false", required = false) boolean showAll, Principal principal) {
        accessService.checkOrganizationOwnership(principal, organizationId);
        if (showAll) {
            return ResponseEntity.ok(groupManager.getAllForOrganization(organizationId));
        } else {
            return ResponseEntity.ok(groupManager.getAllActiveForOrganization(organizationId));
        }
    }

    @GetMapping("/for/{organizationId}/detail/{listId}")
    public ResponseEntity<GroupModification> loadDetail(@PathVariable("organizationId") int organizationId, @PathVariable("listId") int listId, Principal principal) {
        accessService.checkGroupOwnership(principal, listId, organizationId);
        return groupManager.loadComplete(listId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/for/{organizationId}/update/{groupId}")
    public ResponseEntity<GroupModification> updateGroup(@PathVariable("organizationId") int organizationId,
                                                         @PathVariable("groupId") int listId,
                                                         @RequestBody GroupModification modification,
                                                         Principal principal) {
        accessService.checkGroupUpdateRequest(principal, listId, organizationId, modification);
        return groupManager.update(listId, modification).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/for/{organizationId}/new")
    public ResponseEntity<String> createNew(@PathVariable("organizationId") int organizationId, @RequestBody GroupModification request, Principal principal) {
        accessService.checkGroupCreateRequest(principal, organizationId, request);
        if(request.getOrganizationId() != organizationId) {
            return ResponseEntity.badRequest().build();
        }
        Result<Integer> result = groupManager.createNew(request);
        if(result.isSuccess()) {
            return ResponseEntity.ok(String.valueOf(result.getData()));
        }

        ErrorCode error = result.getFirstErrorOrNull();
        if(error != null && error.getCode().equals("value.duplicate")) {
            return ResponseEntity.badRequest().body(error.getDescription());
        }
        return ResponseEntity.badRequest().build();
    }

    @GetMapping("/for/event/{eventName}/all")
    public ResponseEntity<List<LinkedGroup>> findLinked(@PathVariable("eventName") String eventName,
                                                        Principal principal) {
        accessService.checkEventMembership(principal, eventName, AccessService.MEMBERSHIP_ROLES);
        return eventManager.getOptionalEventAndOrganizationIdByName(eventName, principal.getName())
            .map(event -> ResponseEntity.ok(groupManager.getLinksForEvent(event.getId())))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/for/event/{eventName}")
    public ResponseEntity<LinkedGroup> findActiveGroup(@PathVariable("eventName") String eventName,
                                                       Principal principal) {
        accessService.checkEventOwnership(principal, eventName);
        return eventManager.getOptionalEventAndOrganizationIdByName(eventName, principal.getName())
            .map(event -> {
                Optional<LinkedGroup> configuration = groupManager.getLinksForEvent(event.getId()).stream()
                    .filter(c -> c.getTicketCategoryId() == null)
                    .findFirst();
                return configuration.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/for/event/{eventName}/category/{categoryId}")
    public ResponseEntity<LinkedGroup> findActiveGroup(@PathVariable("eventName") String eventName,
                                                       @PathVariable("categoryId") int categoryId,
                                                       Principal principal) {
        accessService.checkCategoryOwnership(principal, eventName, categoryId);
        return eventManager.getOptionalEventAndOrganizationIdByName(eventName, principal.getName())
            .map(event -> {
                Optional<LinkedGroup> configuration = groupManager.findLinks(event.getId(), categoryId)
                    .stream()
                    .filter(c -> c.getTicketCategoryId() != null && c.getTicketCategoryId() == categoryId)
                    .findFirst();
                return configuration.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{groupId}/link")
    public ResponseEntity<Integer> linkGroup(@PathVariable("groupId") int groupId, @RequestBody LinkedGroupModification body, Principal principal) {
        if(body == null || groupId != body.getGroupId()) {
            return ResponseEntity.badRequest().build();
        }

        EventAndOrganizationId event;

        if (body.getTicketCategoryId() != null) {
            event = accessService.checkCategoryOwnership(principal, body.getEventId(), body.getTicketCategoryId());
        } else {
            event = accessService.checkEventOwnership(principal, body.getEventId());
        }

        Optional<LinkedGroup> existing = groupManager.getLinksForEvent(event.getId())
            .stream()
            .filter(c -> Objects.equals(body.getTicketCategoryId(), c.getTicketCategoryId()))
            .findFirst();
        LinkedGroup link;
        if(existing.isPresent()) {
            link = groupManager.updateLink(existing.get().getId(), body);
        } else {
            link = groupManager.createLink(groupId, event.getId(), body);
        }
        return ResponseEntity.ok(link.getId());
    }

    @DeleteMapping("/for/{organizationId}/event/{eventId}/link/{configurationId}")
    public ResponseEntity<String> unlinkGroup(@PathVariable("organizationId") int organizationId,
                                              @PathVariable("configurationId") int configurationId,
                                              @PathVariable("eventId") int eventId,
                                              @RequestParam(name = "categoryId", required = false) Integer categoryId,
                                              Principal principal) {
        accessService.checkGroupLinkOwnership(principal, configurationId, organizationId, eventId, categoryId);
        groupManager.disableLink(configurationId);
        return ResponseEntity.ok("OK");
    }

    @DeleteMapping("/for/{organizationId}/id/{groupId}/member/{memberId}")
    public ResponseEntity<Boolean> deactivateMember(@PathVariable("groupId") int groupId,
                                                    @PathVariable("memberId") int memberId,
                                                    @PathVariable("organizationId") int organizationId,
                                                    Principal principal) {
        accessService.checkGroupOwnership(principal, groupId, organizationId);
        return ResponseEntity.ok(groupManager.deactivateMembers(Collections.singletonList(memberId), groupId));
    }

    @DeleteMapping("/for/{organizationId}/id/{groupId}")
    public ResponseEntity<Boolean> deactivateGroup(@PathVariable("groupId") int groupId,
                                                   @PathVariable("organizationId") int organizationId,
                                                   Principal principal) {
        accessService.checkGroupOwnership(principal, groupId, organizationId);
        return ResponseEntity.ok(groupManager.deactivateGroup(groupId));
    }

}
