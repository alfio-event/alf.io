/**
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.controller;

import alfio.controller.form.PaymentForm;
import alfio.controller.form.ReservationForm;
import alfio.controller.form.UpdateTicketOwnerForm;
import alfio.controller.support.TemplateManager;
import alfio.controller.support.TemplateProcessor;
import alfio.manager.EventManager;
import alfio.manager.StripeManager;
import alfio.manager.TicketReservationManager;
import alfio.manager.support.OrderSummary;
import alfio.manager.support.PDFTemplateBuilder;
import alfio.manager.support.PaymentResult;
import alfio.manager.support.TextTemplateBuilder;
import alfio.manager.system.Mailer;
import alfio.model.Event;
import alfio.model.Ticket;
import alfio.model.TicketCategory;
import alfio.model.TicketReservation;
import alfio.model.modification.TicketReservationWithOptionalCodeModification;
import alfio.model.user.Organization;
import alfio.repository.EventRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.TicketRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.OptionalWrapper;
import alfio.util.ValidationResult;
import alfio.util.Validator;
import com.google.zxing.WriterException;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
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
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
	private final TicketRepository ticketRepository;
	private final TicketCategoryRepository ticketCategoryRepository;
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
								 TicketRepository ticketRepository,
								 TicketCategoryRepository ticketCategoryRepository, EventController eventController) {
		this.eventRepository = eventRepository;
		this.eventManager = eventManager;
		this.ticketReservationManager = ticketReservationManager;
		this.organizationRepository = organizationRepository;
		this.stripeManager = stripeManager;
		this.mailer = mailer;
		this.templateManager = templateManager;
		this.messageSource = messageSource;
		this.ticketRepository = ticketRepository;
		this.ticketCategoryRepository = ticketCategoryRepository;
		this.eventController = eventController;
	}

	@RequestMapping(value = "/event/{eventName}/reserve-tickets", method = { RequestMethod.POST, RequestMethod.GET })
	public String reserveTicket(@PathVariable("eventName") String eventName,
			@ModelAttribute ReservationForm reservation, BindingResult bindingResult, Model model,
			ServletWebRequest request) {

		Optional<Event> event = OptionalWrapper.optionally(() -> eventRepository.findByShortName(eventName));
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
		} catch (TicketReservationManager.NotEnoughTicketsException nete) {
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

		Optional<Event> event = OptionalWrapper.optionally(() -> eventRepository.findByShortName(eventName));
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
		} else if (reservation.get().getStatus() == TicketReservation.TicketReservationStatus.PENDING) {

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
		} else if (reservation.get().getStatus() == TicketReservation.TicketReservationStatus.COMPLETE) {

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
			model.asMap().putIfAbsent("validationResult", ValidationResult.success());
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

		Optional<Event> eventOptional = OptionalWrapper.optionally(() -> eventRepository.findByShortName(eventName));
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
			bindingResult.reject(ErrorsCode.STEP_2_ORDER_EXPIRED);
		}
		final TicketReservationManager.TotalPrice reservationCost = ticketReservationManager.totalReservationCostWithVAT(reservationId);
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
            MessageSourceResolvable message = new DefaultMessageSourceResolvable(new String[]{errorMessageCode, StripeManager.STRIPE_UNEXPECTED});
            bindingResult.reject(ErrorsCode.STEP_2_PAYMENT_PROCESSING_ERROR, new Object[]{messageSource.getMessage(message, locale)}, null);
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

		Optional<Event> event = OptionalWrapper.optionally(() -> eventRepository.findByShortName(eventName));
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


	@RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/ticket/{ticketIdentifier}/assign", method = RequestMethod.POST, headers = "X-Requested-With=XMLHttpRequest")
	public String ajaxAssignTicketToPerson(@PathVariable("eventName") String eventName,
									   @PathVariable("reservationId") String reservationId,
									   @PathVariable("ticketIdentifier") String ticketIdentifier,
									   UpdateTicketOwnerForm updateTicketOwner,
									   BindingResult bindingResult,
									   HttpServletRequest request,
									   Model model) throws Exception {

		assignTicket(eventName, reservationId, ticketIdentifier, updateTicketOwner, bindingResult, request, model);
		return "/event/assign-ticket-result";

	}

	@RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/ticket/{ticketIdentifier}/assign", method = RequestMethod.POST)
	public String assignTicketToPerson(@PathVariable("eventName") String eventName,
									   @PathVariable("reservationId") String reservationId,
									   @PathVariable("ticketIdentifier") String ticketIdentifier,
									   UpdateTicketOwnerForm updateTicketOwner,
									   BindingResult bindingResult,
									   HttpServletRequest request,
									   Model model) throws Exception {

		Optional<Triple<ValidationResult, Event, Ticket>> result = assignTicket(eventName, reservationId, ticketIdentifier, updateTicketOwner, bindingResult, request, model);
		return result.map(t -> "redirect:/event/"+t.getMiddle().getShortName()+"/reservation/"+t.getRight().getTicketsReservationId()).orElse("redirect:/");
	}

	private Optional<Triple<ValidationResult, Event, Ticket>> assignTicket(String eventName, String reservationId, String ticketIdentifier, UpdateTicketOwnerForm updateTicketOwner, BindingResult bindingResult, HttpServletRequest request, Model model) {
		Optional<Triple<ValidationResult, Event, Ticket>> triple = ticketReservationManager.fetchComplete(eventName, reservationId, ticketIdentifier)
				.map(result -> {
					Ticket t = result.getRight();
					Validate.isTrue(!t.getLockedAssignment(), "cannot change a locked ticket");
					final Event event = result.getLeft();
					final TicketReservation ticketReservation = result.getMiddle();
					ValidationResult validationResult = Validator.validateTicketAssignment(updateTicketOwner, bindingResult)
							.ifSuccess(() -> updateTicketOwner(updateTicketOwner, request, t, event, ticketReservation));
					return Triple.of(validationResult, event, ticketRepository.findByUUID(t.getUuid()));
				});
		triple.ifPresent(t -> {
			model.addAttribute("value", t.getRight());
			model.addAttribute("validationResult", t.getLeft());
			model.addAttribute("countries", getLocalizedCountries(RequestContextUtils.getLocale(request)));
			model.addAttribute("event", t.getMiddle());
		});
		return triple;
	}

	private void updateTicketOwner(UpdateTicketOwnerForm updateTicketOwner, HttpServletRequest request, Ticket t, Event event, TicketReservation ticketReservation) {
		ticketReservationManager.updateTicketOwner(t, RequestContextUtils.getLocale(request), event, updateTicketOwner,
				getConfirmationTextBuilder(request, t, event, ticketReservation),
				getOwnerChangeTextBuilder(request, t, event),
				preparePdfTicket(request, event, ticketReservation, t));
	}

	private TextTemplateBuilder getOwnerChangeTextBuilder(HttpServletRequest request, Ticket t, Event event) {
		return TemplateProcessor.buildEmailForOwnerChange(t.getEmail(), event, t, organizationRepository, ticketReservationManager, templateManager, request);
	}

	private TextTemplateBuilder getConfirmationTextBuilder(HttpServletRequest request, Ticket t, Event event, TicketReservation ticketReservation) {
		return TemplateProcessor.buildEmail(event, organizationRepository, ticketReservation, t, templateManager, request);
	}

	private PDFTemplateBuilder preparePdfTicket(HttpServletRequest request, Event event, TicketReservation ticketReservation, Ticket ticket) {
		TicketCategory ticketCategory = ticketCategoryRepository.getById(ticket.getCategoryId(), event.getId());
		Organization organization = organizationRepository.getById(event.getOrganizationId());
		try {
			return TemplateProcessor.buildPDFTicket(request, event, ticketReservation, ticket, ticketCategory, organization, templateManager);
		} catch (WriterException | IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private void sendReservationCompleteEmail(HttpServletRequest request, Event event, TicketReservation reservation) {

		String reservationTxt = templateManager.render("/alfio/templates/confirmation-email-txt.ms",
				prepareModelForReservationEmail(event, reservation), request);
		
		mailer.send(reservation.getEmail(), messageSource.getMessage("reservation-email-subject",
				new Object[] { event.getShortName() }, RequestContextUtils.getLocale(request)), reservationTxt,
				Optional.empty());
	}
	
	private void sendReservationCompleteEmailToOrganizer(HttpServletRequest request, Event event, TicketReservation reservation) {
		Organization organization = organizationRepository.getById(event.getOrganizationId());
		String reservationInfo = templateManager.render("/alfio/templates/confirmation-email-for-organizer-txt.ms",
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
