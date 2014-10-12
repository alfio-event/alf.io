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
import io.bagarino.manager.system.MailManager;
import io.bagarino.model.Event;
import io.bagarino.model.Ticket;
import io.bagarino.model.TicketCategory;
import io.bagarino.model.TicketReservation;
import io.bagarino.model.TicketReservation.TicketReservationStatus;
import io.bagarino.model.modification.TicketReservationModification;
import io.bagarino.repository.EventRepository;
import io.bagarino.repository.TicketCategoryRepository;
import io.bagarino.repository.TicketRepository;
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
    private final TicketCategoryRepository ticketCategoryRepository;
    private final StripeManager stripeManager;
    private final MailManager mailManager;

	@Autowired
	public EventController(EventRepository eventRepository,
			TicketRepository ticketRepository,
			TicketReservationManager tickReservationManager,
			TicketCategoryRepository ticketCategoryRepository, StripeManager stripeManager,
			MailManager mailManager) {
		this.eventRepository = eventRepository;
		this.ticketRepository = ticketRepository;
		this.tickReservationManager = tickReservationManager;
		this.ticketCategoryRepository = ticketCategoryRepository;
        this.stripeManager = stripeManager;
        this.mailManager = mailManager;
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

		// TODO: for each ticket categories we should check if there are available tickets, and if they can be sold (and check the visibility)
		
		Optional<Event> event = optionally(() -> eventRepository.findByShortName(eventName));
		
		if(!event.isPresent()) {
			return "redirect:/";
		}

		final Date now = new Date();
		//hide access restricted ticket categories
		List<SellableTicketCategory> t = ticketCategoryRepository.findAllTicketCategories(event.get().getId()).stream().filter((c) -> !c.isAccessRestricted()).map((m) -> new SellableTicketCategory(m, now)).collect(Collectors.toList());
		//
		model.addAttribute("event", event.get())//
			.addAttribute("ticketCategories", t);
		return "/event/show-event";
	}

	@RequestMapping(value = "/event/{eventName}/reserve-tickets", method = RequestMethod.POST)
	public String reserveTicket(@PathVariable("eventName") String eventName, @ModelAttribute ReservationForm reservation) {
		
		Optional<Event> event = optionally(() -> eventRepository.findByShortName(eventName));
		if(!event.isPresent()) {
    		return "redirect:/";
    	}
		
		final Date now = new Date();
		
		//
		final int selectionCount = reservation.selectionCount();
		Validate.isTrue(selectionCount > 0  && selectionCount <= tickReservationManager.maxAmountOfTickets(), "must select at least 1 ticket and less than maximum amount");
		
		// check if the ticket category is saleable / access restricted
		reservation.selected().forEach((r) -> {
			SellableTicketCategory ticketCategory = new SellableTicketCategory(ticketCategoryRepository.getById(r.getTicketCategoryId()), now);
			Validate.isTrue(ticketCategory.getSaleable(), "ticket category must be sellable");
			Validate.isTrue(!ticketCategory.isAccessRestricted(), "ticket category cannot be access restricted");
		});
		//
			
		//TODO handle error cases :D
		Date expiration = DateUtils.addMinutes(new Date(), TicketReservationManager.RESERVATION_MINUTE);
		
		//TODO: this could fail with not enough ticket -> validation error
		String reservationId = tickReservationManager.createTicketReservation(event.get().getId(), reservation.selected(), expiration);
		return "redirect:/event/" + eventName + "/reservation/" + reservationId;
	}



    @RequestMapping(value = "/event/{eventName}/reservation/{reservationId}", method = RequestMethod.GET)
	public String showReservationPage(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId, Model model) {
    	
    	Optional<Event> event = optionally(() -> eventRepository.findByShortName(eventName));
    	if(!event.isPresent()) {
    		return "redirect:/";
    	}

    	Optional<TicketReservation> reservation = tickReservationManager.findById(reservationId);

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
                                    @RequestParam(value = "email", required = false) String email,
                                    @RequestParam(value = "fullName", required = false) String fullName,
                                    @RequestParam(value="billingAddress", required = false) String billingAddress,
                                    @RequestParam(value="cancel-reservation", required = false) Boolean cancelReservation,
                                    Model model) throws StripeException {
    	
    	Optional<Boolean> cancel = Optional.ofNullable(cancelReservation);
    	Optional<Event> event = optionally(() -> eventRepository.findByShortName(eventName));
    	if(!event.isPresent()) {
    		return "redirect:/";
    	}
    	
    	Optional<TicketReservation> ticketReservation = tickReservationManager.findById(reservationId);
    	
    	if(!ticketReservation.isPresent()) {
    		model.addAttribute("reservationId", reservationId);
    		return "/event/reservation-page-not-found";
    	}
    	
    	if(cancel.isPresent()) {
    		tickReservationManager.cancelPendingReservation(reservationId);
			return "redirect:/event/" + eventName + "/";
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
        
        //TODO: complete
        mailManager.getMailer().send(email, "reservation complete :D", "here be link", Optional.of("here be link html"));
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
    
    public static class SellableTicketCategory extends TicketCategory {
    	
    	private final Date now;

		public SellableTicketCategory(TicketCategory ticketCategory, Date now) {
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
