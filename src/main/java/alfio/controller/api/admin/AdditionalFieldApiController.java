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
import alfio.manager.PurchaseContextFieldManager;
import alfio.manager.PurchaseContextManager;
import alfio.model.*;
import alfio.model.modification.AdditionalFieldRequest;
import alfio.model.modification.EventModification;
import alfio.model.modification.TicketFieldDescriptionModification;
import alfio.model.result.ValidationResult;
import alfio.repository.DynamicFieldTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

@RestController
@RequestMapping("/admin/api/{purchaseContextType}/{publicIdentifier}/additional-field")
public class AdditionalFieldApiController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdditionalFieldApiController.class);
    private final PurchaseContextManager purchaseContextManager;
    private final PurchaseContextFieldManager purchaseContextFieldManager;
    private final AccessService accessService;
    private final DynamicFieldTemplateRepository dynamicFieldTemplateRepository;

    public AdditionalFieldApiController(PurchaseContextManager purchaseContextManager,
                                        PurchaseContextFieldManager purchaseContextFieldManager,
                                        AccessService accessService,
                                        DynamicFieldTemplateRepository dynamicFieldTemplateRepository) {
        this.purchaseContextManager = purchaseContextManager;
        this.purchaseContextFieldManager = purchaseContextFieldManager;
        this.accessService = accessService;
        this.dynamicFieldTemplateRepository = dynamicFieldTemplateRepository;
    }

    @GetMapping("/templates")
    public List<DynamicFieldTemplate> loadTemplates(@PathVariable("purchaseContextType") PurchaseContext.PurchaseContextType purchaseContextType,
                                                    @PathVariable("publicIdentifier") String publicIdentifier) {
        LOGGER.trace("Loading templates for {} {}", purchaseContextType, publicIdentifier);
        return dynamicFieldTemplateRepository.loadAll();
    }

    @GetMapping()
    public List<FieldConfigurationAndAllDescriptions> getAllAdditionalField(@PathVariable("purchaseContextType") PurchaseContext.PurchaseContextType purchaseContextType,
                                                                            @PathVariable("publicIdentifier") String publicIdentifier,
                                                                            Principal principal) {
        accessService.checkPurchaseContextOwnership(principal, purchaseContextType, publicIdentifier);
        var purchaseContext = purchaseContextManager.findBy(purchaseContextType, publicIdentifier).orElseThrow();
        final Map<Long, List<PurchaseContextFieldDescription>> descById = purchaseContextFieldManager.findDescriptionsGroupedByFieldId(purchaseContext);
        return purchaseContextFieldManager.findAdditionalFields(purchaseContext).stream()
            .map(field -> new FieldConfigurationAndAllDescriptions(field, descById.getOrDefault(field.getId(), Collections.emptyList())))
            .collect(toList());
    }

    @GetMapping("/{id}/stats")
    public List<RestrictedValueStats> getStats(@PathVariable("purchaseContextType") PurchaseContext.PurchaseContextType purchaseContextType,
                                               @PathVariable("publicIdentifier") String publicIdentifier,
                                               @PathVariable("id") long id,
                                               Principal principal) {
        //
        accessService.checkPurchaseContextOwnership(principal, purchaseContextType, publicIdentifier);
        //
        return purchaseContextFieldManager.retrieveStats(id);
    }

    @PostMapping(
        // "/events/{eventName}/additional-field/new" // old
        "/new"
    )
    public ValidationResult addAdditionalField(@PathVariable("purchaseContextType") PurchaseContext.PurchaseContextType purchaseContextType,
                                               @PathVariable("publicIdentifier") String publicIdentifier,
                                               @RequestBody AdditionalFieldRequest field,
                                               Principal principal,
                                               Errors errors) {
        //
        accessService.checkPurchaseContextOwnership(principal, purchaseContextType, publicIdentifier);
        //

        var purchaseContext = purchaseContextManager.findBy(purchaseContextType, publicIdentifier).orElseThrow();
        return purchaseContextFieldManager.validateAndAddField(purchaseContext, field, errors);
    }

    @PostMapping(
        // "/events/{eventName}/additional-field/descriptions" // old
        "/descriptions"
    )
    public void saveAdditionalFieldDescriptions(@PathVariable("purchaseContextType") PurchaseContext.PurchaseContextType purchaseContextType,
                                                @PathVariable("publicIdentifier") String publicIdentifier,
                                                @RequestBody Map<String, TicketFieldDescriptionModification> descriptions, Principal principal) {
        //
        accessService.checkPurchaseContextOwnershipAndTicketAdditionalFieldIds(principal, purchaseContextType, publicIdentifier, descriptions.values().stream().map(TicketFieldDescriptionModification::getTicketFieldConfigurationId).collect(Collectors.toSet()));
        //
        purchaseContextFieldManager.updateFieldDescriptions(descriptions, purchaseContextManager.getOrganizationId(purchaseContextType, publicIdentifier));
    }

    @PostMapping(
        // "/events/{eventName}/additional-field/swap-position/{id1}/{id2}" // old
        "/swap-position/{id1}/{id2}"
    )
    public void swapAdditionalFieldPosition(@PathVariable("purchaseContextType") PurchaseContext.PurchaseContextType purchaseContextType,
                                            @PathVariable("publicIdentifier") String publicIdentifier,
                                            @PathVariable("id1") long id1,
                                            @PathVariable("id2") long id2,
                                            Principal principal) {
        //
        accessService.checkPurchaseContextOwnershipAndTicketAdditionalFieldIds(principal, purchaseContextType, publicIdentifier, Set.of(id1, id2));
        //
        purchaseContextFieldManager.swapAdditionalFieldPosition(id1, id2);
    }

    @PostMapping(
        // "/events/{eventName}/additional-field/set-position/{id}" // old
        "/set-position/{id}"
    )
    public void setAdditionalFieldPosition(@PathVariable("purchaseContextType") PurchaseContext.PurchaseContextType purchaseContextType,
                                           @PathVariable("publicIdentifier") String publicIdentifier,
                                           @PathVariable("id") long id,
                                           @RequestParam("newPosition") int newPosition,
                                           Principal principal) {
        //
        accessService.checkPurchaseContextOwnershipAndTicketAdditionalFieldIds(principal, purchaseContextType, publicIdentifier, Set.of(id));
        //
        purchaseContextFieldManager.setAdditionalFieldPosition(id, newPosition);
    }

    @DeleteMapping(
        // "/events/{eventName}/additional-field/{id}" // old
        "/{id}"
    )
    public void deleteAdditionalField(@PathVariable("purchaseContextType") PurchaseContext.PurchaseContextType purchaseContextType,
                                      @PathVariable("publicIdentifier") String publicIdentifier,
                                      @PathVariable("id") long id,
                                      Principal principal) {
        //
        accessService.checkPurchaseContextOwnershipAndTicketAdditionalFieldIds(principal, purchaseContextType, publicIdentifier, Set.of(id));
        //
        purchaseContextFieldManager.deleteAdditionalField(id);
    }

    @PostMapping("/{id}")
    public void updateAdditionalField(@PathVariable("purchaseContextType") PurchaseContext.PurchaseContextType purchaseContextType,
                                      @PathVariable("publicIdentifier") String publicIdentifier,
                                      @PathVariable("id") long id,
                                      @RequestBody EventModification.UpdateAdditionalField field,
                                      Principal principal) {
        //
        accessService.checkPurchaseContextOwnershipAndTicketAdditionalFieldIds(principal, purchaseContextType, publicIdentifier, Set.of(id));
        //
        purchaseContextFieldManager.updateAdditionalField(id, field, purchaseContextManager.getOrganizationId(purchaseContextType, publicIdentifier));
    }
}
