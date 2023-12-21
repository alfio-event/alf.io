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
import alfio.manager.SubscriptionManager;
import alfio.model.modification.SubscriptionDescriptorModification;
import alfio.model.subscription.EventSubscriptionLink;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.model.subscription.SubscriptionDescriptorWithStatistics;
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

    private final SubscriptionManager subscriptionManager;
    private final AccessService accessService;

    public SubscriptionApiController(SubscriptionManager subscriptionManager,
                                     AccessService accessService) {
        this.subscriptionManager = subscriptionManager;
        this.accessService = accessService;
    }

    @GetMapping("/list")
    ResponseEntity<List<SubscriptionDescriptorWithStatistics>> findAll(@PathVariable("organizationId") int organizationId, Principal principal) {
        accessService.checkOrganizationOwnership(principal, organizationId);
        return ResponseEntity.ok(subscriptionManager.loadSubscriptionsWithStatistics(organizationId));
    }

    @GetMapping("/active")
    ResponseEntity<List<SubscriptionDescriptor>> findActive(@PathVariable("organizationId") int organizationId, Principal principal) {
        accessService.checkOrganizationOwnership(principal, organizationId);
        return ResponseEntity.ok(subscriptionManager.loadActiveSubscriptionDescriptors(organizationId));
    }

    @GetMapping("/{subscriptionId}")
    ResponseEntity<SubscriptionDescriptorModification> getSingle(@PathVariable("organizationId") int organizationId,
                                                                 @PathVariable("subscriptionId") UUID subscriptionId,
                                                                 Principal principal) {
        accessService.checkOrganizationOwnership(principal, organizationId);
        return ResponseEntity.of(subscriptionManager.findOne(subscriptionId, organizationId).map(SubscriptionDescriptorModification::fromModel));
    }

    @PostMapping("/")
    ResponseEntity<UUID> create(@PathVariable("organizationId") int organizationId,
                                @RequestBody SubscriptionDescriptorModification subscriptionDescriptor,
                                Principal principal) {

        accessService.checkOrganizationOwnership(principal, organizationId);

        if (organizationId != subscriptionDescriptor.getOrganizationId()) {
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
        accessService.checkOrganizationOwnership(principal, organizationId);
        if (organizationId == subscriptionDescriptor.getOrganizationId() && subscriptionId.equals(subscriptionDescriptor.getId())) {
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
        accessService.checkOrganizationOwnership(principal, organizationId);
        return ResponseEntity.ok(subscriptionManager.setPublicStatus(subscriptionId, organizationId, status));
    }

    @GetMapping("/{subscriptionId}/events")
    ResponseEntity<List<EventSubscriptionLink>> getLinkedEvents(@PathVariable("organizationId") int organizationId,
                                                                @PathVariable("subscriptionId") UUID subscriptionId,
                                                                Principal principal) {
        accessService.checkOrganizationOwnership(principal, organizationId);
        return ResponseEntity.ok(subscriptionManager.getLinkedEvents(organizationId, subscriptionId));
    }

    /**
     * Deactivates a subscription descriptor and removes the link with events, if any.
     *
     * This will ensure that existing reservations made or ticket registered using this subscription won't have
     * to be deleted.
     *
     * @param organizationId
     * @param descriptorId
     * @param principal
     * @return
     */
    @DeleteMapping("/{subscriptionId}")
    ResponseEntity<Void> deactivate(@PathVariable("organizationId") int organizationId,
                                    @PathVariable("subscriptionId") UUID descriptorId,
                                    Principal principal) {
        accessService.checkOrganizationOwnership(principal, organizationId);
        return deactivateSubscriptionDescriptor(organizationId, descriptorId, subscriptionManager);
    }

    public static ResponseEntity<Void> deactivateSubscriptionDescriptor(int organizationId,
                                                                         UUID descriptorId,
                                                                         SubscriptionManager subscriptionManager) {
        var result = subscriptionManager.deactivateDescriptor(organizationId, descriptorId);
        if (result.isSuccess()) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    public static List<String> loadLinkedEvents(List<EventSubscriptionLink> eventSubscriptionLinks) {
        return eventSubscriptionLinks.stream()
            .map(EventSubscriptionLink::getEventShortName)
            .collect(Collectors.toList());
    }
}
