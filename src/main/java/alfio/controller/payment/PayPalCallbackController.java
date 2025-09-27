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
import alfio.util.TemplateManager;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Controller
public class PayPalCallbackController {

    private static final String PAYPAL_CALLBACK_BASE_PATH = "/{purchaseContextType}/{purchaseContextIdentifier}/reservation/{reservationId}/payment/paypal";
    private final PurchaseContextManager purchaseContextManager;
    private final TicketReservationManager ticketReservationManager;
    private final PayPalManager payPalManager;
    private final TemplateManager templateManager;

    public PayPalCallbackController(PurchaseContextManager purchaseContextManager,
                                    TicketReservationManager ticketReservationManager,
                                    PayPalManager payPalManager,
                                    TemplateManager templateManager) {
        this.purchaseContextManager = purchaseContextManager;
        this.ticketReservationManager = ticketReservationManager;
        this.payPalManager = payPalManager;
        this.templateManager = templateManager;
    }

    @GetMapping("/payment/paypal/redirect/{operation}")
    public void payPalRedirect(@PathVariable String operation,
                               @RequestParam PurchaseContext.PurchaseContextType purchaseContextType,
                               @RequestParam("purchaseContextIdentifier") String purchaseContextId,
                               @RequestParam String reservationId,
                               @RequestParam(value = "token", required = false) String payPalPaymentId,
                               @RequestParam(value = "PayerID", required = false) String payPalPayerID,
                               @RequestParam(value = "hmac") String hmac,
                               HttpServletResponse response) throws IOException {

        var optionalPurchaseContext = retrievePurchaseContext(purchaseContextType, purchaseContextId, reservationId);

        if(optionalPurchaseContext.isEmpty()) {
            response.sendRedirect("/");
            return;
        }

        var uriBuilder = UriComponentsBuilder.fromUriString(PAYPAL_CALLBACK_BASE_PATH + "/" + operation)
            .queryParam("hmac", hmac);

        if (payPalPaymentId != null) {
            uriBuilder.queryParam("token", payPalPaymentId);
        }

        if (payPalPayerID != null) {
            uriBuilder.queryParam("PayerID", payPalPayerID);
        }

        response.setContentType(MediaType.TEXT_HTML_VALUE);
        templateManager.renderText(
            new ClassPathResource("/alfio/templates/openid-redirect.ms"),
            Map.of("redirectPath", uriBuilder.build(Map.of(
                "purchaseContextType", purchaseContextType,
                "purchaseContextIdentifier", purchaseContextId,
                "reservationId", reservationId
            ))),
            response.getWriter()
        );
        response.flushBuffer();
    }

    @GetMapping(PAYPAL_CALLBACK_BASE_PATH + "/confirm")
    public String payPalSuccess(@PathVariable PurchaseContext.PurchaseContextType purchaseContextType,
                                @PathVariable("purchaseContextIdentifier") String purchaseContextId,
                                @PathVariable String reservationId,
                                @RequestParam(value = "token", required = false) String payPalPaymentId,
                                @RequestParam(value = "PayerID", required = false) String payPalPayerID,
                                @RequestParam(value = "hmac") String hmac) {

        var optionalPurchaseContext = retrievePurchaseContext(purchaseContextType, purchaseContextId, reservationId);
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
            return payPalCancel(purchaseContextType, purchaseContextId, res.getId(), payPalPaymentId);
        }
    }

    @GetMapping(PAYPAL_CALLBACK_BASE_PATH + "/cancel")
    public String payPalCancel(@PathVariable PurchaseContext.PurchaseContextType purchaseContextType,
                               @PathVariable("purchaseContextIdentifier") String purchaseContextId,
                               @PathVariable String reservationId,
                               @RequestParam(value = "token", required = false) String payPalPaymentId) {

        var optionalPurchaseContext = retrievePurchaseContext(purchaseContextType, purchaseContextId, reservationId);
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

    private Optional<PurchaseContext> retrievePurchaseContext(PurchaseContext.PurchaseContextType purchaseContextType, String purchaseContextId, String reservationId) {
        return purchaseContextManager.findByReservationId(reservationId)
            .filter(pc -> pc.getType() == purchaseContextType && pc.getPublicIdentifier().equals(purchaseContextId));
    }
}
