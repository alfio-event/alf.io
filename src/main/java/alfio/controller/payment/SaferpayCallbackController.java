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

import alfio.manager.PurchasableManager;
import alfio.manager.TicketReservationManager;
import alfio.manager.payment.saferpay.PaymentPageInitializeRequestBuilder;
import alfio.model.Purchasable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@RequiredArgsConstructor
public class SaferpayCallbackController {

    private final TicketReservationManager ticketReservationManager;
    private final PurchasableManager purchasableManager;

    @GetMapping(PaymentPageInitializeRequestBuilder.CANCEL_URL_TEMPLATE)
    public String saferpayCancel(@PathVariable("purchasableType") Purchasable.PurchasableType purchasableType,
                                 @PathVariable("purchasableIdentifier") String purchasableIdentifier,
                                 @PathVariable("reservationId") String reservationId) {
        var maybePurchasable = purchasableManager.findBy(purchasableType, purchasableIdentifier);
        if(maybePurchasable.isEmpty()) {
            return "redirect:/";
        }
        var purchasable = maybePurchasable.get();
        var optionalReservation = ticketReservationManager.findById(reservationId);
        if(optionalReservation.isEmpty()) {
            return "redirect:/"+purchasable.getType().getUrlComponent()+"/"+purchasable.getPublicIdentifier();
        }
        var optionalResult = ticketReservationManager.forceTransactionCheck(purchasable, optionalReservation.get());
        if(optionalResult.isEmpty()) {
            // there's no transaction available.
            return "redirect:/"+purchasable.getType().getUrlComponent()+"/"+purchasable.getPublicIdentifier();
        }
        return "redirect:" + UriComponentsBuilder.fromPath(PaymentPageInitializeRequestBuilder.SUCCESS_URL_TEMPLATE)
            .buildAndExpand(purchasable.getType().getUrlComponent(), purchasable.getPublicIdentifier(), reservationId).toUriString();
     }
}
