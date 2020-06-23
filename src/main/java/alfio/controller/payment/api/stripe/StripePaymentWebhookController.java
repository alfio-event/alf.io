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
package alfio.controller.payment.api.stripe;

import alfio.manager.TicketReservationManager;
import alfio.model.transaction.PaymentMethod;
import alfio.model.transaction.PaymentProxy;
import alfio.util.RequestUtils;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.logging.Logger;

@RestController
@Log4j2
@AllArgsConstructor
public class StripePaymentWebhookController {

    private final TicketReservationManager ticketReservationManager;

    @PostMapping("/api/payment/webhook/stripe/payment")
    public ResponseEntity<String> receivePaymentConfirmation(@RequestHeader(value = "Stripe-Signature") String stripeSignature,
                                                            HttpServletRequest request) {
        return RequestUtils.readRequest(request)
            .map(content -> {
                var result = ticketReservationManager.processTransactionWebhook(content, stripeSignature, PaymentProxy.STRIPE, Map.of());
                if(result.isSuccessful()) {
                    return ResponseEntity.ok("OK");
                } else if(result.isError()) {
                    log.error("StripeWebhook error: " + result.getReason());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result.getReason());
                }
                return ResponseEntity.ok(result.getReason());
            })
            .orElseGet(() -> ResponseEntity.badRequest().body("NOK"));

    }
}
