/**
 * This file is part of bagarino.
 *
 * bagarino is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bagarino is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.bagarino.controller;


import io.bagarino.controller.api.support.LocationDescriptor;
import io.bagarino.controller.decorator.EventDescriptor;
import io.bagarino.controller.decorator.SaleableTicketCategory;
import io.bagarino.manager.system.ConfigurationManager;
import io.bagarino.model.Event;
import io.bagarino.repository.EventRepository;
import io.bagarino.repository.TicketCategoryRepository;
import io.bagarino.repository.user.OrganizationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.bagarino.model.system.ConfigurationKeys.MAPS_CLIENT_API_KEY;
import static io.bagarino.model.system.ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION;
import static io.bagarino.util.OptionalWrapper.optionally;

@Controller
public class EventController {

    private final EventRepository eventRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final ConfigurationManager configurationManager;
    private final OrganizationRepository organizationRepository;

	@Autowired
	public EventController(ConfigurationManager configurationManager, EventRepository eventRepository,
			OrganizationRepository organizationRepository,
			TicketCategoryRepository ticketCategoryRepository) {
		this.configurationManager = configurationManager;
		this.eventRepository = eventRepository;
		this.organizationRepository = organizationRepository;
		this.ticketCategoryRepository = ticketCategoryRepository;
	}

	@RequestMapping(value = {"/"}, method = RequestMethod.GET)
	public String listEvents(Model model) {
		List<Event> events = eventRepository.findAll();
		if(events.size() == 1) {
			return "redirect:/event/" + events.get(0).getShortName() + "/";
		} else {
			model.addAttribute("events", events.stream().map(EventDescriptor::new).collect(Collectors.toList()));
			return "/event/event-list";
		}
	}

	@RequestMapping(value = "/event/{eventName}", method = RequestMethod.GET)
	public String showEvent(@PathVariable("eventName") String eventName, Model model) {

		// TODO: for each ticket categories we should check if there are available tickets (to show sold out text)
		
		Optional<Event> event = optionally(() -> eventRepository.findByShortName(eventName));
		
		if(!event.isPresent()) {
			return "redirect:/";
		}

		Event ev = event.get();
		final ZonedDateTime now = ZonedDateTime.now(ev.getZoneId());
		//hide access restricted ticket categories
		List<SaleableTicketCategory> t = ticketCategoryRepository.findAllTicketCategories(ev.getId()).stream()
                .filter((c) -> !c.isAccessRestricted())
                .map((m) -> new SaleableTicketCategory(m, now, ev.getZoneId()))
                .collect(Collectors.toList());
		//


		LocationDescriptor ld = LocationDescriptor.fromGeoData(ev.getLatLong(), TimeZone.getTimeZone(ev.getTimeZone()),
				configurationManager.getStringConfigValue(MAPS_CLIENT_API_KEY));
        final EventDescriptor eventDescriptor = new EventDescriptor(ev);
        model.addAttribute("event", eventDescriptor)//
			.addAttribute("organizer", organizationRepository.getById(ev.getOrganizationId()))
			.addAttribute("ticketCategories", t)//
			.addAttribute("amountOfTickets", IntStream.rangeClosed(0, configurationManager.getIntConfigValue(MAX_AMOUNT_OF_TICKETS_BY_RESERVATION, 5)).toArray())//
			.addAttribute("locationDescriptor", ld);
		model.asMap().putIfAbsent("hasErrors", false);//TODO: refactor
		return "/event/show-event";
	}
}
