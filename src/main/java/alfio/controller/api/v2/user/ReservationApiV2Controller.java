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
package alfio.controller.api.v2.user;

import alfio.controller.ReservationController;
import alfio.controller.api.v2.user.model.BookingInfo;
import alfio.controller.api.v2.user.model.BookingInfo.TicketsByTicketCategory;
import alfio.controller.api.v2.user.model.ValidatedResponse;
import alfio.controller.form.ContactAndTicketsForm;
import alfio.controller.form.PaymentForm;
import alfio.controller.support.SessionUtil;
import alfio.controller.support.TicketDecorator;
import alfio.manager.TicketReservationManager;
import alfio.model.Event;
import alfio.model.TicketCategory;
import alfio.model.TicketReservation;
import alfio.repository.EventRepository;
import alfio.repository.TicketReservationRepository;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v2/public/")
public class ReservationApiV2Controller {

    private final EventRepository eventRepository;
    private final ReservationController reservationController;
    private final TicketReservationManager ticketReservationManager;
    private final TicketReservationRepository ticketReservationRepository;


    @GetMapping("/tmp/event/{eventName}/reservation/{reservationId}/status")
    public ResponseEntity<String> getStatus(@PathVariable("eventName") String eventName,
                                            @PathVariable("reservationId") String reservationId) {
        Optional<Event> event = eventRepository.findOptionalByShortName(eventName);
        if (event.isEmpty()) {
            return ResponseEntity.ok("redirect:/");
        }
        return ResponseEntity.ok(reservationController.redirectReservation(ticketReservationManager.findById(reservationId), eventName, reservationId));
    }



    @GetMapping("/event/{eventName}/reservation/{reservationId}/booking-info")
    public ResponseEntity<BookingInfo> getBookingInfo(@PathVariable("eventName") String eventName,
                                                      @PathVariable("reservationId") String reservationId,
                                                      Model model,
                                                      Locale locale) {
        reservationController.showBookingPage(eventName, reservationId, model, locale);
        //
        var ticketsByCategory = ((List<Pair<TicketCategory, List<TicketDecorator>>>) model.asMap().get("ticketsByCategory"))
            .stream()
            .map(a -> new TicketsByTicketCategory(a.getKey().getName(), toBookingInfoTicket(a.getValue())))
            .collect(Collectors.toList());

        var reservation = (TicketReservation) model.asMap().get("reservation");

        return ResponseEntity.ok(new BookingInfo(reservation.getFirstName(), reservation.getLastName(), reservation.getEmail(), ticketsByCategory));
    }

    @DeleteMapping("/event/{eventName}/reservation/{reservationId}")
    public ResponseEntity<Boolean> cancelPendingReservation(@PathVariable("eventName") String eventName,
                                                            @PathVariable("reservationId") String reservationId,
                                                            HttpServletRequest request) {

        //FIXME check precondition (see ReservationController.redirectIfNotValid)
        ticketReservationManager.cancelPendingReservation(reservationId, false, null);
        SessionUtil.cleanupSession(request);
        return ResponseEntity.ok(true);
    }

    @PostMapping("/event/{eventName}/reservation/{reservationId}/back-to-booking")
    public ResponseEntity<Boolean> backToBook(@PathVariable("eventName") String eventName,
                                              @PathVariable("reservationId") String reservationId) {

        //FIXME check precondition (see ReservationController.redirectIfNotValid)

        ticketReservationRepository.updateValidationStatus(reservationId, false);
        return ResponseEntity.ok(true);
    }

    @PostMapping("/event/{eventName}/reservation/{reservationId}")
    public ResponseEntity<ValidatedResponse<Boolean>> handleReservation(@PathVariable("eventName") String eventName,
                                                               @PathVariable("reservationId") String reservationId,
                                                               @RequestBody  PaymentForm paymentForm,
                                                               BindingResult bindingResult,
                                                               Model model,
                                                               HttpServletRequest request,
                                                               Locale locale,
                                                               RedirectAttributes redirectAttributes,
                                                               HttpSession session) {
        //FIXME check precondition (see ReservationController.redirectIfNotValid)
        reservationController.handleReservation(eventName, reservationId, paymentForm,
            bindingResult, model, request, locale, redirectAttributes,
            session);
        return ResponseEntity.ok(ValidatedResponse.toResponse(bindingResult, !bindingResult.hasErrors()));
    }

    @PostMapping("/event/{eventName}/reservation/{reservationId}/validate-to-overview")
    public ResponseEntity<ValidatedResponse<Boolean>> validateToOverview(@PathVariable("eventName") String eventName,
                                                             @PathVariable("reservationId") String reservationId,
                                                             @RequestBody ContactAndTicketsForm contactAndTicketsForm,
                                                             BindingResult bindingResult,
                                                             HttpServletRequest request,
                                                             RedirectAttributes redirectAttributes,
                                                             Locale locale) {

        //FIXME check precondition (see ReservationController.redirectIfNotValid)
        reservationController.validateToOverview(eventName, reservationId, contactAndTicketsForm, bindingResult, request, redirectAttributes, locale);

        return ResponseEntity.ok(ValidatedResponse.toResponse(bindingResult, !bindingResult.hasErrors()));
    }

    @GetMapping("/tmp/event/{eventName}/reservation/{reservationId}/overview")
    public ResponseEntity<Map<String, ?>> showOverview(@PathVariable("eventName") String eventName,
                               @PathVariable("reservationId") String reservationId,
                               Locale locale,
                               Model model,
                               HttpSession session) {
        var res = reservationController.showOverview(eventName, reservationId, locale, model, session);
        model.addAttribute("viewState", res);
        return ResponseEntity.ok(model.asMap());
    }

    @GetMapping("/tmp/event/{eventName}/reservation/{reservationId}/success")
    public ResponseEntity<Map<String, ?>> showConfirmationPage(@PathVariable("eventName") String eventName,
                                                      @PathVariable("reservationId") String reservationId,
                                                      Locale locale,
                                                      Model model,
                                                      HttpServletRequest request) {

        var res = reservationController.showConfirmationPage(eventName, reservationId, false, false,
            model, locale, request);
        model.addAttribute("viewState", res);
        var ticketsByCategory = (List<Pair<TicketCategory, List<TicketDecorator>>>) model.asMap().get("ticketsByCategory");
        if (ticketsByCategory != null) {
            model.addAttribute("ticketsByCategory", ticketsByCategory.stream()
                .map(a -> new TicketsByTicketCategory(a.getKey().getName(), toBookingInfoTicket(a.getValue())))
                .collect(Collectors.toList()));
        }

        return ResponseEntity.ok(model.asMap());
    }


    @PostMapping("/event/{eventName}/reservation/{reservationId}/re-send-email")
    public ResponseEntity<Boolean> reSendReservationConfirmationEmail(@PathVariable("eventName") String eventName,
                                                     @PathVariable("reservationId") String reservationId, HttpServletRequest request) {
        var res = reservationController.reSendReservationConfirmationEmail(eventName, reservationId, request);
        if(res.endsWith("confirmation-email-sent=true")) {
            return ResponseEntity.ok(true);
        } else {
            return ResponseEntity.ok(false);
        }
    }

    private static List<BookingInfo.BookingInfoTicket> toBookingInfoTicket(List<TicketDecorator> ticketDecorators) {
        return ticketDecorators.stream()
            .map(td -> new BookingInfo.BookingInfoTicket(td.getUuid(),
                td.getFirstName(), td.getLastName(),
                td.getEmail(),
                td.getFullName(),
                td.getAssigned(),
                td.getTicketFieldConfiguration()))
            .collect(Collectors.toList());
    }

}
