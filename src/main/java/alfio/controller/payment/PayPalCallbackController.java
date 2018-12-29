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

import alfio.manager.PaymentManager;
import alfio.manager.TicketReservationManager;
import alfio.model.Event;
import alfio.model.TicketReservation;
import alfio.model.transaction.token.PayPalToken;
import alfio.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Controller
@RequestMapping("/event/{eventName}/reservation/{reservationId}/payment/paypal")
@RequiredArgsConstructor
public class PayPalCallbackController {

    private final EventRepository eventRepository;
    private final TicketReservationManager ticketReservationManager;

    @GetMapping("/confirm")
    public String payPalSuccess(@PathVariable("eventName") String eventName,
                                @PathVariable("reservationId") String reservationId,
                                @RequestParam(value = "paymentId", required = false) String payPalPaymentId,
                                @RequestParam(value = "PayerID", required = false) String payPalPayerID,
                                @RequestParam(value = "hmac") String hmac,
                                RedirectAttributes redirectAttributes,
                                HttpSession session) {

        Optional<Event> optionalEvent = eventRepository.findOptionalByShortName(eventName);
        if(optionalEvent.isEmpty()) {
            return "redirect:/";
        }

        Optional<TicketReservation> optionalReservation = ticketReservationManager.findById(reservationId);

        if(optionalReservation.isEmpty()) {
            return "redirect:/event/"+eventName;
        }

        if (isNotBlank(payPalPayerID) && isNotBlank(payPalPaymentId)) {
            redirectAttributes.addFlashAttribute("paypalCheckoutConfirmation", true)
                 .addFlashAttribute("tokenAcquired", true);
            session.setAttribute(PaymentManager.PAYMENT_TOKEN, new PayPalToken(payPalPayerID, payPalPaymentId, hmac));
        } else {
            return payPalCancel(eventName, reservationId, redirectAttributes, session);
        }

        return "redirect:/event/"+eventName+"/reservation/"+reservationId+"/overview";
    }

    @GetMapping("/cancel")
    public String payPalCancel(@PathVariable("eventName") String eventName,
                               @PathVariable("reservationId") String reservationId,
                               RedirectAttributes redirectAttributes,
                               HttpSession session) {

        session.removeAttribute(PaymentManager.PAYMENT_TOKEN);

        redirectAttributes.addFlashAttribute("tokenAcquired", false)
             .addFlashAttribute("payPalCancelled", true);
        return "redirect:/event/"+eventName+"/reservation/"+reservationId+"/overview";
    }
}
