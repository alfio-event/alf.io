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

	@RequestMapping(value = "/event/", method = RequestMethod.GET)
	public String listEvents(Model model) {
		model.addAttribute("events", eventRepository.findAll());
		return "/event/event-list";
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
		
		//TODO: should have a maximum selection count too.
		final int selectionCount = reservation.selectionCount();
		Validate.isTrue(selectionCount > 0  && selectionCount <= tickReservationManager.maxAmountOfTickets());
			
		//TODO handle error cases :D
		//TODO: 25 minutes should be configurable
		Date expiration = DateUtils.addMinutes(new Date(), 25);
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

    	
    	if(!reservation.isPresent()) {
    		model.addAttribute("reservationId", reservationId);
    		return "/event/reservation-page-not-found";
    	} else if(reservation.get().getStatus() == TicketReservationStatus.PENDING) {
    		
    		model.addAttribute("summary", extractSummary(reservationId));
    		model.addAttribute("totalPrice", formatCents(totalReservationCost(reservationId)));
    		model.addAttribute("stripe_p_key", stripeManager.getPublicKey());
    		model.addAttribute("event", event.get());
    		model.addAttribute("reservationId", reservationId);
    		
    		return "/event/reservation-page";
    	} else {
    		
    		model.addAttribute("reservationId", reservationId);
    		model.addAttribute("ticketsByCategory", ticketRepository.findTicketsInReservation(reservationId).stream().collect(Collectors.groupingBy(Ticket::getCategoryId)).entrySet());
    		
    		return "/event/reservation-page-complete";
    	}
	}

    @RequestMapping(value = "/event/{eventName}/reservation/{reservationId}", method = RequestMethod.POST)
    public String handleReservation(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId,
                                    @RequestParam("stripeToken") String stripeToken, Model model) throws StripeException {
    	
    	
    	Optional<Event> event = optionally(() -> eventRepository.findByShortName(eventName));
    	if(!event.isPresent()) {
    		return "redirect:/event/";
    	}
    	
    	if(!optionally(() -> ticketReservationRepository.findReservationById(reservationId)).isPresent()) {
    		model.addAttribute("reservationId", reservationId);
    		return "/event/reservation-page-not-found";
    	}
    	
    	//FIXME before charging the credit card we need to check that we are within the 25 minutes -> the job should remove only the transactions that are pending and older than 35 minutes
    	
    	//
    	
    	// TODO handle error - free case
        stripeManager.chargeCreditCard(stripeToken, totalReservationCost(reservationId), event.get().getCurrency());
        //
        
        
        // we can enter here only if the reservation is done correctly
        tickReservationManager.completeReservation(event.get().getId(), reservationId);
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
