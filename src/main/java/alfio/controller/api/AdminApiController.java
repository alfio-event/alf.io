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
package alfio.controller.api;

import alfio.manager.EventManager;
import alfio.manager.location.LocationManager;
import alfio.manager.support.OrderSummary;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.TicketReservation;
import alfio.model.modification.*;
import alfio.model.modification.support.LocationDescriptor;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Organization;
import alfio.model.user.User;
import alfio.util.ValidationResult;
import alfio.util.Validator;
import com.opencsv.CSVReader;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

import static alfio.model.system.ConfigurationKeys.MAPS_CLIENT_API_KEY;
import static org.springframework.web.bind.annotation.RequestMethod.*;

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

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String unhandledException(Exception e) {
        return e.getMessage();
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
    public List<EventWithStatistics> getAllEvents(Principal principal) {
        return eventManager.getAllEventsWithStatistics(principal.getName());
    }

    @RequestMapping(value = "/events/{name}", method = GET)
    public Map<String, Object> getSingleEvent(@PathVariable("name") String eventName, Principal principal) {
        Map<String, Object> out = new HashMap<>();
        final String username = principal.getName();
        final EventWithStatistics event = eventManager.getSingleEventWithStatistics(eventName, username);
        out.put("event", event);
        out.put("organization", eventManager.loadOrganizer(event.getEvent(), username));
        return out;
    }

    @RequestMapping(value = "/events/check", method = POST)
    public ValidationResult validateEvent(@RequestBody EventModification eventModification) {
        return ValidationResult.success();
    }

    @RequestMapping(value = "/events/new", method = POST)
    public String insertEvent(@RequestBody EventModification eventModification) {
        eventManager.createEvent(eventModification);
        return OK;
    }

    @RequestMapping(value = "/events/{id}/header/update", method = POST)
    public ValidationResult updateHeader(@PathVariable("id") int id, @RequestBody EventModification eventModification, Errors errors,  Principal principal) {
        return Validator.validateEventHeader(eventModification, errors).ifSuccess(() -> eventManager.updateEventHeader(id, eventModification, principal.getName()));
    }

    @RequestMapping(value = "/events/{id}/prices/update", method = POST)
    public ValidationResult updatePrices(@PathVariable("id") int id, @RequestBody EventModification eventModification, Errors errors,  Principal principal) {
        return Validator.validateEventPrices(eventModification, errors).ifSuccess(() -> eventManager.updateEventPrices(id, eventModification, principal.getName()));
    }

    @RequestMapping(value = "/events/{eventId}/categories/{categoryId}/update", method = POST)
    public ValidationResult updateExistingCategory(@PathVariable("eventId") int eventId, @PathVariable("categoryId") int categoryId, @RequestBody TicketCategoryModification category, Errors errors, Principal principal) {
        return Validator.validateCategory(category, errors).ifSuccess(() -> eventManager.updateCategory(categoryId, eventId, category, principal.getName()));
    }

    @RequestMapping(value = "/events/{eventId}/categories/new", method = POST)
    public ValidationResult createCategory(@PathVariable("eventId") int eventId, @RequestBody TicketCategoryModification category, Errors errors, Principal principal) {
        return Validator.validateCategory(category, errors).ifSuccess(() -> eventManager.insertCategory(eventId, category, principal.getName()));
    }

    @RequestMapping(value = "/events/reallocate", method = PUT)
    public String reallocateTickets(@RequestBody TicketAllocationModification form) {
        eventManager.reallocateTickets(form.getSrcCategoryId(), form.getTargetCategoryId(), form.getEventId());
        return OK;
    }

    @RequestMapping(value = "/events/{eventName}/pending-payments")
    public List<Pair<TicketReservation, OrderSummary>> getPendingPayments(@PathVariable("eventName") String eventName, Principal principal) {
        return eventManager.getPendingPayments(eventName, principal.getName());
    }

    @RequestMapping(value = "/events/{eventName}/pending-payments/{reservationId}/confirm", method = POST)
    public String confirmPayment(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId, Principal principal) {
        eventManager.confirmPayment(eventName, reservationId, principal.getName());
        return OK;
    }

    @RequestMapping(value = "/events/{eventName}/pending-payments/{reservationId}", method = DELETE)
    public String deletePendingPayment(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId, Principal principal) {
        eventManager.deletePendingOfflinePayment(eventName, reservationId, principal.getName());
        return OK;
    }

    @RequestMapping(value = "/events/{eventName}/pending-payments/bulk-confirmation", method = POST)
    public List<Triple<Boolean, String, String>> bulkConfirmation(@PathVariable("eventName") String eventName,
                                                                  Principal principal,
                                                                  @RequestParam("file") MultipartFile file) throws IOException {

        try(InputStreamReader isr = new InputStreamReader(file.getInputStream())) {
            CSVReader reader = new CSVReader(isr);
            String username = principal.getName();
            return reader.readAll().stream()
                    .map(line -> {
                        String reservationID = null;
                        try {
                            Validate.isTrue(line.length >= 2);
                            reservationID = line[0];
                            eventManager.confirmPayment(eventName, reservationID, new BigDecimal(line[1]), username);
                            return Triple.of(Boolean.TRUE, reservationID, "");
                        } catch (Exception e) {
                            return Triple.of(Boolean.FALSE, Optional.ofNullable(reservationID).orElse(""), e.getMessage());
                        }
                    })
                    .collect(Collectors.toList());
        }
    }

    @RequestMapping(value = "/location/geo", method = GET)
    public LocationDescriptor geocodeAddress(@RequestParam("location") String address) {
        Pair<String, String> coordinates = locationManager.geocode(address);
        TimeZone timezone = locationManager.getTimezone(coordinates);
        return LocationDescriptor.fromGeoData(coordinates, timezone, getMapsClientApiKey());
    }

    private Optional<String> getMapsClientApiKey() {
        return configurationManager.getStringConfigValue(MAPS_CLIENT_API_KEY);
    }

    @RequestMapping(value = "/location/map", method = GET)
    public String getMapUrl(@RequestParam("lat") String latitude, @RequestParam("long") String longitude) {
        Validate.notBlank(latitude);
        Validate.notBlank(longitude);
        LocationDescriptor descriptor = LocationDescriptor.fromGeoData(Pair.of(latitude, longitude), TimeZone.getDefault(), getMapsClientApiKey());
        return descriptor.getMapUrl();
    }

    @RequestMapping(value = "/configuration/load", method = GET)
    public Map<ConfigurationKeys.Type, List<Configuration>> loadConfiguration() {
        return configurationManager.loadAllIncludingMissing();
    }

    @RequestMapping(value = "/configuration/update", method = POST)
    public Map<ConfigurationKeys.Type, List<Configuration>> updateConfiguration(@RequestBody ConfigurationModification configuration) {
        configurationManager.save(ConfigurationKeys.fromValue(configuration.getKey()), configuration.getValue());
        return loadConfiguration();
    }

    @RequestMapping(value = "/configuration/update-bulk", method = POST)
    public Map<ConfigurationKeys.Type, List<Configuration>> updateConfiguration(@RequestBody Map<ConfigurationKeys.Type, List<ConfigurationModification>> input) {
        Objects.requireNonNull(input);
        List<ConfigurationModification> list = input.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        configurationManager.saveAll(list);
        return loadConfiguration();
    }
    
    @RequestMapping(value = "/configuration/key/{key}", method = DELETE)
    public boolean deleteKey(@PathVariable("key") String key) {
        configurationManager.deleteKey(key);
        return true;
    }

    @RequestMapping(value = "/categories/{categoryId}/tickets/{ticketId}/toggle-locking", method = PUT)
    public boolean toggleTicketLocking(@PathVariable("categoryId") int categoryId, @PathVariable("ticketId") int ticketId) {
        return eventManager.toggleTicketLocking(categoryId, ticketId);
    }

}
