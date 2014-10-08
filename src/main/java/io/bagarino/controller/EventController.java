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

import com.stripe.exception.StripeException;

import io.bagarino.manager.StripeManager;
import io.bagarino.manager.TicketReservationManager;
import io.bagarino.model.Event;
import io.bagarino.model.Ticket;
import io.bagarino.model.TicketReservation;
import io.bagarino.model.TicketReservation.TicketReservationStatus;
import io.bagarino.model.modification.TicketReservationModification;
import io.bagarino.repository.EventRepository;
import io.bagarino.repository.TicketCategoryRepository;
import io.bagarino.repository.TicketRepository;
import io.bagarino.repository.TicketReservationRepository;
import lombok.Data;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.bagarino.util.MonetaryUtil.formatCents;
import static io.bagarino.util.OptionalWrapper.optionally;
import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

@Controller
public class EventController {

    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;
    private final TicketReservationManager tickReservationManager;
    private final TicketReservationRepository ticketReservationRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final StripeManager stripeManager;

	@Autowired
	public EventController(EventRepository eventRepository,
			TicketRepository ticketRepository,
			TicketReservationManager tickReservationManager,
			TicketReservationRepository ticketReservationRepository,
			TicketCategoryRepository ticketCategoryRepository, StripeManager stripeManager) {
		this.eventRepository = eventRepository;
		this.ticketRepository = ticketRepository;
		this.tickReservationManager = tickReservationManager;
		this.ticketReservationRepository = ticketReservationRepository;
		this.ticketCategoryRepository = ticketCategoryRepository;
        this.stripeManager = stripeManager;
	}

	@RequestMapping(value = {"/"}, method = RequestMethod.GET)
	public String listEvents(Model model) {
		List<Event> events = eventRepository.findAll();
		if(events.size() == 1) {
			return "redirect:/event/" + events.get(0).getShortName();
		} else {
			model.addAttribute("events", events);
			return "/event/event-list";
		}
	}

	@RequestMapping(value = "/event/{eventName}", method = RequestMethod.GET)
	public String showEvent(@PathVariable("eventName") String eventName, Model model) {

		// TODO: for each ticket categories we should check if there are available tickets
		
		Optional<Event> event = optionally(() -> eventRepository.findByShortName(eventName));

		if(event.isPresent()) {
			model.addAttribute("event", event.get())//
				.addAttribute("ticketCategories", ticketCategoryRepository.findAllTicketCategories(event.get().getId()));
			return "/event/show-event";
		} else {
			return "redirect:/event/";
		}
	}

	@RequestMapping(value = "/event/{eventName}/reserve-tickets", method = RequestMethod.POST)
	public String reserveTicket(@PathVariable("eventName") String eventName, @ModelAttribute ReservationForm reservation) {
		
		Optional<Event> event = optionally(() -> eventRepository.findByShortName(eventName));
		if(!event.isPresent()) {
    		return "redirect:/event/";
    	}
		
		//
		final int selectionCount = reservation.selectionCount();
		Validate.isTrue(selectionCount > 0  && selectionCount <= tickReservationManager.maxAmountOfTickets());
			
		//TODO handle error cases :D
		//TODO: 25 minutes should be configurable
		Date expiration = DateUtils.addMinutes(new Date(), TicketReservationManager.RESERVATION_MINUTE);
		
		//TODO: this could fail with not enough ticket -> should we launch an exception?
		String reservationId = tickReservationManager.createTicketReservation(event.get().getId(), reservation.selected(), expiration);
		return "redirect:/event/" + eventName + "/reservation/" + reservationId;
	}



    @RequestMapping(value = "/event/{eventName}/reservation/{reservationId}", method = RequestMethod.GET)
	public String showReservationPage(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId, Model model) {
    	
    	Optional<Event> event = optionally(() -> eventRepository.findByShortName(eventName));
    	if(!event.isPresent()) {
    		return "redirect:/event/";
    	}

    	Optional<TicketReservation> reservation = optionally(() -> ticketReservationRepository.findReservationById(reservationId));

    	model.addAttribute("event", event.get());
    	
    	if(!reservation.isPresent()) {
    		model.addAttribute("reservationId", reservationId);
    		return "/event/reservation-page-not-found";
    	} else if(reservation.get().getStatus() == TicketReservationStatus.PENDING) {
    		
    		int reservationCost = totalReservationCost(reservationId);
    		
    		model.addAttribute("summary", extractSummary(reservationId));
    		model.addAttribute("free", reservationCost == 0);
    		model.addAttribute("totalPrice", formatCents(reservationCost));
    		model.addAttribute("stripe_p_key", stripeManager.getPublicKey());
    		model.addAttribute("reservationId", reservationId);
    		model.addAttribute("reservation", reservation.get());
    		
    		return "/event/reservation-page";
    	} else {
    		model.addAttribute("reservationId", reservationId);
    		model.addAttribute("ticketsByCategory", ticketRepository.findTicketsInReservation(reservationId).stream().collect(Collectors.groupingBy(Ticket::getCategoryId)).entrySet());
    		
    		return "/event/reservation-page-complete";
    	}
	}

    @RequestMapping(value = "/event/{eventName}/reservation/{reservationId}", method = RequestMethod.POST)
    public String handleReservation(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId,
                                    @RequestParam(value = "stripeToken", required = false) String stripeToken, 
                                    @RequestParam("email") String email,
                                    @RequestParam("fullName") String fullName,
                                    @RequestParam(value="billingAddress", required = false) String billingAddress,
                                    Model model) throws StripeException {
    	Optional<Event> event = optionally(() -> eventRepository.findByShortName(eventName));
    	if(!event.isPresent()) {
    		return "redirect:/event/";
    	}
    	
    	Optional<TicketReservation> ticketReservation = optionally(() -> ticketReservationRepository.findReservationById(reservationId));
    	
    	if(!ticketReservation.isPresent()) {
    		model.addAttribute("reservationId", reservationId);
    		return "/event/reservation-page-not-found";
    	}
    	
    	//TODO: expose as a validation error :D
    	Validate.isTrue(ticketReservation.get().getValidity().after(new Date()));
    	//
    	
    	final int reservationCost = totalReservationCost(reservationId);
    	// TODO handle error
    	if(reservationCost > 0) {
    		Validate.isTrue(StringUtils.isNotBlank(stripeToken));
    		stripeManager.chargeCreditCard(stripeToken, reservationCost, event.get().getCurrency(), reservationId, email, fullName, billingAddress);
    	}
        //
        
        
        // we can enter here only if the reservation is done correctly
        tickReservationManager.completeReservation(event.get().getId(), reservationId, email, fullName, billingAddress);
        //

        return "redirect:/event/" + eventName + "/reservation/" + reservationId;
    }
    
    private int totalReservationCost(String reservationId) {
    	return totalFrom(ticketRepository.findTicketsInReservation(reservationId));
    }
    
    private static int totalFrom(List<Ticket> tickets) {
    	return tickets.stream().mapToInt(Ticket::getPaidPriceInCents).sum();
    }
    
    private List<SummaryRow> extractSummary(String reservationId) {
    	List<SummaryRow> summary = new ArrayList<>();
    	List<Ticket> tickets = ticketRepository.findTicketsInReservation(reservationId);
    	tickets.stream().collect(Collectors.groupingBy(Ticket::getCategoryId)).forEach((categoryId, ticketsByCategory) -> {
    		String categoryName = ticketCategoryRepository.getById(categoryId).getName();
    		summary.add(new SummaryRow(categoryName, formatCents(ticketsByCategory.get(0).getPaidPriceInCents()), ticketsByCategory.size(), formatCents(totalFrom(ticketsByCategory))));
    	});
    	return summary;
    }
    
    @Data
    public static class SummaryRow {
    	private final String name;
    	private final String price;
    	private final int amount;
    	private final String subTotal;
    }

    @Data
	public static class ReservationForm {
		private List<TicketReservationModification> reservation;

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
