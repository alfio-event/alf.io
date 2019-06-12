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
package alfio.controller.api.user;

import alfio.controller.api.support.DescriptionsLoader;
import alfio.controller.api.support.EventListItem;
import alfio.controller.api.support.PublicCategory;
import alfio.controller.api.support.PublicEvent;
import alfio.controller.form.ReservationForm;
import alfio.manager.EventManager;
import alfio.manager.TicketReservationManager;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.SpecialPrice;
import alfio.model.TicketCategory;
import alfio.model.result.Result;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.model.user.Organization;
import alfio.repository.AdditionalServiceRepository;
import alfio.repository.EventRepository;
import alfio.repository.SpecialPriceRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.ErrorsCode;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/public/")
public class RestEventApiController {

    private final EventManager eventManager;
    private final OrganizationRepository organizationRepository;
    private final EventRepository eventRepository;
    private final DescriptionsLoader descriptionsLoader;
    private final TicketReservationManager ticketReservationManager;
    private final ConfigurationManager configurationManager;
    private final SpecialPriceRepository specialPriceRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final AdditionalServiceRepository additionalServiceRepository;


    @Autowired
    public RestEventApiController(EventManager eventManager,
                                  OrganizationRepository organizationRepository,
                                  EventRepository eventRepository,
                                  DescriptionsLoader descriptionsLoader,
                                  TicketReservationManager ticketReservationManager,
                                  ConfigurationManager configurationManager,
                                  SpecialPriceRepository specialPriceRepository,
                                  TicketCategoryRepository ticketCategoryRepository,
                                  AdditionalServiceRepository additionalServiceRepository) {
        this.eventManager = eventManager;
        this.organizationRepository = organizationRepository;
        this.eventRepository = eventRepository;
        this.descriptionsLoader = descriptionsLoader;
        this.ticketReservationManager = ticketReservationManager;
        this.configurationManager = configurationManager;
        this.specialPriceRepository = specialPriceRepository;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.additionalServiceRepository = additionalServiceRepository;
    }


    @RequestMapping("events")
    public List<EventListItem> listEvents(HttpServletRequest request) {
        return eventManager.getPublishedEvents().stream()
            .map(e -> new EventListItem(e, request.getContextPath(), descriptionsLoader.eventDescriptions().load(e)))
            .collect(Collectors.toList());
    }

    @RequestMapping("events/{shortName}")
    public ResponseEntity<PublicEvent> showEvent(@PathVariable("shortName") String shortName, @RequestParam(value="specialCode", required = false) String specialCodeParam, HttpServletRequest request) {

        Optional<SpecialPrice> specialCode = Optional.ofNullable(StringUtils.trimToNull(specialCodeParam)).flatMap(specialPriceRepository::getByCode);

        return eventRepository.findOptionalByShortName(shortName).map((e) -> {
            List<PublicCategory> categories = ticketCategoryRepository.findAllTicketCategories(e.getId()).stream()
                .filter((c) -> !c.isAccessRestricted() || (specialCode.filter(sc -> sc.getTicketCategoryId() == c.getId()).isPresent()))
                .map(c -> buildPublicCategory(c, e))
                .collect(Collectors.toList());
            Organization organization = organizationRepository.getById(e.getOrganizationId());

            return new ResponseEntity<>(new PublicEvent(e, request.getContextPath(), descriptionsLoader.eventDescriptions().load(e), categories, organization), HttpStatus.OK);
        }).orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /*

    fetch('/api/public/events/eventname/reserve-tickets', {method: 'POST', body: JSON.stringify({reservation: [{ticketCategoryId: 0, amount:1}]}), credentials: 'same-origin', headers: new Headers({'X-Requested-With':'XMLHttpRequest', 'Content-Type': 'application/json', 'X-CSRF-TOKEN': 'cfda015e-87c8-4780-b41d-3db805f45a78'})})
        .then(r =>r.json())
        .then(json => console.log(json))


     */
    @RequestMapping(value = "events/{shortName}/reserve-tickets", method = RequestMethod.POST)
    public ResponseEntity<Result<String>> reserveTickets(@PathVariable("shortName") String shortName, @RequestBody ReservationForm reservation, BindingResult bindingResult, Locale locale) {
        return eventRepository.findOptionalByShortName(shortName).map(event -> {
            Optional<String> reservationUrl = reservation.validate(bindingResult, ticketReservationManager, additionalServiceRepository, eventManager, event).flatMap(selected -> {
                Date expiration = DateUtils.addMinutes(new Date(), ticketReservationManager.getReservationTimeout(event));
                try {
                    String reservationId = ticketReservationManager.createTicketReservation(event,
                        selected.getLeft(), selected.getRight(), expiration,
                        Optional.ofNullable(reservation.getPromoCode()), //FIXME check
                        Optional.ofNullable(reservation.getPromoCode()), //FIXME check
                        locale, false);
                    return Optional.of("/event/" + shortName + "/reservation/" + reservationId + "/book");
                } catch (TicketReservationManager.NotEnoughTicketsException nete) {
                    bindingResult.reject(ErrorsCode.STEP_1_NOT_ENOUGH_TICKETS);
                } catch (TicketReservationManager.MissingSpecialPriceTokenException missing) {
                    bindingResult.reject(ErrorsCode.STEP_1_ACCESS_RESTRICTED);
                } catch (TicketReservationManager.InvalidSpecialPriceTokenException invalid) {
                    bindingResult.reject(ErrorsCode.STEP_1_CODE_NOT_FOUND);
                }
                return Optional.empty();
            });

            Result<String> result = reservationUrl.map(Result::success).orElseGet(() -> Result.validationError(bindingResult.getAllErrors()));
            return new ResponseEntity<>(result, HttpStatus.OK);
        }).orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    private PublicCategory buildPublicCategory(TicketCategory c, Event e) {
        return new PublicCategory(c, e,
            ticketReservationManager.countAvailableTickets(e, c),
            configurationManager.getIntConfigValue(Configuration.from(e.getOrganizationId(), e.getId(), c.getId(), ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION), 5),
            descriptionsLoader.ticketCategoryDescriptions());
    }
}
