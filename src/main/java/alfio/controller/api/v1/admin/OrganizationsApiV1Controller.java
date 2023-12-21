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

import alfio.manager.AccessService;
import alfio.manager.OrganizationDeleter;
import alfio.manager.user.UserManager;
import alfio.model.api.v1.admin.ApiKeyType;
import alfio.model.api.v1.admin.CreateApiKeyRequest;
import alfio.model.api.v1.admin.OrganizationApiKey;
import alfio.model.modification.OrganizationModification;
import alfio.model.user.Organization;
import alfio.model.user.Role;
import alfio.model.user.User;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/system/organization")
public class OrganizationsApiV1Controller {
    private final UserManager userManager;
    private final OrganizationDeleter organizationDeleter;
    private final AccessService accessService;

    @Autowired
    public OrganizationsApiV1Controller(UserManager userManager,
                                        OrganizationDeleter organizationDeleter,
                                        AccessService accessService) {
        this.userManager = userManager;
        this.organizationDeleter = organizationDeleter;
        this.accessService = accessService;
    }

    @PostMapping("/create")
    public ResponseEntity<Organization> createOrganization(@RequestBody OrganizationModification om, Principal principal) {
        accessService.ensureSystemApiKey(principal);
        if (om == null || !om.isValid(true)) {
            return ResponseEntity.badRequest().build();
        }
        int orgId = userManager.createOrganization(om, principal);
        return ResponseEntity.ok(userManager.findOrganizationById(orgId, UserManager.ADMIN_USERNAME));
    }

    @GetMapping("/list")
    public List<Organization> getAllOrganizations(Principal principal) {
        accessService.ensureSystemApiKey(principal);
        return userManager.findUserOrganizations(UserManager.ADMIN_USERNAME);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Organization> getSingleOrganization(@PathVariable("id") int organizationId, Principal principal) {
        accessService.checkOrganizationOwnership(principal, organizationId);
        return ResponseEntity.of(userManager.findOptionalOrganizationById(organizationId, UserManager.ADMIN_USERNAME));
    }

    @PutMapping("/{id}/api-key")
    public ResponseEntity<OrganizationApiKey> createApiKeyForOrganization(@PathVariable("id") int organizationId,
                                                                          @RequestBody(required = false) CreateApiKeyRequest createApiKeyRequest,
                                                                          Principal principal) {
        accessService.checkOrganizationOwnership(principal, organizationId);
        ApiKeyType keyType = ApiKeyType.API_CLIENT;
        String description = CreateApiKeyRequest.DEFAULT_DESCRIPTION;
        if (createApiKeyRequest != null && StringUtils.isNotBlank(createApiKeyRequest.apiKeyType())) {
            var keyTypeOptional = ApiKeyType.safeValueOf(createApiKeyRequest.apiKeyType());
            if (keyTypeOptional.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            keyType = keyTypeOptional.get();
            description = createApiKeyRequest.description();
        }
        var user = userManager.insertUser(organizationId, null, null, null, null, Role.fromRoleName(keyType.roleName()), User.Type.API_KEY, null, description, principal);
        return ResponseEntity.ok(new OrganizationApiKey(organizationId, user.getUsername(), keyType));
    }

    @DeleteMapping("/{id}/api-key/{apiKey}")
    public ResponseEntity<Boolean> deleteApiKeyForOrganization(@PathVariable("id") int organizationId,
                                                               @PathVariable("apiKey") String apiKey,
                                                               Principal principal) {
        accessService.checkOrganizationOwnership(principal, organizationId);
        return ResponseEntity.of(userManager.findUserIdByApiKey(apiKey, organizationId).map(userId -> {
            userManager.deleteUser(userId, principal);
            return true;
        }));
    }

    @PostMapping("/{id}")
    public ResponseEntity<Organization> update(@PathVariable("id") int organizationId,
                                               @RequestBody OrganizationModification om,
                                               Principal principal) {
        accessService.checkOrganizationOwnership(principal, organizationId);
        if (om == null || !om.isValid(false) || organizationId != om.getId()) {
            return ResponseEntity.badRequest().build();
        }
        userManager.updateOrganization(om, principal);
        return ResponseEntity.ok(userManager.findOrganizationById(organizationId, UserManager.ADMIN_USERNAME));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") int organizationId, Principal principal) {
        accessService.checkOrganizationOwnership(principal, organizationId);
        boolean result = organizationDeleter.deleteOrganization(organizationId, principal);
        if (result) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

}
