package io.bagarino.controller;

import io.bagarino.repository.EventRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class EventController {

	private final EventRepository eventRepository;

	@Autowired
	public EventController(EventRepository eventRepository) {
		this.eventRepository = eventRepository;
	}

	@RequestMapping(value = "/event/", method = RequestMethod.GET)
	public String listEvents(Model model) {
		model.addAttribute("events", eventRepository.findAll());
		return "/event/event-list";
	}

	@RequestMapping(value = "/event/{eventId}", method = RequestMethod.GET)
	public String showEvent(@PathVariable("eventId") int eventId, Model model) {
		model.addAttribute("event", eventRepository.findById(eventId));
		return "/event/show-event";
	}
}
