package alfio.controller.payment;

import alfio.manager.TicketReservationManager;
import alfio.manager.payment.saferpay.PaymentPageInitializeRequestBuilder;
import alfio.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@RequiredArgsConstructor
public class SaferpayCallbackController {

    private final TicketReservationManager ticketReservationManager;
    private final EventRepository eventRepository;

    @GetMapping(PaymentPageInitializeRequestBuilder.CANCEL_URL_TEMPLATE)
    public String saferpayCancel(@PathVariable("eventName") String eventName,
                                 @PathVariable("reservationId") String reservationId) {
        var optionalEvent = eventRepository.findOptionalByShortName(eventName);
        if(optionalEvent.isEmpty()) {
            return "redirect:/";
        }
        var event = optionalEvent.get();
        var optionalReservation = ticketReservationManager.findByIdForEvent(reservationId, event.getId());
        if(optionalReservation.isEmpty()) {
            return "redirect:/event/"+eventName;
        }
        var optionalResult = ticketReservationManager.forceTransactionCheck(event, optionalReservation.get());
        if(optionalResult.isEmpty()) {
            // there's no transaction available.
            return "redirect:/event/"+eventName;
        }
        return "redirect:" + UriComponentsBuilder.fromPath(PaymentPageInitializeRequestBuilder.SUCCESS_URL_TEMPLATE)
            .buildAndExpand(eventName, reservationId).toUriString();
     }
}
