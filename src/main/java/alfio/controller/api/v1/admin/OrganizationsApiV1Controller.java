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

import alfio.manager.OrganizationDeleter;
import alfio.manager.user.UserManager;
import alfio.model.modification.OrganizationModification;
import alfio.model.user.Organization;
import alfio.model.user.Role;
import alfio.model.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/system/organization")
public class OrganizationsApiV1Controller {
    private final UserManager userManager;
    private final OrganizationDeleter organizationDeleter;

    @Autowired
    public OrganizationsApiV1Controller(UserManager userManager,
                                        OrganizationDeleter organizationDeleter) {
        this.userManager = userManager;
        this.organizationDeleter = organizationDeleter;
    }

    @PostMapping("/create")
    public Organization createOrganization(@RequestBody OrganizationModification om) {
        int orgId = userManager.createOrganization(om);
        return userManager.findOrganizationById(orgId, UserManager.ADMIN_USERNAME);
    }

    @GetMapping("/list")
    @ResponseStatus(HttpStatus.OK)
    public List<Organization> getAllOrganizations() {
        return userManager.findUserOrganizations(UserManager.ADMIN_USERNAME);
    }

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public Organization getSingleOrganization(@PathVariable("id") int organizationId) {
        return userManager.findOrganizationById(organizationId, UserManager.ADMIN_USERNAME);
    }

    @PutMapping("/{id}/api-key")
    public OrganizationApiKey createApiKeyForOrganization(@PathVariable("id") int organizationId) {
        var user = userManager.insertUser(organizationId, null, null, null, null, Role.fromRoleName("ROLE_API_CLIENT"), User.Type.API_KEY, null, "Auto Generated API Key");
        return new OrganizationApiKey(organizationId, user.getUsername());
    }

    @PostMapping("/{id}")
    public ResponseEntity<Organization> update(@PathVariable("id") int organizationId,
                                               @RequestBody OrganizationModification om,
                                               Principal principal) {
        if (om == null || !om.isValid() || organizationId != om.getId()) {
            return ResponseEntity.badRequest().build();
        }
        userManager.updateOrganization(om, principal);
        return ResponseEntity.ok(userManager.findOrganizationById(organizationId, UserManager.ADMIN_USERNAME));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") int organizationId, Principal principal) {
        boolean result = organizationDeleter.deleteOrganization(organizationId, principal);
        if (result) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    static class OrganizationApiKey {
        private final int organizationId;
        private final String apiKey;

        OrganizationApiKey(int organizationId, String apiKey) {
            this.organizationId = organizationId;
            this.apiKey = apiKey;
        }

        public int getOrganizationId() {
            return organizationId;
        }

        public String getApiKey() {
            return apiKey;
        }
    }
}
