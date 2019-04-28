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
package alfio.controller.api.v2.user;

import alfio.controller.EventController;
import alfio.controller.api.v2.user.model.*;
import alfio.controller.decorator.SaleableTicketCategory;
import alfio.controller.form.ReservationForm;
import alfio.manager.EventManager;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.EventDescription;
import alfio.model.modification.support.LocationDescriptor;
import alfio.model.result.ValidationResult;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.model.user.Organization;
import alfio.repository.EventDescriptionRepository;
import alfio.repository.EventRepository;
import alfio.repository.TicketCategoryDescriptionRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.MustacheCustomTagInterceptor;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/v2/public/")
@AllArgsConstructor
public class EventApiV2Controller {

    private final EventController eventController;
    private final EventManager eventManager;
    private final EventRepository eventRepository;
    private final ConfigurationManager configurationManager;
    private final OrganizationRepository organizationRepository;
    private final EventDescriptionRepository eventDescriptionRepository;
    private final TicketCategoryDescriptionRepository ticketCategoryDescriptionRepository;


    @GetMapping("events")
    public ResponseEntity<List<BasicEventInfo>> listEvents() {
        var events = eventManager.getPublishedEvents()
            .stream()
            .map(e -> new BasicEventInfo(e.getShortName(), e.getFileBlobId(), e.getDisplayName(), e.getLocation()))
            .collect(Collectors.toList());
        return new ResponseEntity<>(events, getCorsHeaders(), HttpStatus.OK);
    }

    @GetMapping("event/{eventName}")
    public ResponseEntity<EventWithAdditionalInfo> getEvent(@PathVariable("eventName") String eventName) {
        return eventRepository.findOptionalByShortName(eventName).filter(e -> e.getStatus() != Event.Status.DISABLED)//
            .map(event -> {

                var descriptions = applyCommonMark(eventDescriptionRepository.findByEventIdAndType(event.getId(), EventDescription.EventDescriptionType.DESCRIPTION)
                    .stream()
                    .collect(Collectors.toMap(EventDescription::getLocale, EventDescription::getDescription)));

                Organization organization = organizationRepository.getById(event.getOrganizationId());

                Map<ConfigurationKeys, Optional<String>> geoInfoConfiguration = configurationManager.getStringConfigValueFrom(
                    Configuration.from(event, ConfigurationKeys.MAPS_PROVIDER),
                    Configuration.from(event, ConfigurationKeys.MAPS_CLIENT_API_KEY),
                    Configuration.from(event, ConfigurationKeys.MAPS_HERE_APP_ID),
                    Configuration.from(event, ConfigurationKeys.MAPS_HERE_APP_CODE));
                LocationDescriptor ld = LocationDescriptor.fromGeoData(event.getLatLong(), TimeZone.getTimeZone(event.getTimeZone()), geoInfoConfiguration);
                return new ResponseEntity<>(new EventWithAdditionalInfo(event, ld, organization, descriptions), getCorsHeaders(), HttpStatus.OK);
            })
            .orElseGet(() -> ResponseEntity.notFound().headers(getCorsHeaders()).build());
    }

    private static Map<String, String> applyCommonMark(Map<String, String> in) {
        if (in == null) {
            return Collections.emptyMap();
        }

        var res = new HashMap<String, String>();
        in.forEach((k, v) -> {
            res.put(k, MustacheCustomTagInterceptor.renderToCommonmark(v));
        });
        return res;
    }

    @GetMapping("event/{eventName}/ticket-categories")
    public ResponseEntity<List<TicketCategory>> getTicketCategories(@PathVariable("eventName") String eventName, Model model, HttpServletRequest request) {
        if ("/event/show-event".equals(eventController.showEvent(eventName, model, request, Locale.ENGLISH))) {
            var valid = (List<SaleableTicketCategory>) model.asMap().get("ticketCategories");
            var ticketCategoryIds = valid.stream().map(SaleableTicketCategory::getId).collect(Collectors.toList());
            var ticketCategoryDescriptions = ticketCategoryDescriptionRepository.descriptionsByTicketCategory(ticketCategoryIds);
            var converted = valid.stream().map(stc -> new TicketCategory(stc, applyCommonMark(ticketCategoryDescriptions.get(stc.getId())))).collect(Collectors.toList());
            return new ResponseEntity<>(converted, getCorsHeaders(), HttpStatus.OK);
        } else {
            return ResponseEntity.notFound().headers(getCorsHeaders()).build();
        }
    }

    @GetMapping("event/{eventName}/calendar/{locale}")
    public void getCalendar(@PathVariable("eventName") String eventName,
                            @PathVariable("locale") String locale,
                            @RequestParam(value = "type", required = false) String calendarType,
                            @RequestParam(value = "ticketId", required = false) String ticketId,
                            HttpServletResponse response) throws IOException {
        eventController.calendar(eventName, locale, calendarType, ticketId, response);
    }

    @PostMapping("tmp/event/{eventName}/promoCode/{promoCode}")
    @ResponseBody
    public ValidationResult savePromoCode(@PathVariable("eventName") String eventName,
                                          @PathVariable("promoCode") String promoCode,
                                          Model model,
                                          HttpServletRequest request) {
        return eventController.savePromoCode(eventName, promoCode, model, request);
    }

    @PostMapping(value = "event/{eventName}/reserve-tickets")
    public ResponseEntity<ValidatedResponse<String>> reserveTicket(@PathVariable("eventName") String eventName,
                                                                   @RequestBody ReservationForm reservation,
                                                                   BindingResult bindingResult,
                                                                   ServletWebRequest request,
                                                                   RedirectAttributes redirectAttributes,
                                                                   Locale locale) {

        String redirectResult = eventController.reserveTicket(eventName, reservation, bindingResult, request, redirectAttributes, locale);

        if (bindingResult.hasErrors()) {
            return new ResponseEntity<>(ValidatedResponse.toResponse(bindingResult, null), getCorsHeaders(), HttpStatus.OK);
        } else {
            String reservationIdentifier = redirectResult
                .substring(redirectResult.lastIndexOf("reservation/")+"reservation/".length())
                .replace("/book", "");
            return ResponseEntity.ok(new ValidatedResponse<>(ValidationResult.success(), reservationIdentifier));
        }

    }


    private static HttpHeaders getCorsHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Access-Control-Allow-Origin", "*");
        return headers;
    }
}
