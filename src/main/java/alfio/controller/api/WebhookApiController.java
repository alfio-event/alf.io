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

import alfio.manager.StripeManager;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/webhook")
@AllArgsConstructor
public class WebhookApiController {

    //private final MollieManager mollieManager;
    //private final TicketReservationManager ticketReservationManager;
    private final StripeManager stripeManager;

    @RequestMapping(value = "/mollie/event/{eventName}/reservation/{reservationId}", method = RequestMethod.POST)
    public void handleMollie(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId) throws Exception {
        // mollieManager.handleWebhook(eventName, reservationId, null);
        // call ticketReservationManager.confirm... if handlewebhoook return status paid
    }

    @RequestMapping(value = "/stripe/notification", method = RequestMethod.POST)
    public ResponseEntity<Boolean> handleStripeMessage(@RequestBody String body,
                                                      @RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature) {
        return Optional.ofNullable(body)
            .flatMap(b -> stripeManager.processWebhookEvent(body, stripeSignature))
            .filter(b -> b)
            .map(ResponseEntity::ok)
            .orElseGet(() -> new ResponseEntity<>(HttpStatus.BAD_REQUEST));
    }
}
