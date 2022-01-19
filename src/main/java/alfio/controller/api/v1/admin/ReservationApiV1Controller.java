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
package alfio.controller.api.v1.admin;

import alfio.manager.EventManager;
import alfio.manager.PromoCodeRequestManager;
import alfio.manager.TicketReservationManager;
import alfio.model.api.v1.admin.ReservationCreationRequest;
import alfio.model.result.ErrorCode;
import alfio.util.ReservationUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNullElseGet;

@RestController
@RequestMapping("/api/v1/admin/event")
public class ReservationApiV1Controller {

    private final TicketReservationManager ticketReservationManager;
    private final EventManager eventManager;
    private final PromoCodeRequestManager promoCodeRequestManager;

    @Autowired
    public ReservationApiV1Controller(TicketReservationManager ticketReservationManager,
                                      EventManager eventManager,
                                      PromoCodeRequestManager promoCodeRequestManager) {
        this.ticketReservationManager = ticketReservationManager;
        this.eventManager = eventManager;
        this.promoCodeRequestManager = promoCodeRequestManager;
    }

    @PostMapping("/{slug}/reservation")
    public ResponseEntity<CreationResponse> createReservation(@PathVariable("slug") String eventSlug,
                                                  @RequestBody ReservationCreationRequest reservationCreationRequest,
                                                  Principal principal) {
        var bindingResult = new BeanPropertyBindingResult(reservationCreationRequest, "reservation");

        var optionalEvent = eventManager.getOptionalByName(eventSlug, principal.getName());
        if (optionalEvent.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var event = optionalEvent.get();
        Optional<String> promoCodeDiscount = ReservationUtil.checkPromoCode(reservationCreationRequest, event, promoCodeRequestManager, bindingResult);
        var locale = Locale.forLanguageTag(requireNonNullElseGet(reservationCreationRequest.getLanguage(), () -> event.getContentLanguages().get(0).getLanguage()));
        var selected = ReservationUtil.validateCreateRequest(reservationCreationRequest, bindingResult, ticketReservationManager, eventManager, "", event);
        if(selected.isPresent() && !bindingResult.hasErrors()) {
            var pair = selected.get();
            return ticketReservationManager.createTicketReservation(event, pair.getLeft(), pair.getRight(), promoCodeDiscount, locale, bindingResult, principal)
                .map(id -> {
                    var user = reservationCreationRequest.getUser();
                    if(user != null) {
                        ticketReservationManager.setReservationOwner(id, user.getUsername(), user.getEmail(), user.getFirstName(), user.getLastName(), locale.getLanguage());
                    }
                    return ResponseEntity.ok(CreationResponse.success(id, ticketReservationManager.reservationUrlForExternalClients(id, event, locale.getLanguage(), user != null)));
                })
                .orElseGet(() -> ResponseEntity.badRequest().build());
        } else {
            return ResponseEntity.badRequest()
                .body(CreationResponse.error(bindingResult.getAllErrors().stream().map(err -> ErrorCode.custom("invalid."+err.getObjectName(), err.getCode())).collect(Collectors.toList())));
        }
    }

    public static class CreationResponse {
        private final String id;
        private final String href;
        private final List<ErrorCode> errors;

        private CreationResponse(String id, String href, List<ErrorCode> errors) {
            this.id = id;
            this.href = href;
            this.errors = errors;
        }

        public String getId() {
            return id;
        }

        public String getHref() {
            return href;
        }

        public List<ErrorCode> getErrors() {
            return errors;
        }

        public boolean isSuccess() {
            return CollectionUtils.isEmpty(errors) && StringUtils.isNotEmpty(id);
        }

        static CreationResponse success(String id, String href) {
            return new CreationResponse(id, href, null);
        }

        static CreationResponse error(List<ErrorCode> errors) {
            return new CreationResponse(null, null, errors);
        }
    }
}
