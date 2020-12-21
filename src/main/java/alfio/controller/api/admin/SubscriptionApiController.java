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

import alfio.manager.SubscriptionManager;
import alfio.manager.user.UserManager;
import alfio.model.SubscriptionDescriptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/admin/api/organization/{organizationId}/subscription")
public class SubscriptionApiController {

    private final SubscriptionManager subscriptionManager;
    private final UserManager userManager;

    public SubscriptionApiController(SubscriptionManager subscriptionManager, UserManager userManager) {
        this.subscriptionManager = subscriptionManager;
        this.userManager = userManager;
    }

    @GetMapping("/list")
    ResponseEntity<List<SubscriptionDescriptor>> findAll(@PathVariable("organizationId") int organizationId, Principal principal) {
        if (userManager.isOwnerOfOrganization(principal.getName(), organizationId)) {
            return ResponseEntity.ok(subscriptionManager.findAll(organizationId));
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

    }

    @PostMapping("/")
    public void create(@PathVariable("organizationId") int organizationId, Principal principal) {
    }
}
