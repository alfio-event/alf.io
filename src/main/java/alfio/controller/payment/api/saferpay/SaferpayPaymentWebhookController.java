package alfio.controller.payment.api.saferpay;

import alfio.manager.TicketReservationManager;
import alfio.manager.payment.saferpay.PaymentPageInitializeRequestBuilder;
import alfio.model.transaction.PaymentContext;
import alfio.model.transaction.PaymentProxy;
import alfio.repository.EventRepository;
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
    private final EventRepository eventRepository;

    @GetMapping(PaymentPageInitializeRequestBuilder.WEBHOOK_URL_TEMPLATE)
    ResponseEntity<String> handleTransactionNotification(@PathVariable("eventShortName") String eventName,
                                                         @PathVariable("reservationId") String reservationId) {
        return eventRepository.findOptionalByShortName(eventName)
                .map(event -> {
                    var result = ticketReservationManager.processTransactionWebhook("", null, PaymentProxy.SAFERPAY,
                        Map.of("eventName", eventName, "reservationId", reservationId), new PaymentContext(event, reservationId));
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
