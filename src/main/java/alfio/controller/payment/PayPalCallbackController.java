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

import alfio.manager.PurchaseContextManager;
import alfio.manager.TicketReservationManager;
import alfio.manager.payment.PayPalManager;
import alfio.model.PurchaseContext;
import alfio.model.TicketReservation;
import alfio.model.transaction.token.PayPalToken;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Controller
@RequestMapping("/{purchaseContextType}/{purchaseContextIdentifier}/reservation/{reservationId}/payment/paypal")
@RequiredArgsConstructor
public class PayPalCallbackController {

    private final PurchaseContextManager purchaseContextManager;
    private final TicketReservationManager ticketReservationManager;
    private final PayPalManager payPalManager;

    @GetMapping("/confirm")
    public String payPalSuccess(@PathVariable("purchaseContextType") PurchaseContext.PurchaseContextType purchaseContextType,
                                @PathVariable("purchaseContextIdentifier") String purchaseContextId,
                                @PathVariable("reservationId") String reservationId,
                                @RequestParam(value = "token", required = false) String payPalPaymentId,
                                @RequestParam(value = "PayerID", required = false) String payPalPayerID,
                                @RequestParam(value = "hmac") String hmac) {

        var optionalPurchaseContext = purchaseContextManager.findByReservationId(reservationId)
            .filter(pc -> pc.getType() == purchaseContextType && pc.getPublicIdentifier().equals(purchaseContextId));
        if(optionalPurchaseContext.isEmpty()) {
            return "redirect:/";
        }

        Optional<TicketReservation> optionalReservation = ticketReservationManager.findById(reservationId);

        var purchaseContext = optionalPurchaseContext.get();

        if(optionalReservation.isEmpty()) {
            return "redirect:/"+purchaseContext.getType().getUrlComponent()+"/" + purchaseContext.getPublicIdentifier();
        }

        var res = optionalReservation.get();


        if (isNotBlank(payPalPayerID) && isNotBlank(payPalPaymentId)) {
            var token = new PayPalToken(payPalPayerID, payPalPaymentId, hmac);
            payPalManager.saveToken(res.getId(), purchaseContext, token);
            return "redirect:/" + purchaseContext.getType().getUrlComponent() + "/" + purchaseContext.getPublicIdentifier() + "/reservation/" +res.getId() + "/overview";
        } else {
            return payPalCancel(res.getId(), payPalPaymentId, hmac);
        }
    }

    @GetMapping("/cancel")
    public String payPalCancel(@PathVariable("reservationId") String reservationId,
                               @RequestParam(value = "token", required = false) String payPalPaymentId,
                               @RequestParam(value = "hmac") String hmac) {

        var optionalPurchaseContext = purchaseContextManager.findByReservationId(reservationId);
        if(optionalPurchaseContext.isEmpty()) {
            return "redirect:/";
        }
        var purchaseContext = optionalPurchaseContext.get();

        Optional<TicketReservation> optionalReservation = ticketReservationManager.findById(reservationId);

        if(optionalReservation.isEmpty()) {
            return "redirect:/" + purchaseContext.getType().getUrlComponent() + "/" + purchaseContext.getPublicIdentifier();
        }

        payPalManager.removeToken(optionalReservation.get(), payPalPaymentId);
        return "redirect:/" + purchaseContext.getType().getUrlComponent() + "/" + purchaseContext.getPublicIdentifier() + "/reservation/" + optionalReservation.get().getId() + "/overview";
    }
}
