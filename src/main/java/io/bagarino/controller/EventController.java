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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import lombok.Data;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
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

	@RequestMapping(value = "/event/{eventId}", method = RequestMethod.GET)
	public String showEvent(@PathVariable("eventId") int eventId, Model model) {

		// TODO: for each ticket categories we should check if there are available tickets
		
		Optional<Event> event = optionally(() -> eventRepository.findById(eventId));

		if(event.isPresent()) {
			model.addAttribute("event", event.get())//
				.addAttribute("ticketCategories", ticketCategoryRepository.findAllTicketCategories(eventId));
			return "/event/show-event";
		} else {
			return "redirect:/event/";
		}
	}

	@RequestMapping(value = "/event/{eventId}/reserve-tickets", method = RequestMethod.POST)
	public String reserveTicket(@PathVariable("eventId") int eventId, @ModelAttribute ReservationForm reservation) {
		
		if(!optionally(() -> eventRepository.findById(eventId)).isPresent()) {
    		return "redirect:/event/";
    	}
		

		Validate.isTrue(reservation.selectionCount() > 0);
			
		//TODO handle error cases :D
		//TODO: 25 minutes should be configurable
		Date expiration = DateUtils.addMinutes(new Date(), 25);
		String reservationId = tickReservationManager.createTicketReservation(eventId, reservation.selected(), expiration);
		return "redirect:/event/" + eventId + "/reservation/" + reservationId;
	}



    @RequestMapping(value = "/event/{eventId}/reservation/{reservationId}", method = RequestMethod.GET)
	public String showReservationPage(@PathVariable("eventId") int eventId, @PathVariable("reservationId") String reservationId, Model model) {
    	 
    	if(!optionally(() -> eventRepository.findById(eventId)).isPresent()) {
    		return "redirect:/event/";
    	}

    	Optional<TicketReservation> reservation = optionally(() -> ticketReservationRepository.findReservationById(reservationId));

    	
    	if(!reservation.isPresent()) {
    		model.addAttribute("reservationId", reservationId);
    		return "/event/reservation-page-not-found";
    	} else if(reservation.get().getStatus() == TicketReservationStatus.PENDING) {
    		
    		model.addAttribute("summary", extractSummary(reservationId));
    		model.addAttribute("totalPrice", totalReservationCost(reservationId));
    		model.addAttribute("stripe_p_key", stripeManager.getPublicKey());
    		model.addAttribute("event", eventRepository.findById(eventId));
    		model.addAttribute("reservationId", reservationId);
    		
    		return "/event/reservation-page";
    	} else {
    		return "/event/reservation-page-complete";
    	}
	}

    @RequestMapping(value = "/event/{eventId}/reservation/{reservationId}", method = RequestMethod.POST)
    public String handleReservation(@PathVariable("eventId") int eventId, @PathVariable("reservationId") String reservationId,
                                    @RequestParam("stripeToken") String stripeToken, Model model) throws StripeException {
    	
    	if(!optionally(() -> eventRepository.findById(eventId)).isPresent()) {
    		return "redirect:/event/";
    	}
    	
    	if(!optionally(() -> ticketReservationRepository.findReservationById(reservationId)).isPresent()) {
    		model.addAttribute("reservationId", reservationId);
    		return "/event/reservation-page-not-found";
    	}
    	
    	// TODO handle error/other payment methods
    	//FIXME: there is a mismatch between the price of the ticket (chf) and the expected stripe value (cents?)
        stripeManager.chargeCreditCard(stripeToken, totalReservationCost(reservationId).longValueExact());
        //
        
        
        // we can enter here only if the reservation is done correctly
        tickReservationManager.completeReservation(eventId, reservationId);
        //

        return "redirect:/event/" + eventId + "/reservation/" + reservationId;
    }
    
    private BigDecimal totalReservationCost(String reservationId) {
    	return totalFrom(ticketRepository.findTicketsInReservation(reservationId));
    }
    
    private BigDecimal totalFrom(List<Ticket> tickets) {
    	return tickets.stream().map(Ticket::getPaidPrice).reduce((a,b) -> a.add(b)).orElseThrow(IllegalStateException::new);
    }
    
    private List<SummaryRow> extractSummary(String reservationId) {
    	List<SummaryRow> summary = new ArrayList<>();
    	List<Ticket> tickets = ticketRepository.findTicketsInReservation(reservationId);
    	tickets.stream().collect(Collectors.groupingBy(Ticket::getCategoryId)).forEach((categoryId, ticketsByCategory) -> {
    		String categoryName = ticketCategoryRepository.getById(categoryId).getName();
    		summary.add(new SummaryRow(categoryName, ticketsByCategory.get(0).getPaidPrice(), ticketsByCategory.size(), totalFrom(ticketsByCategory)));
    	});
    	return summary;
    }
    
    @Data
    public static class SummaryRow {
    	private final String name;
    	private final BigDecimal price;
    	private final int amount;
    	private final BigDecimal subTotal;
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
    
    private <T> Optional<T> optionally(Supplier<T> s) {
    	 try {
    		 return Optional.of(s.get());
    	 } catch(EmptyResultDataAccessException e) {
    		 return Optional.empty();
    	 }
    }
}
