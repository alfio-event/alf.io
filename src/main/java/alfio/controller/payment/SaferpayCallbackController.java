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
package alfio.controller.payment;

import alfio.manager.TicketReservationManager;
import alfio.manager.payment.saferpay.PaymentPageInitializeRequestBuilder;
import alfio.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@RequiredArgsConstructor
public class SaferpayCallbackController {

    private final TicketReservationManager ticketReservationManager;
    private final EventRepository eventRepository;

    @GetMapping(PaymentPageInitializeRequestBuilder.CANCEL_URL_TEMPLATE)
    public String saferpayCancel(@PathVariable("eventName") String eventName,
                                 @PathVariable("reservationId") String reservationId) {
        var optionalEvent = eventRepository.findOptionalByShortName(eventName);
        if(optionalEvent.isEmpty()) {
            return "redirect:/";
        }
        var event = optionalEvent.get();
        var optionalReservation = ticketReservationManager.findByIdForEvent(reservationId, event.getId());
        if(optionalReservation.isEmpty()) {
            return "redirect:/event/"+eventName;
        }
        var optionalResult = ticketReservationManager.forceTransactionCheck(event, optionalReservation.get());
        if(optionalResult.isEmpty()) {
            // there's no transaction available.
            return "redirect:/event/"+eventName;
        }
        return "redirect:" + UriComponentsBuilder.fromPath(PaymentPageInitializeRequestBuilder.SUCCESS_URL_TEMPLATE)
            .buildAndExpand(eventName, reservationId).toUriString();
     }
}