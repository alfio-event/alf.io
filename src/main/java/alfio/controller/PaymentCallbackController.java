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
package alfio.controller;

import alfio.controller.support.SessionUtil;
import alfio.manager.PaymentManager;
import alfio.manager.TicketReservationManager;
import alfio.manager.payment.PaymentSpecification;
import alfio.manager.support.PaymentResult;
import alfio.model.Event;
import alfio.model.OrderSummary;
import alfio.model.TicketReservation;
import alfio.model.TotalPrice;
import alfio.model.transaction.PaymentContext;
import alfio.model.transaction.PaymentMethod;
import alfio.model.transaction.PaymentProxy;
import alfio.model.transaction.capabilities.ExternalProcessing;
import alfio.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Optional;

@Controller
@RequestMapping("/event/{eventName}/reservation/{reservationId}/payment")
@RequiredArgsConstructor
@Log4j2
public class PaymentCallbackController {

    private static final String REDIRECT_TO_ROOT = "redirect:/";
    private final TicketReservationManager ticketReservationManager;
    private final EventRepository eventRepository;
    private final PaymentManager paymentManager;

    @RequestMapping("/{paymentMethod}/confirm")
    public String confirm(@PathVariable("eventName") String eventName,
                          @PathVariable("reservationId") String reservationId,
                          @PathVariable("paymentMethod") String method,
                          @RequestParam MultiValueMap<String, String> requestParams,
                          HttpServletRequest request,
                          Model model,
                          BindingResult bindingResult,
                          RedirectAttributes redirectAttributes) {

        PaymentMethod paymentMethod = PaymentMethod.safeParse(method);
        if(paymentMethod == null) {
            log.warn("unrecognized payment method received: {}. Redirecting to list", method);
            return REDIRECT_TO_ROOT;
        }
        return getEventAndReservation(eventName, reservationId)
            .flatMap(pair -> {
                Event event = pair.getLeft();
                TicketReservation reservation = pair.getRight();
                int organizationId = event.getOrganizationId();
                if(paymentManager.getActivePaymentMethods(event).stream().anyMatch(dto -> dto.getPaymentProxy().getPaymentMethod() == paymentMethod)) {
                    log.warn("Payment method {} is not active for organization {}", method, organizationId);
                    return Optional.empty();
                }
                return paymentManager.lookupProviderByMethod(paymentMethod, new PaymentContext(event))
                    .filter(ExternalProcessing.class::isInstance)
                    .map(provider -> {
                        TotalPrice reservationCost = ticketReservationManager.totalReservationCostWithVAT(reservationId);
                        OrderSummary orderSummary = ticketReservationManager.orderSummaryForReservationId(reservationId, event);
                        PaymentSpecification paymentSpecification = ((ExternalProcessing)provider).getSpecificationFromRequest(event, reservation, reservationCost, orderSummary).apply(requestParams);

                        PaymentResult paymentResult = ticketReservationManager.performPayment(paymentSpecification,
                            reservationCost,
                            SessionUtil.retrieveSpecialPriceSessionId(request),
                            Optional.ofNullable(PaymentProxy.fromPaymentMethod(paymentMethod)));

                        if(paymentResult.isRedirect()) {
                            return "redirect:"+paymentResult.getRedirectUrl();
                        }

                        if(paymentResult.isFailed()) {
                            bindingResult.reject(paymentResult.getErrorCode().orElse(null));
                            SessionUtil.addToFlash(bindingResult, redirectAttributes);
                        }

                        return "redirect:" +ticketReservationManager.reservationUrl(reservationId, event);
                    });
            })
            .orElse(REDIRECT_TO_ROOT);


    }


    @RequestMapping("/{paymentMethod}/cancel")
    public String cancel(@PathVariable("eventName") String eventName,
                         @PathVariable("reservationId") String reservationId,
                         @PathVariable("paymentMethod") String paymentMethod,
                         Model model,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {

        return getEventAndReservation(eventName, reservationId)
            .map(pair -> {
                bindingResult.reject("error.STEP_2_PAYPAL_unexpected");
                SessionUtil.addToFlash(bindingResult, redirectAttributes);
                return "redirect:" +ticketReservationManager.reservationUrl(reservationId, pair.getLeft());
            })
            .orElse(REDIRECT_TO_ROOT);
    }

    private Optional<Pair<Event, TicketReservation>> getEventAndReservation(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId) {
        return eventRepository.findOptionalByShortName(eventName)
            .map(event -> Pair.of(event, ticketReservationManager.findById(reservationId)))
            .filter(pair -> pair.getRight().isPresent())
            .map(pair -> Pair.of(pair.getLeft(), pair.getRight().orElseThrow(IllegalStateException::new)));
    }

}
