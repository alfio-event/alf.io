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
import alfio.controller.form.UpdateTicketOwnerForm;
import alfio.controller.support.SessionUtil;
import alfio.controller.support.TemplateProcessor;
import alfio.manager.EventManager;
import alfio.manager.NotificationManager;
import alfio.manager.StripeManager;
import alfio.manager.TicketReservationManager;
import alfio.manager.support.OrderSummary;
import alfio.manager.support.PartialTicketPDFGenerator;
import alfio.manager.support.PartialTicketTextGenerator;
import alfio.manager.support.PaymentResult;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.system.Mailer;
import alfio.model.Event;
import alfio.model.Ticket;
import alfio.model.TicketCategory;
import alfio.model.TicketReservation;
import alfio.model.TicketReservation.TicketReservationStatus;
import alfio.model.system.ConfigurationKeys;
import alfio.model.user.Organization;
import alfio.repository.EventRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.TicketRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.*;
import com.google.zxing.WriterException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.ZonedDateTime;
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
	private final ConfigurationManager configurationManager;
	private final NotificationManager notificationManager;

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
								 TicketCategoryRepository ticketCategoryRepository,
								 ConfigurationManager configurationManager,
								 NotificationManager notificationManager) {
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
		this.configurationManager = configurationManager;
		this.notificationManager = notificationManager;
	}

	@RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/book", method = RequestMethod.GET)
	public String showPaymentPage(@PathVariable("eventName") String eventName,
								  @PathVariable("reservationId") String reservationId,
								  Model model,
								  HttpServletRequest request) {

		Optional<Event> event = OptionalWrapper.optionally(() -> eventRepository.findByShortName(eventName));
		if (!event.isPresent()) {
			return "redirect:/";
		}

		Optional<TicketReservation> reservation = ticketReservationManager.findById(reservationId);
		if(reservation.isPresent() && reservation.get().getStatus() == TicketReservationStatus.PENDING) {
			OrderSummary orderSummary = ticketReservationManager.orderSummaryForReservationId(reservationId, event.get());
			model.addAttribute("orderSummary", orderSummary);
			model.addAttribute("reservationId", reservationId);
			model.addAttribute("reservation", reservation.get());
			model.addAttribute("pageTitle", "reservation-page.header.title");
			model.addAttribute("delayForOfflinePayment", configurationManager.getIntConfigValue(ConfigurationKeys.OFFLINE_PAYMENT_DAYS, 5));
			model.addAttribute("event", event.get());
			if (orderSummary.getOriginalTotalPrice().getPriceWithVAT() > 0) {
				model.addAttribute("stripe_p_key", stripeManager.getPublicKey());
			}
			Map<String, Object> modelMap = model.asMap();
			modelMap.putIfAbsent("paymentForm", new PaymentForm());
			modelMap.putIfAbsent("hasErrors", false);
			return "/event/reservation-page";
		} else {
			return redirectReservation(reservation, eventName, reservationId);
		}
	}

	@RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/success", method = RequestMethod.GET)
	public String showConfirmationPage(@PathVariable("eventName") String eventName,
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
		if (reservation.isPresent() && reservation.get().getStatus() == TicketReservationStatus.COMPLETE) {

			SessionUtil.removeSpecialPriceData(request);

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
			model.addAttribute("event", event.get());
			model.asMap().putIfAbsent("validationResult", ValidationResult.success());
			return "/event/reservation-page-complete";
		} else {
			return redirectReservation(reservation, eventName, reservationId);
		}
	}

	@RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/failure", method = RequestMethod.GET)
	public String showFailurePage(@PathVariable("eventName") String eventName,
									   @PathVariable("reservationId") String reservationId,
									   @RequestParam(value = "confirmation-email-sent", required = false, defaultValue = "false") boolean confirmationEmailSent,
									   @RequestParam(value = "ticket-email-sent", required = false, defaultValue = "false") boolean ticketEmailSent,
									   Model model) {
		Optional<Event> event = OptionalWrapper.optionally(() -> eventRepository.findByShortName(eventName));
		if (!event.isPresent()) {
			return "redirect:/";
		}

		Optional<TicketReservation> reservation = ticketReservationManager.findById(reservationId);
		Optional<TicketReservationStatus> status = reservation.map(TicketReservation::getStatus);

		if(!status.isPresent()) {
			return redirectReservation(reservation, eventName, reservationId);
		}

		TicketReservationStatus ticketReservationStatus = status.get();
		if(ticketReservationStatus == TicketReservationStatus.IN_PAYMENT || ticketReservationStatus == TicketReservationStatus.STUCK) {
			model.addAttribute("reservation", reservation.get());
			model.addAttribute("organizer", organizationRepository.getById(event.get().getOrganizationId()));
			model.addAttribute("pageTitle", "reservation-page-error-status.header.title");
			model.addAttribute("event", event.get());
			return "/event/reservation-page-error-status";
		}

		return redirectReservation(reservation, eventName, reservationId);
	}

	@RequestMapping(value = "/event/{eventName}/reservation/{reservationId}", method = RequestMethod.GET)
	public String showReservationPage(@PathVariable("eventName") String eventName,
									  @PathVariable("reservationId") String reservationId,
									  Model model) {
		Optional<Event> event = OptionalWrapper.optionally(() -> eventRepository.findByShortName(eventName));
		if (!event.isPresent()) {
			return "redirect:/";
		}

		return redirectReservation(ticketReservationManager.findById(reservationId), eventName, reservationId);
	}

	@RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/notfound", method = RequestMethod.GET)
	public String showNotFoundPage(@PathVariable("eventName") String eventName,
								   @PathVariable("reservationId") String reservationId,
								   Model model) {

		Optional<Event> event = OptionalWrapper.optionally(() -> eventRepository.findByShortName(eventName));
		if (!event.isPresent()) {
			return "redirect:/";
		}

		Optional<TicketReservation> reservation = ticketReservationManager.findById(reservationId);

		if(!reservation.isPresent()) {
			model.addAttribute("reservationId", reservationId);
			model.addAttribute("pageTitle", "reservation-page-not-found.header.title");
			model.addAttribute("event", event.get());
			return "/event/reservation-page-not-found";
		}

		return redirectReservation(reservation, eventName, reservationId);
	}

	@RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/waitingPayment", method = RequestMethod.GET)
	public String showWaitingPaymentPage(@PathVariable("eventName") String eventName,
								   @PathVariable("reservationId") String reservationId,
								   Model model) {

		Optional<Event> event = OptionalWrapper.optionally(() -> eventRepository.findByShortName(eventName));
		if (!event.isPresent()) {
			return "redirect:/";
		}

		Optional<TicketReservation> reservation = ticketReservationManager.findById(reservationId);
		TicketReservationStatus status = reservation.map(TicketReservation::getStatus).orElse(TicketReservationStatus.PENDING);
		if(reservation.isPresent() && status == TicketReservationStatus.OFFLINE_PAYMENT) {
			Event ev = event.get();
			TicketReservation ticketReservation = reservation.get();
			OrderSummary orderSummary = ticketReservationManager.orderSummaryForReservationId(reservationId, ev);
			model.addAttribute("totalPrice", orderSummary.getTotalPrice());
			model.addAttribute("emailAddress", organizationRepository.getById(ev.getOrganizationId()).getEmail());
			model.addAttribute("reservation", ticketReservation);
			model.addAttribute("paymentReason", ticketReservationManager.getShortReservationID(reservationId));
			model.addAttribute("pageTitle", "reservation-page-waiting.header.title");
			model.addAttribute("bankAccount", configurationManager.getStringConfigValue(ConfigurationKeys.BANK_ACCOUNT_NR).orElse(""));
			model.addAttribute("expires", ZonedDateTime.ofInstant(ticketReservation.getValidity().toInstant(), ev.getZoneId()));
			model.addAttribute("event", ev);
			return "/event/reservation-waiting-for-payment";
		}

		return redirectReservation(reservation, eventName, reservationId);
	}



	private String redirectReservation(Optional<TicketReservation> ticketReservation, String eventName, String reservationId) {
		String baseUrl = "redirect:/event/" + eventName + "/reservation/" + reservationId;
		if(!ticketReservation.isPresent()) {
			return baseUrl + "/notfound";
		}
		TicketReservation reservation = ticketReservation.get();

		switch(reservation.getStatus()) {
			case PENDING:
				return baseUrl + "/book";
			case COMPLETE:
				return baseUrl + "/success";
			case OFFLINE_PAYMENT:
				return baseUrl + "/waitingPayment";
			case IN_PAYMENT:
			case STUCK:
				return baseUrl + "/failure";
		}

		return "redirect:/";
	}


	@RequestMapping(value = "/event/{eventName}/reservation/{reservationId}", method = RequestMethod.POST)
	public String handleReservation(@PathVariable("eventName") String eventName,
			@PathVariable("reservationId") String reservationId, PaymentForm paymentForm, BindingResult bindingResult,
			Model model, HttpServletRequest request, Locale locale, RedirectAttributes redirectAttributes) {

		Optional<Event> eventOptional = OptionalWrapper.optionally(() -> eventRepository.findByShortName(eventName));
		if (!eventOptional.isPresent()) {
			return "redirect:/";
		}
        Event event = eventOptional.get();
		Optional<TicketReservation> ticketReservation = ticketReservationManager.findById(reservationId);
		if (!ticketReservation.isPresent()) {
			return redirectReservation(ticketReservation, eventName, reservationId);
		}
		if (paymentForm.shouldCancelReservation()) {
			ticketReservationManager.cancelPendingReservation(reservationId);
			SessionUtil.removeSpecialPriceData(request);
			return "redirect:/event/" + eventName + "/";
		}
		if (!ticketReservation.get().getValidity().after(new Date())) {
			bindingResult.reject(ErrorsCode.STEP_2_ORDER_EXPIRED);
		}
		final TicketReservationManager.TotalPrice reservationCost = ticketReservationManager.totalReservationCostWithVAT(reservationId);
		paymentForm.validate(bindingResult, reservationCost, event.getAllowedPaymentProxies());
		if (bindingResult.hasErrors()) {
			SessionUtil.addToFlash(bindingResult, redirectAttributes);
			return redirectReservation(ticketReservation, eventName, reservationId);
		}
        final PaymentResult status = ticketReservationManager.confirm(paymentForm.getStripeToken(), event, reservationId, paymentForm.getEmail(),
                paymentForm.getFullName(), paymentForm.getBillingAddress(), reservationCost, SessionUtil.retrieveSpecialPriceSessionId(request), Optional.ofNullable(paymentForm.getPaymentMethod()));

        if(!status.isSuccessful()) {
            String errorMessageCode = status.getErrorCode().get();
            MessageSourceResolvable message = new DefaultMessageSourceResolvable(new String[]{errorMessageCode, StripeManager.STRIPE_UNEXPECTED});
            bindingResult.reject(ErrorsCode.STEP_2_PAYMENT_PROCESSING_ERROR, new Object[]{messageSource.getMessage(message, locale)}, null);
			SessionUtil.addToFlash(bindingResult, redirectAttributes);
			return redirectReservation(ticketReservation, eventName, reservationId);
        }

        //
        TicketReservation reservation = ticketReservationManager.findById(reservationId).orElseThrow(IllegalStateException::new);
        sendReservationCompleteEmail(request, event,reservation);
        sendReservationCompleteEmailToOrganizer(request, event, reservation);
		//

		return "redirect:/event/" + eventName + "/reservation/" + reservationId + "/success";
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
		return "redirect:/event/" + eventName + "/reservation/" + reservationId + "/success?confirmation-email-sent=true";
	}


	@RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/ticket/{ticketIdentifier}/assign", method = RequestMethod.POST, headers = "X-Requested-With=XMLHttpRequest")
	@ResponseBody
	public Map<String, Object> ajaxAssignTicketToPerson(@PathVariable("eventName") String eventName,
														@PathVariable("reservationId") String reservationId,
														@PathVariable("ticketIdentifier") String ticketIdentifier,
														UpdateTicketOwnerForm updateTicketOwner,
														BindingResult bindingResult,
														HttpServletRequest request,
														Model model) throws Exception {

		Optional<Triple<ValidationResult, Event, Ticket>> assignmentResult = assignTicket(eventName, reservationId, ticketIdentifier, updateTicketOwner, bindingResult, request, model);
		Map<String, Object> result = new HashMap<>();
		model.addAttribute("reservationId", reservationId);

		Optional<ValidationResult> validationResult = assignmentResult.map(Triple::getLeft);
		if(validationResult.isPresent() && validationResult.get().isSuccess()) {
			result.put("partial", templateManager.renderServletContextResource("/WEB-INF/templates/event/assign-ticket-result.ms", model.asMap(), request));
		}
		result.put("validationResult", validationResult.orElse(ValidationResult.failed(new ValidationResult.ValidationError("fullName", "error.fullname"))));
		return result;
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
		return result.map(t -> "redirect:/event/"+t.getMiddle().getShortName()+"/reservation/"+t.getRight().getTicketsReservationId()+"/success").orElse("redirect:/");
	}

	private Optional<Triple<ValidationResult, Event, Ticket>> assignTicket(String eventName, String reservationId, String ticketIdentifier, UpdateTicketOwnerForm updateTicketOwner, BindingResult bindingResult, HttpServletRequest request, Model model) {
		Optional<Triple<ValidationResult, Event, Ticket>> triple = ticketReservationManager.fetchComplete(eventName, reservationId, ticketIdentifier)
				.map(result -> {
					Ticket t = result.getRight();
					if(t.getLockedAssignment()) {
						//in case of locked assignment, fullName and Email will be overwritten
						updateTicketOwner.setFullName(t.getFullName());
						updateTicketOwner.setEmail(t.getEmail());
					}
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
				getConfirmationTextBuilder(request, event, ticketReservation),
				getOwnerChangeTextBuilder(request, t, event),
				preparePdfTicket(request, event, ticketReservation, t));
	}

	private PartialTicketTextGenerator getOwnerChangeTextBuilder(HttpServletRequest request, Ticket t, Event event) {
		return TemplateProcessor.buildEmailForOwnerChange(event, t, organizationRepository, ticketReservationManager, templateManager, request);
	}

	private PartialTicketTextGenerator getConfirmationTextBuilder(HttpServletRequest request, Event event, TicketReservation ticketReservation) {
		return TemplateProcessor.buildPartialEmail(event, organizationRepository, ticketReservation, templateManager, request);
	}

	private PartialTicketPDFGenerator preparePdfTicket(HttpServletRequest request, Event event, TicketReservation ticketReservation, Ticket ticket) {
		TicketCategory ticketCategory = ticketCategoryRepository.getById(ticket.getCategoryId(), event.getId());
		Organization organization = organizationRepository.getById(event.getOrganizationId());
		try {
			return TemplateProcessor.buildPartialPDFTicket(request, event, ticketReservation, ticketCategory, organization, templateManager);
		} catch (WriterException | IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private void sendReservationCompleteEmail(HttpServletRequest request, Event event, TicketReservation reservation) {

		Locale locale = RequestContextUtils.getLocale(request);
		String reservationTxt = templateManager.renderClassPathResource("/alfio/templates/confirmation-email-txt.ms",
				ticketReservationManager.prepareModelForReservationEmail(event, reservation), locale);
		String shortReservationID = ticketReservationManager.getShortReservationID(reservation.getId());
		notificationManager.sendSimpleEmail(event, reservation.getEmail(), messageSource.getMessage("reservation-email-subject",
				new Object[] { shortReservationID, event.getShortName() }, locale), reservationTxt);
	}
	
	private void sendReservationCompleteEmailToOrganizer(HttpServletRequest request, Event event, TicketReservation reservation) {
		Organization organization = organizationRepository.getById(event.getOrganizationId());
		String reservationInfo = templateManager.renderClassPathResource("/alfio/templates/confirmation-email-for-organizer-txt.ms",
				ticketReservationManager.prepareModelForReservationEmail(event, reservation), RequestContextUtils.getLocale(request));
		notificationManager.sendSimpleEmail(event, organization.getEmail(), "Reservation complete " + reservation.getId(), reservationInfo);
	}

	private static List<Pair<String, String>> getLocalizedCountries(Locale locale) {
		return Stream.of(Locale.getISOCountries())
				.map(isoCode -> Pair.of(isoCode, new Locale("", isoCode).getDisplayCountry(locale)))
				.sorted(Comparator.comparing(Pair::getRight))
				.collect(Collectors.toList());
	}

}
