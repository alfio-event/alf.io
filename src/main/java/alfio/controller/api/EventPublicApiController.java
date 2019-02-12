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

import alfio.controller.api.support.DescriptionsLoader;
import alfio.controller.api.support.EventListItem;
import alfio.controller.api.support.PublicCategory;
import alfio.controller.api.support.PublicEvent;
import alfio.manager.EventManager;
import alfio.manager.TicketReservationManager;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.TicketCategory;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.model.user.Organization;
import alfio.repository.EventRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.user.OrganizationRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@Log4j2
public class EventPublicApiController {

    private final EventManager eventManager;
    private final OrganizationRepository organizationRepository;
    private final EventRepository eventRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final TicketReservationManager ticketReservationManager;
    private final ConfigurationManager configurationManager;
    private final DescriptionsLoader descriptionsLoader;

    @Autowired
    public EventPublicApiController(EventManager eventManager,
                                    OrganizationRepository organizationRepository,
                                    EventRepository eventRepository,
                                    TicketCategoryRepository ticketCategoryRepository,
                                    TicketReservationManager ticketReservationManager,
                                    ConfigurationManager configurationManager,
                                    DescriptionsLoader descriptionsLoader) {
        this.eventManager = eventManager;
        this.organizationRepository = organizationRepository;
        this.eventRepository = eventRepository;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.ticketReservationManager = ticketReservationManager;
        this.configurationManager = configurationManager;
        this.descriptionsLoader = descriptionsLoader;
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleGenericException(RuntimeException e) {
        log.error("unexpected exception", e);
        return new ResponseEntity<>("unexpected error", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @RequestMapping("/events")
    public ResponseEntity<List<EventListItem>> getEvents(HttpServletRequest request) {
        List<EventListItem> events = eventManager.getPublishedEvents().stream()
            .map(e -> new EventListItem(e, request.getContextPath(), descriptionsLoader.eventDescriptions().load(e)))
            .collect(Collectors.toList());
        return new ResponseEntity<>(events, getCorsHeaders(), HttpStatus.OK);
    }

    @RequestMapping("/events/{shortName}")
    public ResponseEntity<PublicEvent> getEvent(@PathVariable("shortName") String shortName, HttpServletRequest request) {
        return eventRepository.findOptionalByShortName(shortName)
            .map(e -> {
                List<PublicCategory> categories = ticketCategoryRepository.findAllTicketCategories(e.getId()).stream()
                    .filter((c) -> !c.isAccessRestricted())
                    .map(c -> buildPublicCategory(c, e))
                    .collect(Collectors.toList());
                Organization organization = organizationRepository.getById(e.getOrganizationId());
                return new ResponseEntity<>(new PublicEvent(e, request.getContextPath(), descriptionsLoader.eventDescriptions().load(e), categories, organization), getCorsHeaders(), HttpStatus.OK);
            })
            .orElseGet(() -> new ResponseEntity<>(getCorsHeaders(), HttpStatus.NOT_FOUND));
    }

    private static HttpHeaders getCorsHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Access-Control-Allow-Origin", "*");
        return headers;
    }

    private PublicCategory buildPublicCategory(TicketCategory c, Event e) {
        return new PublicCategory(c, e,
            ticketReservationManager.countAvailableTickets(e, c),
            configurationManager.getIntConfigValue(Configuration.from(e.getOrganizationId(), e.getId(), c.getId(), ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION), 5),
            descriptionsLoader.ticketCategoryDescriptions());
    }

}
