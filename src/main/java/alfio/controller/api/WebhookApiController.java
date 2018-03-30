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

import alfio.manager.StripeCreditCardManager;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.pdfbox.io.IOUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.Charset;
import java.util.Optional;

@RestController
@RequestMapping("/api/webhook")
@AllArgsConstructor
@Log4j2
public class WebhookApiController {

    //private final MollieManager mollieManager;
    //private final TicketReservationManager ticketReservationManager;
    private final StripeCreditCardManager stripeCreditCardManager;

    @RequestMapping(value = "/mollie/event/{eventName}/reservation/{reservationId}", method = RequestMethod.POST)
    public void handleMollie(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId) throws Exception {
        // mollieManager.handleWebhook(eventName, reservationId, null);
        // call ticketReservationManager.performPayment... if handlewebhoook return status paid
    }

    @RequestMapping(value = "/stripe/notification", method = RequestMethod.POST)
    public ResponseEntity<Boolean> handleStripeMessage(@RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature, HttpServletRequest request) {
        return readRequest(request)
            .flatMap(b -> stripeCreditCardManager.processWebhookEvent(b, stripeSignature))
            .filter(b -> b)
            .map(ResponseEntity::ok)
            .orElseGet(() -> new ResponseEntity<>(HttpStatus.BAD_REQUEST));
    }

    private static Optional<String> readRequest(HttpServletRequest request) {
        try (ServletInputStream is = request.getInputStream()){
            return Optional.ofNullable(IOUtils.toByteArray(is)).map(b -> new String(b, Charset.forName("UTF-8")));
        } catch (Exception e) {
            log.error("exception during request conversion", e);
            return Optional.empty();
        }
    }
}
