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
package alfio.controller.api.support;

import alfio.manager.payment.StripeCreditCardManager;
import alfio.util.RequestUtils;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/webhook")
@AllArgsConstructor
@Log4j2
public class WebhookApiController {

    //private final MollieManager mollieManager;
    //private final TicketReservationManager ticketReservationManager;
    private final StripeCreditCardManager stripeCreditCardManager;

    @PostMapping("/mollie/event/{eventName}/reservation/{reservationId}")
    public void handleMollie(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId) {
        // mollieManager.handleWebhook(eventName, reservationId, null);
        // call ticketReservationManager.performPayment... if handlewebhoook return status paid
    }

    @PostMapping("/stripe/notification")
    public ResponseEntity<Boolean> handleStripeMessage(@RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature, HttpServletRequest request) {
        return RequestUtils.readRequest(request)
            .flatMap(b -> stripeCreditCardManager.processWebhookEvent(b, stripeSignature))
            .filter(b -> b)
            .map(ResponseEntity::ok)
            .orElseGet(() -> new ResponseEntity<>(HttpStatus.BAD_REQUEST));
    }

}
