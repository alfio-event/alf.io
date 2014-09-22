/**
 * This file is part of bagarino.
 *
 * bagarino is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bagarino is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.bagarino.controller.api;

import io.bagarino.manager.EventManager;
import io.bagarino.manager.user.UserManager;
import io.bagarino.model.Event;
import io.bagarino.model.modification.EventModification;
import io.bagarino.model.modification.OrganizationModification;
import io.bagarino.model.modification.UserModification;
import io.bagarino.model.transaction.PaymentProxy;
import io.bagarino.model.user.Organization;
import io.bagarino.model.user.User;
import io.bagarino.util.ValidationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/admin/api")
public class AdminApiController {

    private static final String OK = "OK";
    private final UserManager userManager;
    private final EventManager eventManager;

    @Autowired
    public AdminApiController(UserManager userManager, EventManager eventManager) {
        this.userManager = userManager;
        this.eventManager = eventManager;
    }

    @RequestMapping(value = "/organizations", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public List<Organization> getAllOrganizations(Principal principal) {
        return userManager.findUserOrganizations(principal.getName());
    }

    @RequestMapping(value = "/paymentProxies", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public List<PaymentProxy> getPaymentProxies() {
        return Arrays.asList(PaymentProxy.values());
    }

    @RequestMapping(value = "/users", method = RequestMethod.GET)
    public List<User> getAllUsers(Principal principal) {
        return userManager.findAllUsers(principal.getName());
    }


    @RequestMapping(value = "/organizations/new", method = RequestMethod.POST)
    public String insertOrganization(@RequestBody OrganizationModification organizationModification) {
        userManager.createOrganization(organizationModification.getName(), organizationModification.getDescription());
        return OK;
    }

    @RequestMapping(value = "/organizations/check", method = RequestMethod.POST)
    public ValidationResult validateOrganization(@RequestBody OrganizationModification organizationModification) {
        return userManager.validateOrganization(organizationModification.getId(), organizationModification.getName(), organizationModification.getDescription());
    }

    @RequestMapping(value = "/users/check", method = RequestMethod.POST)
    public ValidationResult validateUser(@RequestBody UserModification userModification) {
        return userManager.validateUser(userModification.getId(), userModification.getUsername(),
                userModification.getOrganizationId(), userModification.getFirstName(),
                userModification.getLastName(), userModification.getEmailAddress());
    }

    @RequestMapping(value = "/users/new", method = RequestMethod.POST)
    public String insertUser(@RequestBody UserModification userModification) {
        userManager.createUser(userModification.getOrganizationId(), userModification.getUsername(), userModification.getFirstName(), userModification.getLastName(), userModification.getEmailAddress());
        return OK;
    }

    @RequestMapping(value = "/events", method = RequestMethod.GET)
    public List<Event> getAllEvents(Principal principal) {
        return eventManager.getAllEvents(principal.getName());
    }

    @RequestMapping(value = "/events/check", method = RequestMethod.POST)
    public ValidationResult validateEvent(@RequestBody EventModification eventModification) {
        return ValidationResult.success();
    }

    @RequestMapping(value = "/events/new", method = RequestMethod.POST)
    public String insertEvent(@RequestBody EventModification eventModification) {
        eventManager.createEvent(eventModification);
        return OK;
    }
}
