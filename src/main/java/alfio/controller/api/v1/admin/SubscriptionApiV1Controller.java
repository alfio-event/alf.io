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
package alfio.controller.api.v1.admin;

import alfio.controller.api.admin.SubscriptionApiController;
import alfio.manager.*;
import alfio.manager.user.UserManager;
import alfio.model.api.v1.admin.SubscriptionDescriptorModificationRequest;
import alfio.model.modification.SubscriptionDescriptorModification;
import alfio.model.subscription.SubscriptionDescriptorWithStatistics;
import alfio.util.Json;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static alfio.controller.api.admin.SubscriptionApiController.loadLinkedEvents;

@RestController
@RequestMapping("/api/v1/admin/subscription")
@Transactional
public class SubscriptionApiV1Controller {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionApiV1Controller.class);
    private final SubscriptionManager subscriptionManager;
    private final FileUploadManager fileUploadManager;
    private final FileDownloadManager fileDownloadManager;
    private final UserManager userManager;
    private final EventManager eventManager;
    private final AccessService accessService;
    private final PurchaseContextFieldManager purchaseContextFieldManager;

    public SubscriptionApiV1Controller(SubscriptionManager subscriptionManager,
                                       FileUploadManager fileUploadManager,
                                       FileDownloadManager fileDownloadManager,
                                       UserManager userManager,
                                       EventManager eventManager,
                                       AccessService accessService,
                                       PurchaseContextFieldManager purchaseContextFieldManager) {
        this.subscriptionManager = subscriptionManager;
        this.fileUploadManager = fileUploadManager;
        this.fileDownloadManager = fileDownloadManager;
        this.userManager = userManager;
        this.eventManager = eventManager;
        this.accessService = accessService;
        this.purchaseContextFieldManager = purchaseContextFieldManager;
    }

    @PostMapping("/create")
    public ResponseEntity<String> create(@RequestBody SubscriptionDescriptorModificationRequest request, Principal principal) {
        var organization = userManager.findUserOrganizations(principal.getName()).get(0);
        String imageRef = null;
        if(StringUtils.isNotEmpty(request.getImageUrl())) {
            imageRef = fetchImage(request.getImageUrl());
        }
        var modification = request.toDescriptorModification(null, organization.getId(), imageRef)
            .flatMap(SubscriptionDescriptorModification::validate);
        var additionalFields = request.toAdditionalFieldsRequest();
        if (modification.isSuccess() && additionalFields.isSuccess()) {
            // request is valid
            var optionalId = subscriptionManager.createSubscriptionDescriptor(modification.getData());
            if (optionalId.isPresent()) {
                var id = optionalId.get();
                purchaseContextFieldManager.addAdditionalFields(subscriptionManager.getSubscriptionById(id).orElseThrow(), additionalFields.getData());
            }
            return optionalId.map(uuid -> ResponseEntity.ok(uuid.toString()))
                .orElseGet(() -> ResponseEntity.internalServerError().build());
        }
        var errors = new ArrayList<>(modification.getErrors());
        errors.addAll(additionalFields.getErrors());
        return ResponseEntity.badRequest().body(Json.toJson(errors));
    }

    @PostMapping("/{subscriptionId}/update")
    public ResponseEntity<String> update(@PathVariable("subscriptionId") UUID subscriptionId,
                                         @RequestBody SubscriptionDescriptorModificationRequest request, Principal principal) {
        accessService.checkSubscriptionDescriptorOwnership(principal, subscriptionId.toString());
        var organization = userManager.findUserOrganizations(principal.getName()).get(0);
        String imageRef = null;
        if(StringUtils.isNotEmpty(request.getImageUrl())) {
            imageRef = fetchImage(request.getImageUrl());
        }
        var modification = request.toDescriptorModification(subscriptionId, organization.getId(), imageRef)
            .flatMap(SubscriptionDescriptorModification::validate);
        if (modification.isSuccess()) {
            // request is valid
            var optionalId = subscriptionManager.updateSubscriptionDescriptor(modification.getData());
            return optionalId.map(uuid -> ResponseEntity.ok(uuid.toString()))
                .orElseGet(() -> ResponseEntity.internalServerError().build());
        }
        return ResponseEntity.badRequest().body(Json.toJson(modification.getErrors()));
    }

    @GetMapping("/{subscriptionId}")
    public ResponseEntity<SubscriptionDescriptorWithStatistics> get(@PathVariable("subscriptionId") UUID subscriptionId,
                                                                    Principal principal) {
        accessService.checkSubscriptionDescriptorOwnership(principal, subscriptionId.toString());
        var organization = userManager.findUserOrganizations(principal.getName()).get(0);
        return ResponseEntity.of(subscriptionManager.loadSubscriptionWithStatistics(subscriptionId, organization.getId()));
    }

    @GetMapping("/{subscriptionId}/events")
    public ResponseEntity<List<String>> getLinkedEvents(@PathVariable("subscriptionId") UUID subscriptionId,
                                                        Principal principal) {
        accessService.checkSubscriptionDescriptorOwnership(principal, subscriptionId.toString());
        var organization = userManager.findUserOrganizations(principal.getName()).get(0);
        return ResponseEntity.ok(loadLinkedEvents(subscriptionManager.getLinkedEvents(organization.getId(), subscriptionId)));
    }

    @PostMapping("/{subscriptionId}/events")
    public ResponseEntity<List<String>> updateLinkedEvents(@PathVariable("subscriptionId") UUID subscriptionId,
                                                           @RequestBody List<String> eventSlugs,
                                                           Principal principal) {
        if (eventSlugs == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        accessService.checkEventLinkRequest(principal, subscriptionId.toString(), eventSlugs);
        var organization = userManager.findUserOrganizations(principal.getName()).get(0);
        int organizationId = organization.getId();

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
    }

    @DeleteMapping("/{subscriptionId}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable("subscriptionId") UUID descriptorId, Principal principal) {
        accessService.checkSubscriptionDescriptorOwnership(principal, descriptorId.toString());
        var organization = userManager.findUserOrganizations(principal.getName()).get(0);
        int organizationId = organization.getId();
        return SubscriptionApiController.deactivateSubscriptionDescriptor(organizationId, descriptorId, subscriptionManager);
    }

    private String fetchImage(String url) {
        if(url != null) {
            FileDownloadManager.DownloadedFile file = fileDownloadManager.downloadFile(url);
            return file != null ? fileUploadManager.insertFile(file.toUploadBase64FileModification()) : null;
        } else {
            return null;
        }
    }
}
