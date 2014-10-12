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
import io.bagarino.manager.TicketReservationManager.NotEnoughTicketsException;
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
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ValidationUtils;
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

		// TODO: for each ticket categories we should check if there are available tickets (to show sold out text)
		
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
		model.asMap().putIfAbsent("hasErrors", false);//TODO: refactor
		return "/event/show-event";
	}

	@RequestMapping(value = "/event/{eventName}/reserve-tickets", method = RequestMethod.POST)
	public String reserveTicket(@PathVariable("eventName") String eventName, @ModelAttribute ReservationForm reservation, BindingResult bindingResult, Model model) {
		
		Optional<Event> event = optionally(() -> eventRepository.findByShortName(eventName));
		if(!event.isPresent()) {
    		return "redirect:/";
    	}
		
		reservation.validate(bindingResult, tickReservationManager, ticketCategoryRepository);
		
		if (bindingResult.hasErrors()) {
			model.addAttribute("error", bindingResult).addAttribute("hasErrors", bindingResult.hasErrors());//TODO: refactor
			return showEvent(eventName, model);
		}
			
		Date expiration = DateUtils.addMinutes(new Date(), TicketReservationManager.RESERVATION_MINUTE);
		
		try {
			String reservationId = tickReservationManager.createTicketReservation(event.get().getId(),
					reservation.selected(), expiration);
			return "redirect:/event/" + eventName + "/reservation/" + reservationId;
		} catch (NotEnoughTicketsException nete) {
			bindingResult.reject("not_enough_ticket_exception");
			model.addAttribute("error", bindingResult).addAttribute("hasErrors", bindingResult.hasErrors());//TODO: refactor
			return showEvent(eventName, model);
		}
	}



    @RequestMapping(value = "/event/{eventName}/reservation/{reservationId}", method = RequestMethod.GET)
	public String showReservationPage(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId, Model model) {
    	
    	Optional<Event> event = optionally(() -> eventRepository.findByShortName(eventName));
    	if(!event.isPresent()) {
    		return "redirect:/";
    	}

    	Optional<TicketReservation> reservation = tickReservationManager.findById(reservationId);

    	model.addAttribute("event", event.get());
    	model.asMap().putIfAbsent("hasErrors", false);
    	
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
    								PaymentForm paymentForm, BindingResult bindingResult,
                                    Model model) throws StripeException {
    	
    	Optional<Event> event = optionally(() -> eventRepository.findByShortName(eventName));
    	if(!event.isPresent()) {
    		return "redirect:/";
    	}
    	
    	Optional<TicketReservation> ticketReservation = tickReservationManager.findById(reservationId);
    	
    	if(!ticketReservation.isPresent()) {
    		model.addAttribute("reservationId", reservationId);
    		return "/event/reservation-page-not-found";
    	}
    	
    	if(paymentForm.shouldCancelReservation()) {
    		tickReservationManager.cancelPendingReservation(reservationId);
			return "redirect:/event/" + eventName + "/";
    	}
    	
    	if(!ticketReservation.get().getValidity().after(new Date())) {
    		bindingResult.reject("ticket_reservation_no_more_valid");
    	}
    	
    	final int reservationCost = totalReservationCost(reservationId);
    	
    	//
    	paymentForm.validate(bindingResult, reservationCost);
    	//
    	
    	if (bindingResult.hasErrors()) {
			model.addAttribute("error", bindingResult).addAttribute("hasErrors", bindingResult.hasErrors());//TODO: refactor
			return showReservationPage(eventName, reservationId, model);
    	}
    	
    	
    	String email = paymentForm.getEmail(), fullName = paymentForm.getFullName(), billingAddress = paymentForm.getBillingAddress();
    	
    	if(reservationCost > 0) {
    		//transition to IN_PAYMENT, so we can keep track if we have a failure between the stripe payment and the completition of the reservation
    		tickReservationManager.transitionToInPayment(reservationId, email, fullName, billingAddress);
    		
    		try {
    			stripeManager.chargeCreditCard(paymentForm.getStripeToken(), reservationCost, event.get().getCurrency(), reservationId, email, fullName, billingAddress);
    		} catch(StripeException se) {
    			bindingResult.reject("payment_processor_error");
    			model.addAttribute("error", bindingResult).addAttribute("hasErrors", bindingResult.hasErrors());//TODO: refactor
    			return showReservationPage(eventName, reservationId, model);
    		}
    	}
        
        // we can enter here only if the reservation is done correctly
        tickReservationManager.completeReservation(reservationId, email, fullName, billingAddress);
        //
        
        //TODO: complete, additionally, the mail should be sent asynchronously from another thread
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
    
    // step 1 : choose tickets
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
		
		private void validate(BindingResult bindingResult, TicketReservationManager tickReservationManager, TicketCategoryRepository ticketCategoryRepository) {
			int selectionCount = selectionCount();
			
			if(selectionCount <= 0) {
				bindingResult.reject("selection_at_least_one");
			}
			
			if(selectionCount >  tickReservationManager.maxAmountOfTickets()) {
				bindingResult.reject("selection_count_over_maximum");
			}
			
			final Date now = new Date();
			
			selected().forEach((r) -> {
				SellableTicketCategory ticketCategory = new SellableTicketCategory(ticketCategoryRepository.getById(r.getTicketCategoryId()), now);
				
				if (!ticketCategory.getSaleable()) {
					bindingResult.reject("ticket_category_must_be_saleable"); // TODO add correct field
				}
				if (ticketCategory.isAccessRestricted()) {
					bindingResult.reject("ticket_category_access_restricted"); //
				}
			});
		}
	}
    
    // step 2 : payment/claim ticketss
    
    @Data
    public static class PaymentForm {
    	private String stripeToken;
        private String email;
        private String fullName;
        private String billingAddress;
        private Boolean cancelReservation;
        
        private void validate(BindingResult bindingResult, int reservationCost) {
        	
        	
			if (reservationCost > 0 && StringUtils.isBlank(stripeToken)) {
				bindingResult.reject("missing_stripe_token");
			}
			
			
			//TODO: check email/fullname length/billing address
			ValidationUtils.rejectIfEmptyOrWhitespace(bindingResult, "email", "email_missing");
			
			if(email != null && !email.contains("@")) {
				bindingResult.rejectValue("email", "not_an_email");
			}
			
			ValidationUtils.rejectIfEmptyOrWhitespace(bindingResult, "fullName", "fullname_missing");
        }
        
        public Boolean shouldCancelReservation() {
        	return Optional.ofNullable(cancelReservation).orElse(false);
        }
    }
}
