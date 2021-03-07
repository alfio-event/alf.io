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
package alfio.controller.payment.api.mollie;

import alfio.manager.PurchaseContextManager;
import alfio.manager.TicketReservationManager;
import alfio.model.transaction.PaymentContext;
import alfio.model.transaction.PaymentProxy;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;

import static alfio.manager.payment.MollieWebhookPaymentManager.*;

@RestController
@Log4j2
@AllArgsConstructor
public class MolliePaymentWebhookController {
    private final TicketReservationManager ticketReservationManager;
    private final PurchaseContextManager purchaseContextManager;

    @SuppressWarnings("MVCPathVariableInspection")
    @PostMapping(WEBHOOK_URL_TEMPLATE)
    public ResponseEntity<String> receivePaymentConfirmation(HttpServletRequest request,
                                                             @PathVariable("reservationId") String reservationId) {
        return Optional.ofNullable(StringUtils.trimToNull(request.getParameter("id")))
            .flatMap(id -> purchaseContextManager.findByReservationId(reservationId)
                    .map(purchaseContext -> {
                        var content = "id="+id;
                        var result = ticketReservationManager.processTransactionWebhook(content, null, PaymentProxy.MOLLIE,
                            Map.of(ADDITIONAL_INFO_PURCHASE_CONTEXT_TYPE, purchaseContext.getType().getUrlComponent(),
                                ADDITIONAL_INFO_PURCHASE_IDENTIFIER, purchaseContext.getPublicIdentifier(),
                                ADDITIONAL_INFO_RESERVATION_ID, reservationId), new PaymentContext(purchaseContext, reservationId));
                        if(result.isSuccessful()) {
                            return ResponseEntity.ok("OK");
                        } else if(result.isError()) {
                            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result.getReason());
                        }
                        return ResponseEntity.ok(result.getReason());
                    }))
            .orElseGet(() -> ResponseEntity.badRequest().body("NOK"));
    }
}
