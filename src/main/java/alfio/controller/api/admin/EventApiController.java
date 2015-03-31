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
import alfio.manager.i18n.I18nManager;
import alfio.manager.support.OrderSummary;
import alfio.model.Event;
import alfio.model.TicketReservation;
import alfio.model.modification.EventModification;
import alfio.model.modification.EventWithStatistics;
import alfio.model.modification.TicketAllocationModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.transaction.PaymentProxy;
import alfio.util.ValidationResult;
import alfio.util.Validator;
import com.opencsv.CSVReader;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.Validate;
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

import static org.springframework.web.bind.annotation.RequestMethod.*;

@RestController
@RequestMapping("/admin/api")
@Log4j2
public class EventApiController {

    private static final String OK = "OK";
    private final EventManager eventManager;
    private final I18nManager i18nManager;

    @Autowired
    public EventApiController(EventManager eventManager, I18nManager i18nManager) {
        this.eventManager = eventManager;
        this.i18nManager = i18nManager;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String unhandledException(Exception e) {
        if(!IllegalArgumentException.class.isInstance(e)) {
            log.warn("unhandled exception", e);
        }
        return e.getMessage();
    }


    @RequestMapping(value = "/paymentProxies", method = GET)
    @ResponseStatus(HttpStatus.OK)
    public List<PaymentProxy> getPaymentProxies() {
        return Arrays.asList(PaymentProxy.values());
    }

    @RequestMapping(value = "/events", method = GET)
    public List<EventWithStatistics> getAllEvents(Principal principal) {
        return eventManager.getAllEventsWithStatistics(principal.getName()).stream()
                .sorted().collect(Collectors.toList());
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
    public List<SerializablePair<TicketReservation, OrderSummary>> getPendingPayments(@PathVariable("eventName") String eventName, Principal principal) {
        return eventManager.getPendingPayments(eventName, principal.getName()).stream().map(SerializablePair::fromPair).collect(Collectors.toList());
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

    @RequestMapping(value = "/events/{eventName}/categories/{categoryId}/tickets/{ticketId}/toggle-locking", method = PUT)
    public boolean toggleTicketLocking(@PathVariable("eventName") String eventName,
                                       @PathVariable("categoryId") int categoryId,
                                       @PathVariable("ticketId") int ticketId,
                                       Principal principal) {
        return eventManager.toggleTicketLocking(eventName, categoryId, ticketId, principal.getName());
    }

    @RequestMapping(value = "/events/{eventName}/languages", method = GET)
    public List<Locale> getAvailableLocales(@PathVariable("eventName") String eventName) {
        return i18nManager.getEventLocales(eventName);
    }

    @RequestMapping(value = "/events/{eventName}/categories-containing-tickets", method = GET)
    public List<TicketCategoryModification> getCategoriesWithTickets(@PathVariable("eventName") String eventName, Principal principal) {
        Event event = eventManager.getSingleEvent(eventName, principal.getName());
        return eventManager.loadTicketCategoriesWithStats(event).stream()
                .filter(tc -> !tc.getTickets().isEmpty())
                .map(tc -> TicketCategoryModification.fromTicketCategory(tc.getTicketCategory(), event.getZoneId()))
                .collect(Collectors.toList());
    }

}
