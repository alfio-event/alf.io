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


import io.bagarino.model.Event;
import io.bagarino.model.TicketCategory;
import io.bagarino.repository.EventRepository;
import io.bagarino.repository.TicketCategoryRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.bagarino.util.OptionalWrapper.optionally;

@Controller
public class EventController {

    private final EventRepository eventRepository;
    private final TicketCategoryRepository ticketCategoryRepository;

	@Autowired
	public EventController(EventRepository eventRepository,
			TicketCategoryRepository ticketCategoryRepository) {
		this.eventRepository = eventRepository;
		this.ticketCategoryRepository = ticketCategoryRepository;
	}

	@RequestMapping(value = {"/"}, method = RequestMethod.GET)
	public String listEvents(Model model) {
		List<Event> events = eventRepository.findAll();
		if(events.size() == 1) {
			return "redirect:/event/" + events.get(0).getShortName() + "/";
		} else {
			model.addAttribute("events", events);
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

		final Date now = new Date();
		//hide access restricted ticket categories
		List<SaleableTicketCategory> t = ticketCategoryRepository.findAllTicketCategories(event.get().getId()).stream().filter((c) -> !c.isAccessRestricted()).map((m) -> new SaleableTicketCategory(m, now)).collect(Collectors.toList());
		//
		model.addAttribute("event", event.get())//
			.addAttribute("ticketCategories", t);
		model.asMap().putIfAbsent("hasErrors", false);//TODO: refactor
		return "/event/show-event";
	}
    

    
    public static class SaleableTicketCategory extends TicketCategory {
    	
    	private final Date now;

		public SaleableTicketCategory(TicketCategory ticketCategory, Date now) {
			super(ticketCategory.getId(), ticketCategory.getInception(), ticketCategory.getExpiration(), ticketCategory
					.getMaxTickets(), ticketCategory.getName(), ticketCategory.getDescription(), ticketCategory
					.getPriceInCents(), ticketCategory.isAccessRestricted());
			this.now = now;
		}
		
		public boolean getSaleable() {
			return getInception().before(now) && getExpiration().after(now);
		}
		
		public boolean getExpired() {
			return getExpiration().before(now);
		}
		
		public boolean getSaleInFuture() {
			return getInception().after(now);
		}
    	
    }
    
    
}
