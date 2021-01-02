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
package alfio.controller.payment.api.saferpay;

import alfio.manager.PurchasableManager;
import alfio.manager.TicketReservationManager;
import alfio.manager.payment.saferpay.PaymentPageInitializeRequestBuilder;
import alfio.model.Purchasable;
import alfio.model.transaction.PaymentContext;
import alfio.model.transaction.PaymentProxy;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@AllArgsConstructor
public class SaferpayPaymentWebhookController {
    private final TicketReservationManager ticketReservationManager;
    private final PurchasableManager purchasableManager;

    @GetMapping(PaymentPageInitializeRequestBuilder.WEBHOOK_URL_TEMPLATE)
    ResponseEntity<String> handleTransactionNotification(@PathVariable("purchasableType") String purchasableType,
                                                         @PathVariable("purchasableIdentifier") String purchasableIdentifier,
                                                         @PathVariable("reservationId") String reservationId) {
        return purchasableManager.findBy(Purchasable.PurchasableType.from(purchasableType), purchasableIdentifier)
                .map(purchasable -> {
                    var result = ticketReservationManager.processTransactionWebhook("", null, PaymentProxy.SAFERPAY,
                        Map.of("purchasableType", purchasableType, "purchasableIdentifier", purchasableIdentifier, "reservationId", reservationId), new PaymentContext(purchasable, reservationId));
                    if(result.isSuccessful()) {
                        return ResponseEntity.ok("OK");
                    } else if(result.isError()) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result.getReason());
                    }
                    return ResponseEntity.ok(result.getReason());
                })
            .orElseGet(() -> ResponseEntity.badRequest().body("NOK"));
    }
}
