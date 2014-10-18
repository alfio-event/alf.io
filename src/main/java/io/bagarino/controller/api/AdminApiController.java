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

import io.bagarino.controller.api.support.LocationDescriptor;
import io.bagarino.manager.EventManager;
import io.bagarino.manager.location.LocationManager;
import io.bagarino.manager.system.ConfigurationManager;
import io.bagarino.manager.user.UserManager;
import io.bagarino.model.Event;
import io.bagarino.model.modification.EventModification;
import io.bagarino.model.modification.OrganizationModification;
import io.bagarino.model.modification.UserModification;
import io.bagarino.model.transaction.PaymentProxy;
import io.bagarino.model.user.Organization;
import io.bagarino.model.user.User;
import io.bagarino.util.ValidationResult;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

import static io.bagarino.model.system.ConfigurationKeys.MAPS_CLIENT_API_KEY;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@RestController
@RequestMapping("/admin/api")
public class AdminApiController {

    private static final String OK = "OK";
    private final UserManager userManager;
    private final EventManager eventManager;
    private final LocationManager locationManager;
    private final ConfigurationManager configurationManager;

    @Autowired
    public AdminApiController(UserManager userManager,
                              EventManager eventManager,
                              LocationManager locationManager,
                              ConfigurationManager configurationManager) {
        this.userManager = userManager;
        this.eventManager = eventManager;
        this.locationManager = locationManager;
        this.configurationManager = configurationManager;
    }

    @RequestMapping(value = "/organizations", method = GET)
    @ResponseStatus(HttpStatus.OK)
    public List<Organization> getAllOrganizations(Principal principal) {
        return userManager.findUserOrganizations(principal.getName());
    }

    @RequestMapping(value = "/organizations/{id}", method = GET)
    public Organization getOrganization(@PathVariable("id") int id, Principal principal) {
        return userManager.findOrganizationById(id, principal.getName());
    }

    @RequestMapping(value = "/paymentProxies", method = GET)
    @ResponseStatus(HttpStatus.OK)
    public List<PaymentProxy> getPaymentProxies() {
        return Arrays.asList(PaymentProxy.values());
    }

    @RequestMapping(value = "/users", method = GET)
    public List<User> getAllUsers(Principal principal) {
        return userManager.findAllUsers(principal.getName());
    }


    @RequestMapping(value = "/organizations/new", method = POST)
    public String insertOrganization(@RequestBody OrganizationModification om) {
        userManager.createOrganization(om.getName(), om.getDescription(), om.getEmail());
        return OK;
    }

    @RequestMapping(value = "/organizations/check", method = POST)
    public ValidationResult validateOrganization(@RequestBody OrganizationModification om) {
        return userManager.validateOrganization(om.getId(), om.getName(), om.getEmail(), om.getDescription());
    }

    @RequestMapping(value = "/users/check", method = POST)
    public ValidationResult validateUser(@RequestBody UserModification userModification) {
        return userManager.validateUser(userModification.getId(), userModification.getUsername(),
                userModification.getOrganizationId(), userModification.getFirstName(),
                userModification.getLastName(), userModification.getEmailAddress());
    }

    @RequestMapping(value = "/users/new", method = POST)
    public String insertUser(@RequestBody UserModification userModification) {
        userManager.createUser(userModification.getOrganizationId(), userModification.getUsername(), userModification.getFirstName(), userModification.getLastName(), userModification.getEmailAddress());
        return OK;
    }

    @RequestMapping(value = "/events", method = GET)
    public List<Event> getAllEvents(Principal principal) {
        return eventManager.getAllEvents(principal.getName());
    }

    @RequestMapping(value = "/events/{name}", method = GET)
    public Map<String, Object> getSingleEvent(@PathVariable("name") String eventName, Principal principal) {
        Map<String, Object> out = new HashMap<>();
        final String username = principal.getName();
        final Event event = eventManager.getSingleEvent(eventName, username);
        out.put("event", event);
        out.put("organization", eventManager.loadOrganizer(event, username));
        out.put("ticketCategories", eventManager.loadTicketCategoriesWithStats(event));
        return out;
    }

    @RequestMapping(value = "/events/check", method = POST)
    public ValidationResult validateEvent(@RequestBody EventModification eventModification) {
        return ValidationResult.success();
    }

    @RequestMapping(value = "/events/new", method = POST)
    public String insertEvent(@RequestBody EventModification eventModification) {
    	//FIXME: check event short name should not contain "/" as we use it as part of the url
        eventManager.createEvent(eventModification);
        return OK;
    }

    @RequestMapping(value = "/location/geo", method = GET)
    public LocationDescriptor geocodeAddress(@RequestParam("location") String address) {
        Pair<String, String> coordinates = locationManager.geocode(address);
        TimeZone timezone = locationManager.getTimezone(coordinates);
        return LocationDescriptor.fromGeoData(coordinates, timezone, configurationManager.getStringConfigValue(MAPS_CLIENT_API_KEY));
    }

    @RequestMapping(value = "/location/map", method = GET)
    public String getMapUrl(@RequestParam("lat") String latitude, @RequestParam("long") String longitude) {
        Validate.notBlank(latitude);
        Validate.notBlank(longitude);
        LocationDescriptor descriptor = LocationDescriptor.fromGeoData(Pair.of(latitude, longitude), TimeZone.getDefault(), configurationManager.getStringConfigValue(MAPS_CLIENT_API_KEY));
        return descriptor.getMapUrl();
    }

}
