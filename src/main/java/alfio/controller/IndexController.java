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

import alfio.controller.support.DataPreloaderManager;
import alfio.model.TicketReservationStatusAndValidation;
import alfio.repository.EventRepository;
import alfio.repository.SubscriptionRepository;
import alfio.repository.TicketReservationRepository;
import alfio.util.RequestUtils;
import ch.digitalfondue.jfiveparse.Document;
import ch.digitalfondue.jfiveparse.JFiveParse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static alfio.controller.Constants.*;

@Controller
public class IndexController {

    private static final String TEXT_HTML_CHARSET_UTF_8 = "text/html;charset=UTF-8";
    private static final String UTF_8 = "UTF-8";
    private final Document indexPage;
    private final Document openGraphPage;
    private final EventRepository eventRepository;
    private final TicketReservationRepository ticketReservationRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final DataPreloaderManager dataPreloaderManager;

    public IndexController(EventRepository eventRepository,
                           TicketReservationRepository ticketReservationRepository,
                           SubscriptionRepository subscriptionRepository,
                           DataPreloaderManager dataPreloaderManager) {

        this.eventRepository = eventRepository;

        this.ticketReservationRepository = ticketReservationRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.dataPreloaderManager = dataPreloaderManager;
        try (var idxIs = new ClassPathResource("alfio-public-frontend-index.html").getInputStream();
             var idxOpenIs = new ClassPathResource("alfio/web-templates/event-open-graph-page.html").getInputStream();
             var idxIsR = new InputStreamReader(idxIs, StandardCharsets.UTF_8);
             var idxOpenGraphReader = new InputStreamReader(idxOpenIs, StandardCharsets.UTF_8)) {
            indexPage = JFiveParse.parse(idxIsR);
            openGraphPage = JFiveParse.parse(idxOpenGraphReader);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }


    @RequestMapping(value = "/", method = RequestMethod.HEAD)
    public ResponseEntity<String> replyToProxy() {
        return ResponseEntity.ok("Up and running!");
    }

    @GetMapping("/healthz")
    public ResponseEntity<String> replyToK8s() {
        return ResponseEntity.ok("Up and running!");
    }


    //url defined in the angular app in app-routing.module.ts
    /**
     <pre>{@code
    { path: '', component: EventListComponent, canActivate: [LanguageGuard] },
    { path: 'event/:eventShortName', component: EventDisplayComponent, canActivate: [EventGuard, LanguageGuard] },
    { path: 'event/:eventShortName/poll', loadChildren: () => import('./poll/poll.module').then(m => m.PollModule), canActivate: [EventGuard, LanguageGuard] },
    { path: 'event/:eventShortName/reservation/:reservationId', children: [
    { path: 'book', component: BookingComponent, canActivate: reservationsGuard },
    { path: 'overview', component: OverviewComponent, canActivate: reservationsGuard },
    { path: 'waitingPayment', redirectTo: 'waiting-payment'},
    { path: 'waiting-payment', component: OfflinePaymentComponent, canActivate: reservationsGuard },
    { path: 'deferred-payment', component: DeferredOfflinePaymentComponent, canActivate: reservationsGuard },
    { path: 'processing-payment', component: ProcessingPaymentComponent, canActivate: reservationsGuard },
    { path: 'success', component: SuccessComponent, canActivate: reservationsGuard },
    { path: 'not-found', component: NotFoundComponent, canActivate: reservationsGuard },
    { path: 'error', component: ErrorComponent, canActivate: reservationsGuard },
    ]},
    { path: 'event/:eventShortName/ticket/:ticketId', children: [
    { path: 'view', component: ViewTicketComponent, canActivate: [EventGuard, LanguageGuard] },
    { path: 'update', component: UpdateTicketComponent, canActivate: [EventGuard, LanguageGuard] }
    ]}
    }
     </pre>
     Poll routing:
     <pre>{@code
    { path: '', component: PollComponent, children: [
    {path: '', component: PollSelectionComponent },
    {path: ':pollId', component: DisplayPollComponent }
    ]}
    }
     </pre>

     */
    @GetMapping({
        "/",
        "/o/*",
        "/o/*/events-all",
        "/events-all",
        "/event/{eventShortName}",
        "/event/{eventShortName}/reservation/{reservationId}/book",
        "/event/{eventShortName}/reservation/{reservationId}/overview",
        "/event/{eventShortName}/reservation/{reservationId}/waitingPayment",
        "/event/{eventShortName}/reservation/{reservationId}/waiting-payment",
        "/event/{eventShortName}/reservation/{reservationId}/waiting-custom-payment",
        "/event/{eventShortName}/reservation/{reservationId}/deferred-payment",
        "/event/{eventShortName}/reservation/{reservationId}/processing-payment",
        "/event/{eventShortName}/reservation/{reservationId}/success",
        "/event/{eventShortName}/reservation/{reservationId}/not-found",
        "/event/{eventShortName}/reservation/{reservationId}/error",
        "/event/{eventShortName}/ticket/{ticketId}/view",
        "/event/{eventShortName}/ticket/{ticketId}/update",
        "/event/{eventShortName}/ticket/{ticketId}/check-in/{ticketCodeHash}/waiting-room",
        //
        // subscription
        "/subscriptions-all",
        "/o/*/subscriptions-all",
        "/subscription/{subscriptionId}",
        "/subscription/{subscriptionId}/reservation/{reservationId}/book",
        "/subscription/{subscriptionId}/reservation/{reservationId}/overview",
        "/subscription/{subscriptionId}/reservation/{reservationId}/waitingPayment",
        "/subscription/{subscriptionId}/reservation/{reservationId}/waiting-payment",
        "/subscription/{subscriptionId}/reservation/{reservationId}/waiting-custom-payment",
        "/subscription/{subscriptionId}/reservation/{reservationId}/deferred-payment",
        "/subscription/{subscriptionId}/reservation/{reservationId}/processing-payment",
        "/subscription/{subscriptionId}/reservation/{reservationId}/success",
        "/subscription/{subscriptionId}/reservation/{reservationId}/not-found",
        "/subscription/{subscriptionId}/reservation/{reservationId}/error",
        // poll
        "/event/{eventShortName}/poll",
        "/event/{eventShortName}/poll/{pollId}",
        // user
        "/my-orders",
        "/my-profile",
    })
    public void replyToIndex(@PathVariable(value = EVENT_SHORT_NAME, required = false) String eventShortName,
                             @PathVariable(required = false) String subscriptionId,
                             @RequestHeader(value = "User-Agent", required = false) String userAgent,
                             @RequestParam(value = "lang", required = false) String lang,
                             ServletWebRequest request,
                             HttpServletResponse response,
                             HttpSession session,
                             Authentication authentication) throws IOException {

        response.setContentType(TEXT_HTML_CHARSET_UTF_8);
        response.setCharacterEncoding(UTF_8);

        try (var os = response.getOutputStream(); var osw = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
            var doc = dataPreloaderManager.generateIndexDocument(
                eventShortName,
                subscriptionId,
                userAgent,
                lang,
                request,
                response,
                session,
                authentication,
                openGraphPage,
                indexPage
            );
            JFiveParse.serialize(doc, osw);
        }
    }

    @GetMapping("/event/{eventShortName}/reservation/{reservationId}")
    public String redirectEventToReservation(@PathVariable(value = EVENT_SHORT_NAME) String eventShortName,
                                             @PathVariable String reservationId,
                                             @RequestParam(value = "subscription", required = false) String subscriptionId) {
        if (eventRepository.existsByShortName(eventShortName)) {
            var reservationStatusUrlSegment = ticketReservationRepository.findOptionalStatusAndValidationById(reservationId)
                .map(IndexController::reservationStatusToUrlMapping).orElse(NOT_FOUND);
            return REDIRECT + UriComponentsBuilder.fromPath("/event/{eventShortName}/reservation/{reservationId}/{status}")
                // if subscription param is present, we forward it to the reservation resource
                .queryParamIfPresent("subscription", Optional.ofNullable(StringUtils.trimToNull(subscriptionId)))
                .buildAndExpand(Map.of(EVENT_SHORT_NAME, eventShortName, "reservationId", reservationId, "status",reservationStatusUrlSegment))
                .toUriString();
        } else {
            return "redirect:/";
        }
    }

    @GetMapping("/subscription/{subscriptionId}/reservation/{reservationId}")
    public String redirectSubscriptionToReservation(@PathVariable String subscriptionId, @PathVariable String reservationId) {
        if (subscriptionRepository.existsById(UUID.fromString(subscriptionId))) {
            var reservationStatusUrlSegment = ticketReservationRepository.findOptionalStatusAndValidationById(reservationId)
                .map(IndexController::reservationStatusToUrlMapping).orElse(NOT_FOUND);

            return REDIRECT + UriComponentsBuilder.fromPath("/subscription/{subscriptionId}/reservation/{reservationId}/{status}")
                .buildAndExpand(Map.of("subscriptionId", subscriptionId, "reservationId", reservationId, "status",reservationStatusUrlSegment))
                .toUriString();
        } else {
            return "redirect:/";
        }
    }

    private static String reservationStatusToUrlMapping(TicketReservationStatusAndValidation status) {
        return switch (status.getStatus()) {
            case PENDING -> Boolean.TRUE.equals(status.getValidated()) ? "overview" : "book";
            case COMPLETE, FINALIZING -> "success";
            case OFFLINE_PAYMENT, OFFLINE_FINALIZING -> "waiting-payment";
            case DEFERRED_OFFLINE_PAYMENT -> "deferred-payment";
            case EXTERNAL_PROCESSING_PAYMENT, WAITING_EXTERNAL_CONFIRMATION -> "processing-payment";
            case IN_PAYMENT, STUCK -> "error";
            default -> NOT_FOUND; // <- this may be a little bit aggressive
        };
    }

    @GetMapping(value = {
        "/event/{eventShortName}/code/{code}",
        "/e/{eventShortName}/c/{code}"})
    public String redirectCode(@PathVariable(EVENT_SHORT_NAME) String eventName,
                               @PathVariable String code,
                               @RequestHeader(value = "User-Agent", required = false) String userAgent) {

        if (RequestUtils.isSocialMediaShareUA(userAgent)) {
            return REDIRECT + UriComponentsBuilder.fromPath("/event/{eventShortName}").build(Map.of(EVENT_SHORT_NAME, eventName));
        }

        return REDIRECT + UriComponentsBuilder.fromPath("/api/v2/public/event/{eventShortName}/code/{code}")
            .build(Map.of(EVENT_SHORT_NAME, eventName, "code", code));
    }

    @GetMapping("/e/{eventShortName}")
    public String redirectEvent(@PathVariable(EVENT_SHORT_NAME) String eventName) {
        return REDIRECT + UriComponentsBuilder.fromPath("/event/{eventShortName}").build(Map.of(EVENT_SHORT_NAME, eventName));
    }

}
