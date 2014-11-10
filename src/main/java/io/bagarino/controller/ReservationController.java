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

import io.bagarino.controller.form.PaymentForm;
import io.bagarino.controller.form.ReservationForm;
import io.bagarino.controller.support.TemplateManager;
import io.bagarino.manager.EventManager;
import io.bagarino.manager.StripeManager;
import io.bagarino.manager.TicketReservationManager;
import io.bagarino.manager.TicketReservationManager.NotEnoughTicketsException;
import io.bagarino.manager.TicketReservationManager.TotalPrice;
import io.bagarino.manager.support.OrderSummary;
import io.bagarino.manager.support.PaymentResult;
import io.bagarino.manager.system.Mailer;
import io.bagarino.model.Event;
import io.bagarino.model.Ticket;
import io.bagarino.model.TicketReservation;
import io.bagarino.model.TicketReservation.TicketReservationStatus;
import io.bagarino.model.modification.TicketReservationWithOptionalCodeModification;
import io.bagarino.model.user.Organization;
import io.bagarino.repository.EventRepository;
import io.bagarino.repository.user.OrganizationRepository;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.support.RequestContextUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.bagarino.controller.ErrorsCode.STEP_2_ORDER_EXPIRED;
import static io.bagarino.controller.ErrorsCode.STEP_2_PAYMENT_PROCESSING_ERROR;
import static io.bagarino.manager.StripeManager.STRIPE_UNEXPECTED;
import static io.bagarino.util.OptionalWrapper.optionally;

@Controller
public class ReservationController {

	private final EventRepository eventRepository;
	private final EventManager eventManager;
	private final TicketReservationManager ticketReservationManager;
	private final OrganizationRepository organizationRepository;

	private final StripeManager stripeManager;
	private final Mailer mailer;
	private final TemplateManager templateManager;
	private final MessageSource messageSource;
	//
	private final EventController eventController;

	@Autowired
	public ReservationController(EventRepository eventRepository, 
			EventManager eventManager,
			TicketReservationManager ticketReservationManager,
			OrganizationRepository organizationRepository,
			StripeManager stripeManager, 
			Mailer mailer, 
			TemplateManager templateManager, 
			MessageSource messageSource,
			EventController eventController) {
		this.eventRepository = eventRepository;
		this.eventManager = eventManager;
		this.ticketReservationManager = ticketReservationManager;
		this.organizationRepository = organizationRepository;
		this.stripeManager = stripeManager;
		this.mailer = mailer;
		this.templateManager = templateManager;
		this.messageSource = messageSource;
		this.eventController = eventController;
	}

	@RequestMapping(value = "/event/{eventName}/reserve-tickets", method = { RequestMethod.POST, RequestMethod.GET })
	public String reserveTicket(@PathVariable("eventName") String eventName,
			@ModelAttribute ReservationForm reservation, BindingResult bindingResult, Model model,
			ServletWebRequest request) {

		Optional<Event> event = optionally(() -> eventRepository.findByShortName(eventName));
		if (!event.isPresent()) {
			return "redirect:/";
		}

		if (request.getHttpMethod() == HttpMethod.GET) {
			return "redirect:/event/" + eventName + "/";
		}

		Optional<List<TicketReservationWithOptionalCodeModification>> selected = reservation.validate(bindingResult, ticketReservationManager, eventManager, event.get());

		if (bindingResult.hasErrors()) {
			model.addAttribute("error", bindingResult).addAttribute("hasErrors", bindingResult.hasErrors());//
			return eventController.showEvent(eventName, reservation.getPromoCode(), model);
		}

		Date expiration = DateUtils.addMinutes(new Date(), TicketReservationManager.RESERVATION_MINUTE);

		try {
			String reservationId = ticketReservationManager.createTicketReservation(event.get().getId(),
					selected.get(), expiration);
			return "redirect:/event/" + eventName + "/reservation/" + reservationId;
		} catch (NotEnoughTicketsException nete) {
			bindingResult.reject(ErrorsCode.STEP_1_NOT_ENOUGH_TICKETS);
			model.addAttribute("error", bindingResult).addAttribute("hasErrors", bindingResult.hasErrors());//
			return eventController.showEvent(eventName, reservation.getPromoCode(), model);
		}
	}

	@RequestMapping(value = "/event/{eventName}/reservation/{reservationId}", method = RequestMethod.GET)
	public String showReservationPage(
			@PathVariable("eventName") String eventName,
			@PathVariable("reservationId") String reservationId,
			@RequestParam(value = "confirmation-email-sent", required = false, defaultValue = "false") boolean confirmationEmailSent,
			@RequestParam(value = "ticket-email-sent", required = false, defaultValue = "false") boolean ticketEmailSent,
			Model model,
			HttpServletRequest request) {

		Optional<Event> event = optionally(() -> eventRepository.findByShortName(eventName));
		if (!event.isPresent()) {
			return "redirect:/";
		}

		Optional<TicketReservation> reservation = ticketReservationManager.findById(reservationId);

		model.addAttribute("event", event.get());
		model.asMap().putIfAbsent("hasErrors", false);

		if (!reservation.isPresent()) {
			model.addAttribute("reservationId", reservationId);
			model.addAttribute("pageTitle", "reservation-page-not-found.header.title");
			return "/event/reservation-page-not-found";
		} else if (reservation.get().getStatus() == TicketReservationStatus.PENDING) {

			OrderSummary orderSummary = ticketReservationManager.orderSummaryForReservationId(reservationId, event.get());

			model.addAttribute("orderSummary", orderSummary);
			model.addAttribute("reservationId", reservationId);
			model.addAttribute("reservation", reservation.get());
			model.addAttribute("pageTitle", "reservation-page.header.title");

			if (orderSummary.getOriginalTotalPrice().getPriceWithVAT() > 0) {
				model.addAttribute("stripe_p_key", stripeManager.getPublicKey());
			}

			model.asMap().putIfAbsent("paymentForm", new PaymentForm());

			return "/event/reservation-page";
		} else if (reservation.get().getStatus() == TicketReservationStatus.COMPLETE) {
			
			
			model.addAttribute("reservationId", reservationId);
			model.addAttribute("reservation", reservation.get());
			model.addAttribute("confirmationEmailSent", confirmationEmailSent);
			model.addAttribute("ticketEmailSent", ticketEmailSent);

			List<Ticket> tickets = ticketReservationManager.findTicketsInReservation(reservationId);

			model.addAttribute(
					"ticketsByCategory",
					tickets.stream().collect(Collectors.groupingBy(Ticket::getCategoryId)).entrySet().stream()
							.map((e) -> Pair.of(eventManager.getTicketCategoryById(e.getKey(), event.get().getId()), e.getValue()))
							.collect(Collectors.toList()));
			model.addAttribute("ticketsAreAllAssigned", tickets.stream().allMatch(Ticket::getAssigned));
			model.addAttribute("countries", getLocalizedCountries(RequestContextUtils.getLocale(request)));
			model.addAttribute("pageTitle", "reservation-page-complete.header.title");
			return "/event/reservation-page-complete";
			
		} else { // reservation status has status IN_PAYMENT or STUCK.
			model.addAttribute("reservation", reservation.get());
			model.addAttribute("organizer", organizationRepository.getById(event.get().getOrganizationId()));
			model.addAttribute("pageTitle", "reservation-page-error-status.header.title");
			return "/event/reservation-page-error-status";
		}
	}

	@RequestMapping(value = "/event/{eventName}/reservation/{reservationId}", method = RequestMethod.POST)
	public String handleReservation(@PathVariable("eventName") String eventName,
			@PathVariable("reservationId") String reservationId, PaymentForm paymentForm, BindingResult bindingResult,
			Model model, HttpServletRequest request, Locale locale) {

		Optional<Event> eventOptional = optionally(() -> eventRepository.findByShortName(eventName));
		if (!eventOptional.isPresent()) {
			return "redirect:/";
		}
        Event event = eventOptional.get();
		Optional<TicketReservation> ticketReservation = ticketReservationManager.findById(reservationId);
		if (!ticketReservation.isPresent()) {
			model.addAttribute("event", event);
			model.addAttribute("reservationId", reservationId);
			model.addAttribute("pageTitle", "reservation-page-not-found.header.title");
			return "/event/reservation-page-not-found";
		}
		if (paymentForm.shouldCancelReservation()) {
			ticketReservationManager.cancelPendingReservation(reservationId);
			return "redirect:/event/" + eventName + "/";
		}
		if (!ticketReservation.get().getValidity().after(new Date())) {
			bindingResult.reject(STEP_2_ORDER_EXPIRED);
		}
		final TotalPrice reservationCost = ticketReservationManager.totalReservationCostWithVAT(reservationId);
		paymentForm.validate(bindingResult, reservationCost);
		if (bindingResult.hasErrors()) {
			model.addAttribute("error", bindingResult).addAttribute("hasErrors", bindingResult.hasErrors())
					.addAttribute("paymentForm", paymentForm);//
			return showReservationPage(eventName, reservationId, false, false, model, request);
		}
        final PaymentResult status = ticketReservationManager.confirm(paymentForm.getStripeToken(), event, reservationId, paymentForm.getEmail(),
                paymentForm.getFullName(), paymentForm.getBillingAddress(), reservationCost);

        if(!status.isSuccessful()) {
            String errorMessageCode = status.getErrorCode().get();
            MessageSourceResolvable message = new DefaultMessageSourceResolvable(new String[]{errorMessageCode, STRIPE_UNEXPECTED});
            bindingResult.reject(STEP_2_PAYMENT_PROCESSING_ERROR, new Object[]{messageSource.getMessage(message, locale)}, null);
            model.addAttribute("error", bindingResult)
                    .addAttribute("hasErrors", bindingResult.hasErrors())
                    .addAttribute("paymentForm", paymentForm);
            return showReservationPage(eventName, reservationId, false, false, model, request);
        }

        //
        TicketReservation reservation = ticketReservationManager.findById(reservationId).orElseThrow(IllegalStateException::new);
        sendReservationCompleteEmail(request, event,reservation);
        sendReservationCompleteEmailToOrganizer(request, event, reservation);
		//

		return "redirect:/event/" + eventName + "/reservation/" + reservationId;
	}

	@RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/re-send-email", method = RequestMethod.POST)
	public String reSendReservationConfirmationEmail(@PathVariable("eventName") String eventName,
			@PathVariable("reservationId") String reservationId, HttpServletRequest request) {

		Optional<Event> event = optionally(() -> eventRepository.findByShortName(eventName));
		if (!event.isPresent()) {
			return "redirect:/";
		}

		Optional<TicketReservation> ticketReservation = ticketReservationManager.findById(reservationId);
		if (!ticketReservation.isPresent()) {
			return "redirect:/event/" + eventName + "/";
		}

		sendReservationCompleteEmail(request, event.get(), ticketReservation.orElseThrow(IllegalStateException::new));
		return "redirect:/event/" + eventName + "/reservation/" + reservationId + "?confirmation-email-sent=true";
	}

	private void sendReservationCompleteEmail(HttpServletRequest request, Event event, TicketReservation reservation) {

		String reservationTxt = templateManager.render("/io/bagarino/templates/confirmation-email-txt.ms", 
				prepareModelForReservationEmail(event, reservation), request);
		
		mailer.send(reservation.getEmail(), messageSource.getMessage("reservation-email-subject",
				new Object[] { event.getShortName() }, RequestContextUtils.getLocale(request)), reservationTxt,
				Optional.empty());
	}
	
	private void sendReservationCompleteEmailToOrganizer(HttpServletRequest request, Event event, TicketReservation reservation) {
		Organization organization = organizationRepository.getById(event.getOrganizationId());
		String reservationInfo = templateManager.render("/io/bagarino/templates/confirmation-email-for-organizer-txt.ms", 
				prepareModelForReservationEmail(event, reservation), request);
		
		mailer.send(organization.getEmail(), "Reservation complete " + reservation.getId(), reservationInfo, Optional.empty());
	}

	private Map<String, Object> prepareModelForReservationEmail(Event event, TicketReservation reservation) {
		Map<String, Object> model = new HashMap<>();
		model.put("organization", organizationRepository.getById(event.getOrganizationId()));
		model.put("event", event);
		model.put("ticketReservation", reservation);
		
		Optional<String> vat = ticketReservationManager.getVAT();
		
		model.put("hasVat", vat.isPresent());
		model.put("vatNr", vat.orElse(""));

		OrderSummary orderSummary = ticketReservationManager.orderSummaryForReservationId(reservation.getId(), event);
		model.put("tickets", ticketReservationManager.findTicketsInReservation(reservation.getId()));
		model.put("orderSummary", orderSummary);
		model.put("reservationUrl", ticketReservationManager.reservationUrl(reservation.getId()));
		return model;
	}
	
	private static List<Pair<String, String>> getLocalizedCountries(Locale locale) {
		return Stream.of(Locale.getISOCountries())
				.map(isoCode -> Pair.of(isoCode, new Locale("", isoCode).getDisplayCountry(locale)))
				.sorted(Comparator.comparing(Pair::getRight))
				.collect(Collectors.toList());
	}

}
