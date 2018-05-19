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
package alfio.controller.api;

import alfio.controller.api.support.TicketHelper;
import alfio.controller.form.PaymentForm;
import alfio.controller.form.UpdateTicketOwnerForm;
import alfio.manager.EuVatChecker;
import alfio.manager.TicketReservationManager;
import alfio.manager.i18n.I18nManager;
import alfio.model.*;
import alfio.model.result.ValidationResult;
import alfio.model.transaction.PaymentProxy;
import alfio.repository.EventRepository;
import alfio.repository.TicketReservationRepository;
import alfio.util.Json;
import alfio.util.TemplateManager;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.RequestContextUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

import static alfio.model.PriceContainer.VatStatus.*;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

@RestController
@AllArgsConstructor
public class ReservationApiController {

    private final EventRepository eventRepository;
    private final TicketHelper ticketHelper;
    private final TemplateManager templateManager;
    private final I18nManager i18nManager;
    private final EuVatChecker vatChecker;
    private final TicketReservationRepository ticketReservationRepository;
    private final TicketReservationManager ticketReservationManager;


    @RequestMapping(value = "/event/{eventName}/ticket/{ticketIdentifier}/assign", method = RequestMethod.POST, headers = "X-Requested-With=XMLHttpRequest")
    public Map<String, Object> assignTicketToPerson(@PathVariable("eventName") String eventName,
                                                    @PathVariable("ticketIdentifier") String ticketIdentifier,
                                                    @RequestParam(value = "single-ticket", required = false, defaultValue = "false") boolean singleTicket,
                                                    UpdateTicketOwnerForm updateTicketOwner,
                                                    BindingResult bindingResult,
                                                    HttpServletRequest request,
                                                    Model model,
                                                    Authentication authentication) throws Exception {

        Optional<UserDetails> userDetails = Optional.ofNullable(authentication)
                .map(Authentication::getPrincipal)
                .filter(UserDetails.class::isInstance)
                .map(UserDetails.class::cast);

        Optional<Triple<ValidationResult, Event, Ticket>> assignmentResult = ticketHelper.assignTicket(eventName, ticketIdentifier, updateTicketOwner, Optional.of(bindingResult), request, t -> {
            Locale requestLocale = RequestContextUtils.getLocale(request);
            model.addAttribute("ticketFieldConfiguration", ticketHelper.findTicketFieldConfigurationAndValue(t.getMiddle().getId(), t.getRight(), requestLocale));
            model.addAttribute("value", t.getRight());
            model.addAttribute("validationResult", t.getLeft());
            model.addAttribute("countries", TicketHelper.getLocalizedCountries(requestLocale));
            model.addAttribute("event", t.getMiddle());
            model.addAttribute("useFirstAndLastName", t.getMiddle().mustUseFirstAndLastName());
            model.addAttribute("availableLanguages", i18nManager.getEventLanguages(eventName).stream()
                    .map(ContentLanguage.toLanguage(requestLocale)).collect(Collectors.toList()));
            String uuid = t.getRight().getUuid();
            model.addAttribute("urlSuffix", singleTicket ? "ticket/"+uuid+"/view": uuid);
            model.addAttribute("elementNamePrefix", "");
        }, userDetails);
        Map<String, Object> result = new HashMap<>();

        Optional<ValidationResult> validationResult = assignmentResult.map(Triple::getLeft);
        if(validationResult.isPresent() && validationResult.get().isSuccess()) {
            result.put("partial", templateManager.renderServletContextResource("/WEB-INF/templates/event/assign-ticket-result.ms",
                assignmentResult.get().getMiddle(),//<- ugly, but will be removed
                model.asMap(), request, TemplateManager.TemplateOutput.HTML));
        }
        result.put("validationResult", validationResult.orElse(ValidationResult.failed(new ValidationResult.ErrorDescriptor("fullName", "error.fullname"))));
        return result;
    }

    @RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/vat-validation", method = RequestMethod.POST)
    @Transactional
    public ResponseEntity<VatDetail> validateEUVat(@PathVariable("eventName") String eventName,
                                                   @PathVariable("reservationId") String reservationId,
                                                   PaymentForm paymentForm,
                                                   Locale locale,
                                                   HttpServletRequest request) {

        String country = paymentForm.getVatCountryCode();
        Optional<Triple<Event, TicketReservation, VatDetail>> vatDetail = eventRepository.findOptionalByShortName(eventName)
            .flatMap(e -> ticketReservationRepository.findOptionalReservationById(reservationId).map(r -> Pair.of(e, r)))
            .filter(e -> EnumSet.of(INCLUDED, NOT_INCLUDED).contains(e.getKey().getVatStatus()))
            .filter(e -> vatChecker.isVatCheckingEnabledFor(e.getKey().getOrganizationId()))
            .flatMap(e -> vatChecker.checkVat(paymentForm.getVatNr(), country, e.getKey().getOrganizationId()).map(vd -> Triple.of(e.getLeft(), e.getRight(), vd)));

        vatDetail
            .filter(t -> t.getRight().isValid())
            .ifPresent(t -> {
                VatDetail vd = t.getRight();
                String billingAddress = vd.getName() + "\n" + vd.getAddress();
                PriceContainer.VatStatus vatStatus = determineVatStatus(t.getLeft().getVatStatus(), t.getRight().isVatExempt());
                ticketReservationRepository.updateBillingData(vatStatus, vd.getVatNr(), country, paymentForm.isInvoiceRequested(), reservationId);
                OrderSummary orderSummary = ticketReservationManager.orderSummaryForReservationId(reservationId, t.getLeft(), Locale.forLanguageTag(t.getMiddle().getUserLanguage()));
                ticketReservationRepository.addReservationInvoiceOrReceiptModel(reservationId, Json.toJson(orderSummary));
                ticketReservationRepository.updateTicketReservation(reservationId, t.getMiddle().getStatus().name(), paymentForm.getEmail(),
                    paymentForm.getFullName(), paymentForm.getFirstName(), paymentForm.getLastName(), locale.getLanguage(), billingAddress, null,
                    Optional.ofNullable(paymentForm.getPaymentMethod()).map(PaymentProxy::name).orElse(null));
                paymentForm.getTickets().forEach((ticketId, owner) -> {
                    if(isNotEmpty(owner.getEmail()) && ((isNotEmpty(owner.getFirstName()) && isNotEmpty(owner.getLastName())) || isNotEmpty(owner.getFullName()))) {
                        ticketHelper.preAssignTicket(eventName, reservationId, ticketId, owner, Optional.empty(), request, (tr) -> {}, Optional.empty());
                    }
                });
            });

        return vatDetail
            .map(Triple::getRight)
            .map(vd -> {
                if(vd.isValid()) {
                    return ResponseEntity.ok(vd);
                } else {
                    return new ResponseEntity<VatDetail>(HttpStatus.BAD_REQUEST);
                }
            })
            .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    private static PriceContainer.VatStatus determineVatStatus(PriceContainer.VatStatus current, boolean isVatExempt) {
        if(!isVatExempt) {
            return current;
        }
        return current == NOT_INCLUDED ? NOT_INCLUDED_EXEMPT : INCLUDED_EXEMPT;
    }
}
