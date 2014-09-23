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

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import io.bagarino.manager.EventManager;
import io.bagarino.manager.StripeManager;
import io.bagarino.model.TicketReservation;
import io.bagarino.model.TicketReservation.TicketReservationStatus;
import io.bagarino.model.modification.TicketReservationModification;
import io.bagarino.repository.EventRepository;
import io.bagarino.repository.TicketCategoryRepository;
import io.bagarino.repository.TicketRepository;

import java.util.Date;
import java.util.List;

import lombok.Data;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import com.stripe.exception.StripeException;

@Controller
public class EventController {

    private final EventManager eventManager;
    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final StripeManager stripeManager;

	@Autowired
	public EventController(EventManager eventManager, EventRepository eventRepository,
			TicketRepository ticketRepository,
			TicketCategoryRepository ticketCategoryRepository, StripeManager stripeManager) {
		this.eventManager = eventManager;
		this.eventRepository = eventRepository;
		this.ticketRepository = ticketRepository;
		this.ticketCategoryRepository = ticketCategoryRepository;
        this.stripeManager = stripeManager;
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
	public String reserveTicket(@PathVariable("eventId") int eventId, @ModelAttribute ReservationForm reservation) {
		//TODO handle error cases :D
		
		Validate.isTrue(reservation.selectionCount() > 0);
		
		String reservationId = eventManager.createTicketReservation(eventId, reservation.selected(), DateUtils.addMinutes(new Date(), 25));
		return "redirect:/event/" + eventId + "/reservation/" + reservationId;
	}



    @RequestMapping(value = "/event/{eventId}/reservation/{reservationId}", method = RequestMethod.GET)
	public String showReservationPage(@PathVariable("eventId") int eventId, @PathVariable("reservationId") String reservationId, Model model) {
    	
    	TicketReservation reservation = ticketRepository.findReservationById(reservationId);
    	
    	if(reservation.getStatus() == TicketReservationStatus.PENDING) {
    		model.addAttribute("event", eventRepository.findById(eventId));
    		model.addAttribute("reservationId", reservationId);
    		return "/event/reservation-page";
    	} else {
    		return "/event/reservation-page-complete";
    	}
	}

    @RequestMapping(value = "/event/{eventId}/reservation/{reservationId}", method = RequestMethod.POST)
    public String handleReservation(@PathVariable("eventId") int eventId, @PathVariable("reservationId") String reservationId, @RequestParam("stripeEmail") String customerEmail,
                                    @RequestParam("stripeToken") String stripeToken, Model model) throws StripeException {
    	
    	// TODO handle error/other payment methods
        stripeManager.chargeCreditCard(stripeToken);
        //
        
        
        // we can enter here only if the reservation is done correctly
        eventManager.completeReservation(eventId, reservationId, customerEmail);
        //

        return "redirect:/event/" + eventId + "/reservation/" + reservationId;
    }

    @Data
	public static class ReservationForm {
		List<TicketReservationModification> reservation;

		private List<TicketReservationModification> selected() {
			return ofNullable(reservation).orElse(emptyList()).stream()
					.filter((e) -> e!= null && e.getAmount() != null && e.getTicketCategoryId() != null && e.getAmount() > 0)
					.collect(toList());
		}
		
		private int selectionCount() {
			return selected().stream().mapToInt(TicketReservationModification::getAmount).sum();
		}
	}
}
