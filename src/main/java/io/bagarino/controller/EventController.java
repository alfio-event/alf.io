package io.bagarino.controller;

import io.bagarino.repository.EventRepository;
import io.bagarino.repository.TicketCategoryRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class EventController {

	private final EventRepository eventRepository;
	private final TicketCategoryRepository ticketCategoryRepository;

	@Autowired
	public EventController(EventRepository eventRepository, TicketCategoryRepository ticketCategoryRepository) {
		this.eventRepository = eventRepository;
		this.ticketCategoryRepository = ticketCategoryRepository;
	}

	@RequestMapping(value = "/event/", method = RequestMethod.GET)
	public String listEvents(Model model) {
		model.addAttribute("events", eventRepository.findAll());
		return "/event/event-list";
	}

	@RequestMapping(value = "/event/{eventId}", method = RequestMethod.GET)
	public String showEvent(@PathVariable("eventId") int eventId, Model model) {

		// TODO: for each ticket categories we should check if there are available tickets

		model.addAttribute("event", eventRepository.findById(eventId))//
				.addAttribute("ticketCategories", ticketCategoryRepository.findAllTicketCategories(eventId));
		return "/event/show-event";
	}

	@RequestMapping(value = "/event/{eventId}/reserve-tickets", method = RequestMethod.POST)
	public void reserveTicket() {
		// TODO: transactionally: check if there are enough ticket
		// -> yes, reserve the correct amount (with a expiration obviously) of tickets and redirect to the payment
		// -> no, show error page
	}
}
