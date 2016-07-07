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

import alfio.controller.api.support.TicketHelper;
import alfio.controller.form.PaymentForm;
import alfio.controller.form.UpdateTicketOwnerForm;
import alfio.controller.support.SessionUtil;
import alfio.controller.support.TicketDecorator;
import alfio.manager.*;
import alfio.manager.support.OrderSummary;
import alfio.manager.support.PaymentResult;
import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.model.TicketReservation.TicketReservationStatus;
import alfio.model.system.Configuration;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Organization;
import alfio.repository.EventRepository;
import alfio.repository.TicketFieldRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.ErrorsCode;
import alfio.util.TemplateManager;
import alfio.util.TemplateManager.TemplateOutput;
import alfio.util.ValidationResult;
import com.paypal.base.rest.PayPalRESTException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

import javax.servlet.http.HttpServletRequest;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static alfio.model.system.ConfigurationKeys.ALLOW_FREE_TICKETS_CANCELLATION;
import static alfio.model.system.ConfigurationKeys.BANK_ACCOUNT_NR;
import static java.util.stream.Collectors.toList;

@Controller
public class ReservationController {

    private final EventRepository eventRepository;
    private final EventManager eventManager;
    private final TicketReservationManager ticketReservationManager;
    private final OrganizationRepository organizationRepository;

    private final StripeManager stripeManager;
    private final PaypalManager paypalManager;
    private final TemplateManager templateManager;
    private final MessageSource messageSource;
    private final ConfigurationManager configurationManager;
    private final NotificationManager notificationManager;
    private final TicketHelper ticketHelper;
    private final TicketFieldRepository ticketFieldRepository;

    @Autowired
    public ReservationController(EventRepository eventRepository,
                                 EventManager eventManager,
                                 TicketReservationManager ticketReservationManager,
                                 OrganizationRepository organizationRepository,
                                 StripeManager stripeManager,
                                 TemplateManager templateManager,
                                 MessageSource messageSource,
                                 ConfigurationManager configurationManager,
                                 NotificationManager notificationManager,
                                 TicketHelper ticketHelper,
                                 TicketFieldRepository ticketFieldRepository,
                                 PaypalManager paypalManager) {
        this.eventRepository = eventRepository;
        this.eventManager = eventManager;
        this.ticketReservationManager = ticketReservationManager;
        this.organizationRepository = organizationRepository;
        this.stripeManager = stripeManager;
        this.templateManager = templateManager;
        this.messageSource = messageSource;
        this.configurationManager = configurationManager;
        this.notificationManager = notificationManager;
        this.ticketHelper = ticketHelper;
        this.ticketFieldRepository = ticketFieldRepository;
        this.paypalManager = paypalManager;
    }

    @RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/book", method = RequestMethod.GET)
    public String showPaymentPage(@PathVariable("eventName") String eventName,
                                  @PathVariable("reservationId") String reservationId,
                                  //paypal related parameters
                                  @RequestParam(value = "paymentId", required = false) String paypalPaymentId,
                                  @RequestParam(value = "PayerID", required = false) String paypalPayerID,
                                  @RequestParam(value = "paypal-success", required = false) Boolean isPaypalSuccess,
                                  @RequestParam(value = "paypal-error", required = false) Boolean isPaypalError,
                                  @RequestParam(value = "fullName", required = false) String fullName,
                                  @RequestParam(value = "email", required = false) String email,
                                  @RequestParam(value = "billingAddress", required = false) String billingAddress,
                                  @RequestParam(value = "hmac", required = false) String hmac,
                                  Model model,
                                  Locale locale) {

        return eventRepository.findOptionalByShortName(eventName)
            .map(event -> ticketReservationManager.findById(reservationId)
                .map(reservation -> {

                    if (reservation.getStatus() != TicketReservationStatus.PENDING) {
                        return redirectReservation(Optional.of(reservation), eventName, reservationId);
                    }

                    if (Boolean.TRUE.equals(isPaypalSuccess) && paypalPayerID != null && paypalPaymentId != null) {
                        model.addAttribute("paypalPaymentId", paypalPaymentId)
                            .addAttribute("paypalPayerID", paypalPayerID)
                            .addAttribute("paypalCheckoutConfirmation", true)
                            .addAttribute("fullName", fullName)
                            .addAttribute("email", email)
                            .addAttribute("billingAddress", billingAddress)
                            .addAttribute("hmac", hmac);
                    } else {
                        model.addAttribute("paypalCheckoutConfirmation", false);
                    }

                    OrderSummary orderSummary = ticketReservationManager.orderSummaryForReservationId(reservationId, event, locale);
                    model.addAttribute("orderSummary", orderSummary);
                    model.addAttribute("reservationId", reservationId);
                    model.addAttribute("reservation", reservation);
                    model.addAttribute("pageTitle", "reservation-page.header.title");
                    model.addAttribute("delayForOfflinePayment", Math.max(1, TicketReservationManager.getOfflinePaymentWaitingPeriod(event, configurationManager)));
                    model.addAttribute("event", event);
                    model.addAttribute("expressCheckoutEnabled", isExpressCheckoutEnabled(event, orderSummary));
                    boolean includeStripe = !orderSummary.getFree() && event.getAllowedPaymentProxies().contains(PaymentProxy.STRIPE);
                    model.addAttribute("includeStripe", includeStripe);
                    if (includeStripe) {
                        model.addAttribute("stripe_p_key", stripeManager.getPublicKey(event));
                    }
                    Map<String, Object> modelMap = model.asMap();
                    modelMap.putIfAbsent("paymentForm", new PaymentForm());
                    modelMap.putIfAbsent("hasErrors", false);
                    return "/event/reservation-page";
                }).orElseGet(() -> redirectReservation(Optional.empty(), eventName, reservationId)))
            .orElse("redirect:/");
    }


    @RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/success", method = RequestMethod.GET)
    public String showConfirmationPage(@PathVariable("eventName") String eventName,
                                       @PathVariable("reservationId") String reservationId,
                                       @RequestParam(value = "confirmation-email-sent", required = false, defaultValue = "false") boolean confirmationEmailSent,
                                       @RequestParam(value = "ticket-email-sent", required = false, defaultValue = "false") boolean ticketEmailSent,
                                       Model model,
                                       Locale locale,
                                       HttpServletRequest request) {

        return eventRepository.findOptionalByShortName(eventName).map(ev -> {
            Optional<TicketReservation> tr = ticketReservationManager.findById(reservationId);
            return tr.filter(r -> r.getStatus() == TicketReservationStatus.COMPLETE)
                .map(reservation -> {
                    SessionUtil.removeSpecialPriceData(request);
                    model.addAttribute("reservationId", reservationId);
                    model.addAttribute("reservation", reservation);
                    model.addAttribute("confirmationEmailSent", confirmationEmailSent);
                    model.addAttribute("ticketEmailSent", ticketEmailSent);

                    List<Ticket> tickets = ticketReservationManager.findTicketsInReservation(reservationId);
                    List<Triple<AdditionalService, List<AdditionalServiceText>, AdditionalServiceItem>> additionalServices = ticketReservationManager.findAdditionalServicesInReservation(reservationId)
                        .stream()
                        .map(t -> Triple.of(t.getLeft(), t.getMiddle().stream().filter(d -> d.getLocale().equals(locale.getLanguage())).collect(toList()), t.getRight()))
                        .collect(Collectors.toList());

                    model.addAttribute(
                        "ticketsByCategory",
                        tickets.stream().collect(Collectors.groupingBy(Ticket::getCategoryId)).entrySet().stream()
                            .map((e) -> {
                                TicketCategory category = eventManager.getTicketCategoryById(e.getKey(), ev.getId());
                                List<TicketDecorator> decorators = TicketDecorator.decorate(e.getValue(),
                                    configurationManager.getBooleanConfigValue(Configuration.from(ev.getOrganizationId(), ev.getId(), category.getId(), ALLOW_FREE_TICKETS_CANCELLATION), false),
                                    eventManager.checkTicketCancellationPrerequisites(),
                                    ticket -> ticketHelper.findTicketFieldConfigurationAndValue(ev.getId(), ticket.getId(), locale));
                                return Pair.of(category, decorators);
                            })
                            .collect(toList()));
                    boolean ticketsAllAssigned = tickets.stream().allMatch(Ticket::getAssigned);
                    model.addAttribute("ticketsAreAllAssigned", ticketsAllAssigned);
                    model.addAttribute("collapseEnabled", tickets.size() > 1 && !ticketsAllAssigned);
                    model.addAttribute("additionalServicesOnly", tickets.isEmpty() && !additionalServices.isEmpty());
                    model.addAttribute("additionalServices", additionalServices);
                    model.addAttribute("countries", ticketHelper.getLocalizedCountries(locale));
                    model.addAttribute("pageTitle", "reservation-page-complete.header.title");
                    model.addAttribute("event", ev);
                    model.asMap().putIfAbsent("validationResult", ValidationResult.success());
                    return "/event/reservation-page-complete";
                }).orElseGet(() -> redirectReservation(tr, eventName, reservationId));
        }).orElse("redirect:/");
    }

    @RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/failure", method = RequestMethod.GET)
    public String showFailurePage(@PathVariable("eventName") String eventName,
                                       @PathVariable("reservationId") String reservationId,
                                       @RequestParam(value = "confirmation-email-sent", required = false, defaultValue = "false") boolean confirmationEmailSent,
                                       @RequestParam(value = "ticket-email-sent", required = false, defaultValue = "false") boolean ticketEmailSent,
                                       Model model) {
        Optional<Event> event = eventRepository.findOptionalByShortName(eventName);
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
        Optional<Event> event = eventRepository.findOptionalByShortName(eventName);
        if (!event.isPresent()) {
            return "redirect:/";
        }

        return redirectReservation(ticketReservationManager.findById(reservationId), eventName, reservationId);
    }

    @RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/notfound", method = RequestMethod.GET)
    public String showNotFoundPage(@PathVariable("eventName") String eventName,
                                   @PathVariable("reservationId") String reservationId,
                                   Model model) {

        Optional<Event> event = eventRepository.findOptionalByShortName(eventName);
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
                                   Model model, Locale locale) {

        Optional<Event> event = eventRepository.findOptionalByShortName(eventName);
        if (!event.isPresent()) {
            return "redirect:/";
        }

        Optional<TicketReservation> reservation = ticketReservationManager.findById(reservationId);
        TicketReservationStatus status = reservation.map(TicketReservation::getStatus).orElse(TicketReservationStatus.PENDING);
        if(reservation.isPresent() && status == TicketReservationStatus.OFFLINE_PAYMENT) {
            Event ev = event.get();
            TicketReservation ticketReservation = reservation.get();
            OrderSummary orderSummary = ticketReservationManager.orderSummaryForReservationId(reservationId, ev, locale);
            model.addAttribute("totalPrice", orderSummary.getTotalPrice());
            model.addAttribute("emailAddress", organizationRepository.getById(ev.getOrganizationId()).getEmail());
            model.addAttribute("reservation", ticketReservation);
            model.addAttribute("paymentReason", ev.getShortName() + " " + ticketReservationManager.getShortReservationID(ev, reservationId));
            model.addAttribute("pageTitle", "reservation-page-waiting.header.title");
            model.addAttribute("bankAccount", configurationManager.getStringConfigValue(Configuration.from(ev.getOrganizationId(), ev.getId(), BANK_ACCOUNT_NR)).orElse(""));
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

        Optional<Event> eventOptional = eventRepository.findOptionalByShortName(eventName);
        if (!eventOptional.isPresent()) {
            return "redirect:/";
        }
        Event event = eventOptional.get();
        Optional<TicketReservation> ticketReservation = ticketReservationManager.findById(reservationId);
        if (!ticketReservation.isPresent()) {
            return redirectReservation(ticketReservation, eventName, reservationId);
        }
        if (paymentForm.shouldCancelReservation()) {
            ticketReservationManager.cancelPendingReservation(reservationId, false);
            SessionUtil.removeSpecialPriceData(request);
            return "redirect:/event/" + eventName + "/";
        }
        if (!ticketReservation.get().getValidity().after(new Date())) {
            bindingResult.reject(ErrorsCode.STEP_2_ORDER_EXPIRED);
        }
        final TicketReservationManager.TotalPrice reservationCost = ticketReservationManager.totalReservationCostWithVAT(reservationId);
        paymentForm.validate(bindingResult, reservationCost, event);
        if (bindingResult.hasErrors()) {
            SessionUtil.addToFlash(bindingResult, redirectAttributes);
            return redirectReservation(ticketReservation, eventName, reservationId);
        }

        //handle paypal redirect!
        if(paymentForm.getPaymentMethod() == PaymentProxy.PAYPAL && !paymentForm.hasPaypalTokens()) {
            OrderSummary orderSummary = ticketReservationManager.orderSummaryForReservationId(reservationId, event, locale);
            try {
                String checkoutUrl = paypalManager.createCheckoutRequest(event, reservationId, orderSummary, paymentForm.getFullName(), paymentForm.getEmail(), paymentForm.getBillingAddress(), locale);
                return "redirect:" + checkoutUrl;
            } catch (Exception e) {
                bindingResult.reject(ErrorsCode.STEP_2_PAYMENT_REQUEST_CREATION);
                return redirectReservation(ticketReservation, eventName, reservationId);
            }
        }
        //


        boolean directTicketAssignment = Optional.ofNullable(paymentForm.getExpressCheckoutRequested()).map(b -> Boolean.logicalAnd(b, isExpressCheckoutEnabled(event, ticketReservationManager.orderSummaryForReservationId(reservationId, event, locale)))).orElse(false);
        final PaymentResult status = ticketReservationManager.confirm(paymentForm.getToken(), paymentForm.getPaypalPayerID(), event, reservationId, paymentForm.getEmail(),
                paymentForm.getFullName(), locale, paymentForm.getBillingAddress(), reservationCost, SessionUtil.retrieveSpecialPriceSessionId(request),
                Optional.ofNullable(paymentForm.getPaymentMethod()), directTicketAssignment);

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

        if(directTicketAssignment) {
            ticketHelper.directTicketAssignment(eventName, reservationId, paymentForm.getEmail(), paymentForm.getFullName(), locale.getLanguage(), Optional.of(bindingResult), request, model);
        }

        return "redirect:/event/" + eventName + "/reservation/" + reservationId + "/success";
    }

    @RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/re-send-email", method = RequestMethod.POST)
    public String reSendReservationConfirmationEmail(@PathVariable("eventName") String eventName,
            @PathVariable("reservationId") String reservationId, HttpServletRequest request) {

        Optional<Event> event = eventRepository.findOptionalByShortName(eventName);
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


    @RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/ticket/{ticketIdentifier}/assign", method = RequestMethod.POST)
    public String assignTicketToPerson(@PathVariable("eventName") String eventName,
                                       @PathVariable("reservationId") String reservationId,
                                       @PathVariable("ticketIdentifier") String ticketIdentifier,
                                       UpdateTicketOwnerForm updateTicketOwner,
                                       BindingResult bindingResult,
                                       HttpServletRequest request,
                                       Model model) throws Exception {

        Optional<Triple<ValidationResult, Event, Ticket>> result = ticketHelper.assignTicket(eventName, reservationId, ticketIdentifier, updateTicketOwner, Optional.of(bindingResult), request, model);
        return result.map(t -> "redirect:/event/"+t.getMiddle().getShortName()+"/reservation/"+t.getRight().getTicketsReservationId()+"/success").orElse("redirect:/");
    }

    private void sendReservationCompleteEmail(HttpServletRequest request, Event event, TicketReservation reservation) {
        Locale locale = RequestContextUtils.getLocale(request);
        ticketReservationManager.sendConfirmationEmail(event, reservation, locale);
    }

    private void sendReservationCompleteEmailToOrganizer(HttpServletRequest request, Event event, TicketReservation reservation) {
        Organization organization = organizationRepository.getById(event.getOrganizationId());
        notificationManager.sendSimpleEmail(event, organization.getEmail(), "Reservation complete " + reservation.getId(), () -> {
            return  templateManager.renderClassPathResource("/alfio/templates/confirmation-email-for-organizer-txt.ms", ticketReservationManager.prepareModelForReservationEmail(event, reservation),
                RequestContextUtils.getLocale(request), TemplateOutput.TEXT);
        });
    }

    private boolean isExpressCheckoutEnabled(Event event, OrderSummary orderSummary) {
        return orderSummary.getTicketAmount() == 1 && ticketFieldRepository.countRequiredAdditionalFieldsForEvent(event.getId()) == 0;
    }
}
