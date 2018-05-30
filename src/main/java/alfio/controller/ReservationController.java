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
import alfio.manager.support.PaymentResult;
import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.model.TicketReservation.TicketReservationStatus;
import alfio.model.result.ValidationResult;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Organization;
import alfio.repository.EventRepository;
import alfio.repository.TicketFieldRepository;
import alfio.repository.TicketRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.ErrorsCode;
import alfio.util.TemplateManager;
import alfio.util.TemplateResource;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
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
import java.util.function.Function;
import java.util.stream.Collectors;

import static alfio.model.system.Configuration.getSystemConfiguration;
import static alfio.model.system.ConfigurationKeys.*;
import static java.util.stream.Collectors.toList;

@Controller
@Log4j2
@AllArgsConstructor
public class ReservationController {

    private final EventRepository eventRepository;
    private final EventManager eventManager;
    private final TicketReservationManager ticketReservationManager;
    private final OrganizationRepository organizationRepository;

    private final TemplateManager templateManager;
    private final MessageSource messageSource;
    private final ConfigurationManager configurationManager;
    private final NotificationManager notificationManager;
    private final TicketHelper ticketHelper;
    private final TicketFieldRepository ticketFieldRepository;
    private final PaymentManager paymentManager;
    private final TicketRepository ticketRepository;
    private final EuVatChecker vatChecker;
    private final MollieManager mollieManager;
    private final RecaptchaService recaptchaService;

    @RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/book", method = RequestMethod.GET)
    public String showPaymentPage(@PathVariable("eventName") String eventName,
                                  @PathVariable("reservationId") String reservationId,
                                  //paypal related parameters
                                  @RequestParam(value = "paymentId", required = false) String paypalPaymentId,
                                  @RequestParam(value = "PayerID", required = false) String paypalPayerID,
                                  @RequestParam(value = "paypal-success", required = false) Boolean isPaypalSuccess,
                                  @RequestParam(value = "paypal-error", required = false) Boolean isPaypalError,
                                  @RequestParam(value = "fullName", required = false) String fullName,
                                  @RequestParam(value = "firstName", required = false) String firstName,
                                  @RequestParam(value = "lastName", required = false) String lastName,
                                  @RequestParam(value = "email", required = false) String email,
                                  @RequestParam(value = "billingAddress", required = false) String billingAddress,
                                  @RequestParam(value = "customerReference", required = false) String customerReference,
                                  @RequestParam(value = "hmac", required = false) String hmac,
                                  @RequestParam(value = "postponeAssignment", required = false) Boolean postponeAssignment,
                                  @RequestParam(value = "invoiceRequested", required = false) Boolean invoiceRequested,
                                  Model model,
                                  Locale locale) {

        return eventRepository.findOptionalByShortName(eventName)
            .map(event -> ticketReservationManager.findById(reservationId)
                .map(reservation -> {

                    if (reservation.getStatus() != TicketReservationStatus.PENDING) {
                        return redirectReservation(Optional.of(reservation), eventName, reservationId);
                    }

                    Function<ConfigurationKeys, Configuration.ConfigurationPathKey> partialConfig = Configuration.from(event.getOrganizationId(), event.getId());

                    Configuration.ConfigurationPathKey forceAssignmentKey = partialConfig.apply(FORCE_TICKET_OWNER_ASSIGNMENT_AT_RESERVATION);
                    boolean forceAssignment = configurationManager.getBooleanConfigValue(forceAssignmentKey, false);

                    List<Ticket> ticketsInReservation = ticketReservationManager.findTicketsInReservation(reservationId);
                    if (Boolean.TRUE.equals(isPaypalSuccess) && paypalPayerID != null && paypalPaymentId != null) {
                        model.addAttribute("paypalPaymentId", paypalPaymentId)
                            .addAttribute("paypalPayerID", paypalPayerID)
                            .addAttribute("paypalCheckoutConfirmation", true)
                            .addAttribute("fullName", fullName)
                            .addAttribute("firstName", firstName)
                            .addAttribute("lastName", lastName)
                            .addAttribute("email", email)
                            .addAttribute("billingAddress", billingAddress)
                            .addAttribute("hmac", hmac)
                            .addAttribute("postponeAssignment", Boolean.TRUE.equals(postponeAssignment))
                            .addAttribute("invoiceRequested", Boolean.TRUE.equals(invoiceRequested))
                            .addAttribute("customerReference", customerReference)
                            .addAttribute("showPostpone", !forceAssignment && Boolean.TRUE.equals(postponeAssignment));
                    } else {
                        model.addAttribute("paypalCheckoutConfirmation", false)
                             .addAttribute("postponeAssignment", false)
                             .addAttribute("showPostpone", !forceAssignment && ticketsInReservation.size() > 1);
                    }

                    try {
                        model.addAttribute("delayForOfflinePayment", Math.max(1, TicketReservationManager.getOfflinePaymentWaitingPeriod(event, configurationManager)));
                    } catch (TicketReservationManager.OfflinePaymentException e) {
                        if(event.getAllowedPaymentProxies().contains(PaymentProxy.OFFLINE)) {
                            log.error("Already started event {} has been found with OFFLINE payment enabled" , event.getDisplayName() , e);
                        }
                        model.addAttribute("delayForOfflinePayment", 0);
                    }

                    OrderSummary orderSummary = ticketReservationManager.orderSummaryForReservationId(reservationId, event, locale);
                    List<PaymentProxy> activePaymentMethods = paymentManager.getPaymentMethods(event.getOrganizationId())
                        .stream()
                        .filter(p -> TicketReservationManager.isValidPaymentMethod(p, event, configurationManager))
                        .map(PaymentManager.PaymentMethod::getPaymentProxy)
                        .collect(toList());

                    if(orderSummary.getFree() || activePaymentMethods.stream().anyMatch(p -> p == PaymentProxy.OFFLINE || p == PaymentProxy.ON_SITE)) {
                        boolean captchaForOfflinePaymentEnabled = configurationManager.isRecaptchaForOfflinePaymentEnabled(event);
                        model.addAttribute("captchaRequestedForOffline", captchaForOfflinePaymentEnabled)
                            .addAttribute("recaptchaApiKey", configurationManager.getStringConfigValue(getSystemConfiguration(RECAPTCHA_API_KEY), null))
                            .addAttribute("captchaRequestedFreeOfCharge", orderSummary.getFree() && captchaForOfflinePaymentEnabled);
                    }

                    boolean invoiceAllowed = configurationManager.hasAllConfigurationsForInvoice(event) || vatChecker.isVatCheckingEnabledFor(event.getOrganizationId());
                    boolean onlyInvoice = invoiceAllowed && configurationManager.getBooleanConfigValue(partialConfig.apply(ConfigurationKeys.GENERATE_ONLY_INVOICE), false);
                    PaymentForm paymentForm = PaymentForm.fromExistingReservation(reservation);
                    model.addAttribute("multiplePaymentMethods" , activePaymentMethods.size() > 1 )
                        .addAttribute("orderSummary", orderSummary)
                        .addAttribute("reservationId", reservationId)
                        .addAttribute("reservation", reservation)
                        .addAttribute("pageTitle", "reservation-page.header.title")
                        .addAttribute("event", event)
                        .addAttribute("activePaymentMethods", activePaymentMethods)
                        .addAttribute("expressCheckoutEnabled", isExpressCheckoutEnabled(event, orderSummary))
                        .addAttribute("useFirstAndLastName", event.mustUseFirstAndLastName())
                        .addAttribute("countries", TicketHelper.getLocalizedCountries(locale))
                        .addAttribute("countriesForVat", TicketHelper.getLocalizedCountriesForVat(locale))
                        .addAttribute("euCountriesForVat", TicketHelper.getLocalizedEUCountriesForVat(locale, configurationManager.getRequiredValue(getSystemConfiguration(ConfigurationKeys.EU_COUNTRIES_LIST))))
                        .addAttribute("euVatCheckingEnabled", vatChecker.isVatCheckingEnabledFor(event.getOrganizationId()))
                        .addAttribute("invoiceIsAllowed", invoiceAllowed)
                        .addAttribute("onlyInvoice", onlyInvoice)
                        .addAttribute("vatNrIsLinked", orderSummary.isVatExempt() || paymentForm.getHasVatCountryCode())
                        .addAttribute("attendeeAutocompleteEnabled", ticketsInReservation.size() == 1 && configurationManager.getBooleanConfigValue(partialConfig.apply(ENABLE_ATTENDEE_AUTOCOMPLETE), true))
                        .addAttribute("billingAddressLabel", invoiceAllowed ? "reservation-page.billing-address" : "reservation-page.receipt-address")
                        .addAttribute("customerReferenceEnabled", configurationManager.getBooleanConfigValue(partialConfig.apply(ENABLE_CUSTOMER_REFERENCE), false));

                    boolean includeStripe = !orderSummary.getFree() && activePaymentMethods.contains(PaymentProxy.STRIPE);
                    model.addAttribute("includeStripe", includeStripe);
                    if (includeStripe) {
                        model.addAttribute("stripe_p_key", paymentManager.getStripePublicKey(event));
                    }
                    Map<String, Object> modelMap = model.asMap();
                    modelMap.putIfAbsent("paymentForm", paymentForm);
                    modelMap.putIfAbsent("hasErrors", false);

                    boolean hasPaidSupplement = ticketReservationManager.hasPaidSupplements(reservationId);
                    model.addAttribute(
                        "ticketsByCategory",
                        ticketsInReservation.stream().collect(Collectors.groupingBy(Ticket::getCategoryId)).entrySet().stream()
                            .map((e) -> {
                                TicketCategory category = eventManager.getTicketCategoryById(e.getKey(), event.getId());
                                List<TicketDecorator> decorators = TicketDecorator.decorate(e.getValue(),
                                    !hasPaidSupplement && configurationManager.getBooleanConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), category.getId(), ALLOW_FREE_TICKETS_CANCELLATION), false),
                                    eventManager.checkTicketCancellationPrerequisites(),
                                    ticket -> ticketHelper.findTicketFieldConfigurationAndValue(event.getId(), ticket, locale),
                                    true, (t) -> "tickets['"+t.getUuid()+"'].");
                                return Pair.of(category, decorators);
                            })
                            .collect(toList()));
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
                    boolean hasPaidSupplement = ticketReservationManager.hasPaidSupplements(reservationId);
                    model.addAttribute(
                        "ticketsByCategory",
                        tickets.stream().collect(Collectors.groupingBy(Ticket::getCategoryId)).entrySet().stream()
                            .map((e) -> {
                                TicketCategory category = eventManager.getTicketCategoryById(e.getKey(), ev.getId());
                                List<TicketDecorator> decorators = TicketDecorator.decorate(e.getValue(),
                                    !hasPaidSupplement && configurationManager.getBooleanConfigValue(Configuration.from(ev.getOrganizationId(), ev.getId(), category.getId(), ALLOW_FREE_TICKETS_CANCELLATION), false),
                                    eventManager.checkTicketCancellationPrerequisites(),
                                    ticket -> ticketHelper.findTicketFieldConfigurationAndValue(ev.getId(), ticket, locale),
                                    tickets.size() == 1, TicketDecorator.EMPTY_PREFIX_GENERATOR);
                                return Pair.of(category, decorators);
                            })
                            .collect(toList()));
                    boolean ticketsAllAssigned = tickets.stream().allMatch(Ticket::getAssigned);
                    model.addAttribute("ticketsAreAllAssigned", ticketsAllAssigned);
                    model.addAttribute("collapseEnabled", tickets.size() > 1 && !ticketsAllAssigned);
                    model.addAttribute("additionalServicesOnly", tickets.isEmpty() && !additionalServices.isEmpty());
                    model.addAttribute("additionalServices", additionalServices);
                    model.addAttribute("countries", TicketHelper.getLocalizedCountries(locale));
                    model.addAttribute("pageTitle", "reservation-page-complete.header.title");
                    model.addAttribute("event", ev);
                    model.addAttribute("useFirstAndLastName", ev.mustUseFirstAndLastName());
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


            Optional<String> maybeAccountOwner = configurationManager.getStringConfigValue(Configuration.from(ev.getOrganizationId(), ev.getId(), BANK_ACCOUNT_OWNER));
            model.addAttribute("hasBankAccountOwnerSet", maybeAccountOwner.isPresent());
            model.addAttribute("bankAccountOwner", Arrays.asList(maybeAccountOwner.orElse("").split("\n")));

            model.addAttribute("expires", ZonedDateTime.ofInstant(ticketReservation.getValidity().toInstant(), ev.getZoneId()));
            model.addAttribute("event", ev);
            return "/event/reservation-waiting-for-payment";
        }

        return redirectReservation(reservation, eventName, reservationId);
    }

    @RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/processing-payment", method = RequestMethod.GET)
    public String showProcessingPayment(@PathVariable("eventName") String eventName,
                                        @PathVariable("reservationId") String reservationId,
                                        Model model, Locale locale) {

        //FIXME
        return "/event/reservation-processing-payment";
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
            case EXTERNAL_PROCESSING_PAYMENT:
                return baseUrl + "/processing-payment";
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

        final TotalPrice reservationCost = ticketReservationManager.totalReservationCostWithVAT(reservationId);
        if(isCaptchaInvalid(reservationCost.getPriceWithVAT(), paymentForm.getPaymentMethod(), request, event)) {
            log.debug("captcha validation failed.");
            bindingResult.reject(ErrorsCode.STEP_2_CAPTCHA_VALIDATION_FAILED);
        }

        Configuration.ConfigurationPathKey forceAssignmentKey = Configuration.from(event.getOrganizationId(), event.getId(), ConfigurationKeys.FORCE_TICKET_OWNER_ASSIGNMENT_AT_RESERVATION);
        boolean forceAssignment = configurationManager.getBooleanConfigValue(forceAssignmentKey, false);
        if(forceAssignment) {
            paymentForm.setPostponeAssignment(false);
        }

        Configuration.ConfigurationPathKey invoiceOnlyKey = Configuration.from(event.getOrganizationId(), event.getId(), ConfigurationKeys.GENERATE_ONLY_INVOICE);
        boolean invoiceOnly = configurationManager.getBooleanConfigValue(invoiceOnlyKey, false);
        if(invoiceOnly && reservationCost.getPriceWithVAT() > 0) {
            paymentForm.setInvoiceRequested(true);
        }

        if(paymentForm.getPaymentMethod() != PaymentProxy.PAYPAL || !paymentForm.hasPaypalTokens()) {
            if(!paymentForm.isPostponeAssignment() && !ticketRepository.checkTicketUUIDs(reservationId, paymentForm.getTickets().keySet())) {
                bindingResult.reject(ErrorsCode.STEP_2_MISSING_ATTENDEE_DATA);
            }
            paymentForm.validate(bindingResult, reservationCost, event, ticketFieldRepository.findAdditionalFieldsForEvent(event.getId()));
            if (bindingResult.hasErrors()) {
                SessionUtil.addToFlash(bindingResult, redirectAttributes);
                return redirectReservation(ticketReservation, eventName, reservationId);
            }
        }

        CustomerName customerName = new CustomerName(paymentForm.getFullName(), paymentForm.getFirstName(), paymentForm.getLastName(), event);

        //handle paypal redirect!
        if(paymentForm.getPaymentMethod() == PaymentProxy.PAYPAL && !paymentForm.hasPaypalTokens()) {
            OrderSummary orderSummary = ticketReservationManager.orderSummaryForReservationId(reservationId, event, locale);
            try {
                String checkoutUrl = paymentManager.createPayPalCheckoutRequest(event, reservationId, orderSummary, customerName,
                    paymentForm.getEmail(), paymentForm.getBillingAddress(), paymentForm.getCustomerReference(), locale, paymentForm.isPostponeAssignment(),
                    paymentForm.isInvoiceRequested());
                assignTickets(eventName, reservationId, paymentForm, bindingResult, request, true);
                return "redirect:" + checkoutUrl;
            } catch (Exception e) {
                bindingResult.reject(ErrorsCode.STEP_2_PAYMENT_REQUEST_CREATION);
                SessionUtil.addToFlash(bindingResult, redirectAttributes);
                return redirectReservation(ticketReservation, eventName, reservationId);
            }
        }

        //handle mollie redirect
        if(paymentForm.getPaymentMethod() == PaymentProxy.MOLLIE) {
            OrderSummary orderSummary = ticketReservationManager.orderSummaryForReservationId(reservationId, event, locale);
            try {
                String checkoutUrl = mollieManager.createCheckoutRequest(event, reservationId, orderSummary, customerName,
                    paymentForm.getEmail(), paymentForm.getBillingAddress(), locale,
                    paymentForm.isInvoiceRequested(),
                    paymentForm.getVatCountryCode(),
                    paymentForm.getVatNr(),
                    ticketReservation.get().getVatStatus());
                assignTickets(eventName, reservationId, paymentForm, bindingResult, request, true);
                return "redirect:" + checkoutUrl;
            } catch (Exception e) {
                bindingResult.reject(ErrorsCode.STEP_2_PAYMENT_REQUEST_CREATION);
                SessionUtil.addToFlash(bindingResult, redirectAttributes);
                return redirectReservation(ticketReservation, eventName, reservationId);
            }
        }
        //

        final PaymentResult status = ticketReservationManager.confirm(paymentForm.getToken(), paymentForm.getPaypalPayerID(), event, reservationId, paymentForm.getEmail(),
            customerName, locale, paymentForm.getBillingAddress(), paymentForm.getCustomerReference(), reservationCost, SessionUtil.retrieveSpecialPriceSessionId(request),
                Optional.ofNullable(paymentForm.getPaymentMethod()), paymentForm.isInvoiceRequested(), paymentForm.getVatCountryCode(),
                paymentForm.getVatNr(), ticketReservation.get().getVatStatus());

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

        if(paymentForm.getPaymentMethod() != PaymentProxy.PAYPAL) {
            assignTickets(eventName, reservationId, paymentForm, bindingResult, request, paymentForm.getPaymentMethod() == PaymentProxy.OFFLINE);
        }

        return "redirect:/event/" + eventName + "/reservation/" + reservationId + "/success";
    }

    private boolean isCaptchaInvalid(int cost, PaymentProxy paymentMethod, HttpServletRequest request, Event event) {
        return (cost == 0 || paymentMethod == PaymentProxy.OFFLINE || paymentMethod == PaymentProxy.ON_SITE)
                && configurationManager.isRecaptchaForOfflinePaymentEnabled(event)
                && !recaptchaService.checkRecaptcha(request);
    }

    private void assignTickets(String eventName, String reservationId, PaymentForm paymentForm, BindingResult bindingResult, HttpServletRequest request, boolean preAssign) {
        if(!paymentForm.isPostponeAssignment()) {
            paymentForm.getTickets().forEach((ticketId, owner) -> {
                if (preAssign) {
                    ticketHelper.preAssignTicket(eventName, reservationId, ticketId, owner, Optional.of(bindingResult), request, (tr) -> {
                    }, Optional.empty());
                } else {
                    ticketHelper.assignTicket(eventName, ticketId, owner, Optional.of(bindingResult), request, (tr) -> {
                    }, Optional.empty());
                }
            });
        }
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
        List<String> cc = notificationManager.getCCForEventOrganizer(event);

        notificationManager.sendSimpleEmail(event, organization.getEmail(), cc, "Reservation complete " + reservation.getId(), () ->
            templateManager.renderTemplate(event, TemplateResource.CONFIRMATION_EMAIL_FOR_ORGANIZER, ticketReservationManager.prepareModelForReservationEmail(event, reservation),
                RequestContextUtils.getLocale(request))
        );
    }

    private boolean isExpressCheckoutEnabled(Event event, OrderSummary orderSummary) {
        return orderSummary.getTicketAmount() == 1 && ticketFieldRepository.countRequiredAdditionalFieldsForEvent(event.getId()) == 0;
    }
}
