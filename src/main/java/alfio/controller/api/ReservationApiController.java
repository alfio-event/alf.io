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
import alfio.controller.form.UpdateTicketOwnerForm;
import alfio.model.ContentLanguage;
import alfio.manager.i18n.I18nManager;
import alfio.model.Event;
import alfio.model.Ticket;
import alfio.util.TemplateManager;
import alfio.util.ValidationResult;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.RequestContextUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
public class ReservationApiController {

    private final TicketHelper ticketHelper;
    private final TemplateManager templateManager;
    private final I18nManager i18nManager;

    @Autowired
    public ReservationApiController(TicketHelper ticketHelper, TemplateManager templateManager, I18nManager i18nManager) {
        this.ticketHelper = ticketHelper;
        this.templateManager = templateManager;
        this.i18nManager = i18nManager;
    }


    @RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/ticket/{ticketIdentifier}/assign", method = RequestMethod.POST, headers = "X-Requested-With=XMLHttpRequest")
    public Map<String, Object> assignTicketToPerson(@PathVariable("eventName") String eventName,
                                                    @PathVariable("reservationId") String reservationId,
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

        Optional<Triple<ValidationResult, Event, Ticket>> assignmentResult = ticketHelper.assignTicket(eventName, reservationId, ticketIdentifier, updateTicketOwner, Optional.of(bindingResult), request, t -> {
            Locale requestLocale = RequestContextUtils.getLocale(request);
            model.addAttribute("ticketFieldConfiguration", ticketHelper.findTicketFieldConfigurationAndValue(t.getMiddle().getId(), t.getRight().getId(), requestLocale));
            model.addAttribute("value", t.getRight());
            model.addAttribute("validationResult", t.getLeft());
            model.addAttribute("countries", ticketHelper.getLocalizedCountries(requestLocale));
            model.addAttribute("event", t.getMiddle());
            model.addAttribute("availableLanguages", i18nManager.getEventLanguages(eventName).stream()
                    .map(ContentLanguage.toLanguage(requestLocale)).collect(Collectors.toList()));
            String uuid = t.getRight().getUuid();
            model.addAttribute("urlSuffix", singleTicket ? "ticket/"+uuid+"/view": uuid);
        }, userDetails);
        Map<String, Object> result = new HashMap<>();
        model.addAttribute("reservationId", reservationId);

        Optional<ValidationResult> validationResult = assignmentResult.map(Triple::getLeft);
        if(validationResult.isPresent() && validationResult.get().isSuccess()) {
            result.put("partial", templateManager.renderServletContextResource("/WEB-INF/templates/event/assign-ticket-result.ms", model.asMap(), request, TemplateManager.TemplateOutput.HTML));
        }
        result.put("validationResult", validationResult.orElse(ValidationResult.failed(new ValidationResult.ValidationError("fullName", "error.fullname"))));
        return result;
    }
}
