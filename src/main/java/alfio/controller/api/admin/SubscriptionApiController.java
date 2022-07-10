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
import alfio.manager.SubscriptionManager;
import alfio.manager.user.UserManager;
import alfio.model.modification.SubscriptionDescriptorModification;
import alfio.model.subscription.EventSubscriptionLink;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.model.subscription.SubscriptionDescriptorWithStatistics;
import alfio.util.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/api/organization/{organizationId}/subscription")
public class SubscriptionApiController {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionApiController.class);
    private final SubscriptionManager subscriptionManager;
    private final UserManager userManager;
    private final EventManager eventManager;

    public SubscriptionApiController(SubscriptionManager subscriptionManager,
                                     UserManager userManager,
                                     EventManager eventManager) {
        this.subscriptionManager = subscriptionManager;
        this.userManager = userManager;
        this.eventManager = eventManager;
    }

    @GetMapping("/list")
    ResponseEntity<List<SubscriptionDescriptorWithStatistics>> findAll(@PathVariable("organizationId") int organizationId, Principal principal) {
        if (userManager.isOwnerOfOrganization(principal.getName(), organizationId)) {
            return ResponseEntity.ok(subscriptionManager.loadSubscriptionsWithStatistics(organizationId));
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

    }

    @GetMapping("/active")
    ResponseEntity<List<SubscriptionDescriptor>> findActive(@PathVariable("organizationId") int organizationId, Principal principal) {
        if (userManager.isOwnerOfOrganization(principal.getName(), organizationId)) {
            return ResponseEntity.ok(subscriptionManager.loadActiveSubscriptionDescriptors(organizationId));
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @GetMapping("/{subscriptionId}")
    ResponseEntity<SubscriptionDescriptorModification> getSingle(@PathVariable("organizationId") int organizationId,
                                                                 @PathVariable("subscriptionId") UUID subscriptionId,
                                                                 Principal principal) {
        if(userManager.isOwnerOfOrganization(principal.getName(), organizationId)) {
            return ResponseEntity.of(subscriptionManager.findOne(subscriptionId, organizationId).map(SubscriptionDescriptorModification::fromModel));
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping("/")
    ResponseEntity<UUID> create(@PathVariable("organizationId") int organizationId,
                                @RequestBody SubscriptionDescriptorModification subscriptionDescriptor,
                                Principal principal) {

        if (organizationId != subscriptionDescriptor.getOrganizationId()
            || !userManager.isOwnerOfOrganization(principal.getName(), subscriptionDescriptor.getOrganizationId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        var result = SubscriptionDescriptorModification.validate(subscriptionDescriptor);
        if (result.isSuccess()) {
            return ResponseEntity.of(subscriptionManager.createSubscriptionDescriptor(subscriptionDescriptor));
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{subscriptionId}")
    ResponseEntity<UUID> update(@PathVariable("organizationId") int organizationId,
                                @PathVariable("subscriptionId") UUID subscriptionId,
                                @RequestBody SubscriptionDescriptorModification subscriptionDescriptor,
                                Principal principal) {
        if (organizationId == subscriptionDescriptor.getOrganizationId()
            && userManager.isOwnerOfOrganization(principal.getName(), subscriptionDescriptor.getOrganizationId())
            && subscriptionId.equals(subscriptionDescriptor.getId())
        ) {
            return ResponseEntity.of(subscriptionManager.updateSubscriptionDescriptor(subscriptionDescriptor));
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @PatchMapping("/{subscriptionId}/is-public")
    ResponseEntity<Boolean> setPublicState(@PathVariable("organizationId") int organizationId,
                                           @PathVariable("subscriptionId") UUID subscriptionId,
                                           @RequestParam("status") boolean status,
                                           Principal principal) {
        if (userManager.isOwnerOfOrganization(principal.getName(), organizationId)) {
            return ResponseEntity.ok(subscriptionManager.setPublicStatus(subscriptionId, organizationId, status));
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @GetMapping("/{subscriptionId}/events")
    ResponseEntity<List<String>> getLinkedEvents(@PathVariable("organizationId") int organizationId,
                                                                @PathVariable("subscriptionId") UUID subscriptionId,
                                                                Principal principal) {
        if(userManager.isOwnerOfOrganization(principal.getName(), organizationId)) {
            return ResponseEntity.ok(loadLinkedEvents(subscriptionManager.getLinkedEvents(organizationId, subscriptionId)));
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @PostMapping("/{subscriptionId}/events")
    ResponseEntity<List<String>> updateLinkedEvents(@PathVariable("organizationId") int organizationId,
                                                                   @PathVariable("subscriptionId") UUID subscriptionId,
                                                                   @RequestBody List<String> eventSlugs,
                                                                   Principal principal) {
        if (eventSlugs == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if(userManager.isOwnerOfOrganization(principal.getName(), organizationId)) {
            List<Integer> eventIds = List.of();
            if (!eventSlugs.isEmpty()) {
                eventIds = eventManager.getEventIdsBySlug(eventSlugs, organizationId);
            }
            var result = subscriptionManager.updateLinkedEvents(organizationId, subscriptionId, eventIds);
            if (result.isSuccess()) {
                return ResponseEntity.ok(loadLinkedEvents(result.getData()));
            } else {
                if (log.isWarnEnabled()) {
                    log.warn("Cannot update linked events {}", Json.toJson(result.getErrors()));
                }
                return ResponseEntity.badRequest().build();
            }
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    private List<String> loadLinkedEvents(List<EventSubscriptionLink> eventSubscriptionLinks) {
        return eventSubscriptionLinks.stream()
            .map(EventSubscriptionLink::getEventShortName)
            .collect(Collectors.toList());
    }
}
