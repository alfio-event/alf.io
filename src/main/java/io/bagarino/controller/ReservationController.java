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

import static io.bagarino.util.MonetaryUtil.formatCents;
import static io.bagarino.util.OptionalWrapper.optionally;
import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import io.bagarino.controller.EventController.SaleableTicketCategory;
import io.bagarino.manager.StripeManager;
import io.bagarino.manager.TicketReservationManager;
import io.bagarino.manager.TicketReservationManager.NotEnoughTicketsException;
import io.bagarino.manager.system.MailManager;
import io.bagarino.model.Event;
import io.bagarino.model.Ticket;
import io.bagarino.model.TicketReservation;
import io.bagarino.model.TicketReservation.TicketReservationStatus;
import io.bagarino.model.modification.TicketReservationModification;
import io.bagarino.repository.EventRepository;
import io.bagarino.repository.TicketCategoryRepository;
import io.bagarino.repository.TicketRepository;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.Data;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ValidationUtils;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.request.ServletWebRequest;

import com.stripe.exception.StripeException;

@Controller
public class ReservationController {
	
	private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;
    private final TicketReservationManager tickReservationManager;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final StripeManager stripeManager;
    private final MailManager mailManager;
    //
    private final EventController eventController;
    
    @Autowired
    public ReservationController(EventRepository eventRepository,
			TicketRepository ticketRepository,
			TicketReservationManager tickReservationManager,
			TicketCategoryRepository ticketCategoryRepository, StripeManager stripeManager,
			MailManager mailManager,
			EventController eventController) {
		this.eventRepository = eventRepository;
		this.ticketRepository = ticketRepository;
		this.tickReservationManager = tickReservationManager;
		this.ticketCategoryRepository = ticketCategoryRepository;
        this.stripeManager = stripeManager;
        this.mailManager = mailManager;
        this.eventController = eventController;
	}
	
	@RequestMapping(value = "/event/{eventName}/reserve-tickets", method = {RequestMethod.POST, RequestMethod.GET})
	public String reserveTicket(@PathVariable("eventName") String eventName, @ModelAttribute ReservationForm reservation, BindingResult bindingResult, Model model, ServletWebRequest request) {
		
		Optional<Event> event = optionally(() -> eventRepository.findByShortName(eventName));
		if(!event.isPresent()) {
    		return "redirect:/";
    	}
		
		if (request.getHttpMethod() == HttpMethod.GET) {
			return "redirect:/event/" + eventName + "/";
		}
		
		reservation.validate(bindingResult, tickReservationManager, ticketCategoryRepository);
		
		if (bindingResult.hasErrors()) {
			model.addAttribute("error", bindingResult).addAttribute("hasErrors", bindingResult.hasErrors());//TODO: refactor
			return eventController.showEvent(eventName, model);
		}
			
		Date expiration = DateUtils.addMinutes(new Date(), TicketReservationManager.RESERVATION_MINUTE);
		
		try {
			String reservationId = tickReservationManager.createTicketReservation(event.get().getId(),
					reservation.selected(), expiration);
			return "redirect:/event/" + eventName + "/reservation/" + reservationId;
		} catch (NotEnoughTicketsException nete) {
			bindingResult.reject(ErrorsCode.STEP_1_NOT_ENOUGH_TICKETS);
			model.addAttribute("error", bindingResult).addAttribute("hasErrors", bindingResult.hasErrors());//TODO: refactor
			return eventController.showEvent(eventName, model);
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
    		model.addAttribute("reservationId", reservationId);
    		model.addAttribute("reservation", reservation.get());
    		
    		if(reservationCost > 0) {
    			model.addAttribute("stripe_p_key", stripeManager.getPublicKey());
    		}
    		
    		return "/event/reservation-page";
    	} else if (reservation.get().getStatus() == TicketReservationStatus.COMPLETE ){
    		model.addAttribute("reservationId", reservationId);
    		model.addAttribute("reservation", reservation.get());
    		
    		
    		List<Ticket> tickets = ticketRepository.findTicketsInReservation(reservationId);
    		
    		model.addAttribute("ticketsByCategory", tickets.stream().collect(Collectors.groupingBy(Ticket::getCategoryId)).entrySet()
    					.stream().map((e) -> Pair.of(ticketCategoryRepository.getById(e.getKey()), e.getValue()))
    					.collect(Collectors.toList()));
    		model.addAttribute("ticketsAreAllAssigned", tickets.stream().allMatch(Ticket::getAssigned));
    		
    		return "/event/reservation-page-complete";
    	} else { //reservation status is in payment.
    		throw new IllegalStateException();//FIXME
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
        
        //
        sendReservationCompleteEmail(tickReservationManager.findById(reservationId).orElseThrow(IllegalStateException::new));
        //

        return "redirect:/event/" + eventName + "/reservation/" + reservationId;
    }
    
    
    @RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/re-send-email", method = RequestMethod.POST)
    public String reSendReservationConfirmationEmail(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId) {
    	
    	Optional<Event> event = optionally(() -> eventRepository.findByShortName(eventName));
    	if(!event.isPresent()) {
    		return "redirect:/";
    	}
    	
    	Optional<TicketReservation> ticketReservation = tickReservationManager.findById(reservationId);
    	if(!ticketReservation.isPresent()) {
    		return "redirect:/event/" + eventName + "/";
    	}
    	
    	sendReservationCompleteEmail(ticketReservation.orElseThrow(IllegalStateException::new));
    	
    	return "redirect:/event/" + eventName + "/reservation/" + reservationId+"?confirmation-email-sent=true";
    }
    
    //TODO: complete, additionally, the mail should be sent asynchronously
    private void sendReservationCompleteEmail(TicketReservation reservation) {
    	mailManager.mailer().send(reservation.getEmail(), "reservation complete :D", "here be link", Optional.of("here be link html"));
    }
    
    
    private static int totalFrom(List<Ticket> tickets) {
    	return tickets.stream().mapToInt(Ticket::getPaidPriceInCents).sum();
    }
    
    private int totalReservationCost(String reservationId) {
    	return totalFrom(ticketRepository.findTicketsInReservation(reservationId));
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
				bindingResult.reject(ErrorsCode.STEP_1_SELECT_AT_LEAST_ONE);
			}
			
			if(selectionCount >  tickReservationManager.maxAmountOfTickets()) {
				bindingResult.reject(ErrorsCode.STEP_1_OVER_MAXIMUM);//FIXME: we must display the maximum amount of tickets
			}
			
			final Date now = new Date();
			
			selected().forEach((r) -> {
				SaleableTicketCategory ticketCategory = new SaleableTicketCategory(ticketCategoryRepository.getById(r.getTicketCategoryId()), now);
				
				if (!ticketCategory.getSaleable()) {
					bindingResult.reject(ErrorsCode.STEP_1_TICKET_CATEGORY_MUST_BE_SALEABLE); // TODO add correct field
				}
				if (ticketCategory.isAccessRestricted()) {
					bindingResult.reject(ErrorsCode.STEP_1_ACCESS_RESTRICTED); //
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
			
			//email, fullname maxlength is 255
			//billing address maxlength is 2048
			
			if(email != null && !email.contains("@")) {
				bindingResult.rejectValue("email", "not_an_email");
			}
			
			ValidationUtils.rejectIfEmptyOrWhitespace(bindingResult, "fullName", "fullname_missing");
        }
        
        public Boolean shouldCancelReservation() {
        	return Optional.ofNullable(cancelReservation).orElse(false);
        }
    }
    
    @Data
    public static class SummaryRow {
    	private final String name;
    	private final String price;
    	private final int amount;
    	private final String subTotal;
    }

}
