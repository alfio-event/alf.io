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
import alfio.controller.form.ContactAndTicketsForm;
import alfio.controller.form.PaymentForm;
import alfio.controller.form.UpdateTicketOwnerForm;
import alfio.controller.support.SessionUtil;
import alfio.controller.support.TicketDecorator;
import alfio.manager.*;
import alfio.manager.payment.PaymentSpecification;
import alfio.manager.payment.StripeCreditCardManager;
import alfio.manager.support.PaymentResult;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.system.ReservationPriceCalculator;
import alfio.model.*;
import alfio.model.TicketReservation.TicketReservationStatus;
import alfio.model.TicketReservationInvoicingAdditionalInfo.ItalianEInvoicing;
import alfio.model.result.ValidationResult;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentContext;
import alfio.model.transaction.PaymentProxy;
import alfio.model.transaction.PaymentToken;
import alfio.repository.EventRepository;
import alfio.repository.TicketFieldRepository;
import alfio.repository.TicketReservationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.ErrorsCode;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static alfio.model.PriceContainer.VatStatus.*;
import static alfio.model.system.Configuration.getSystemConfiguration;
import static alfio.model.system.ConfigurationKeys.*;
import static alfio.util.MonetaryUtil.unitToCents;
import static java.util.stream.Collectors.toList;

@Controller
@Log4j2
@AllArgsConstructor
public class ReservationController {

    private final EventRepository eventRepository;
    private final EventManager eventManager;
    private final TicketReservationManager ticketReservationManager;
    private final OrganizationRepository organizationRepository;

    private final MessageSource messageSource;
    private final ConfigurationManager configurationManager;
    private final TicketHelper ticketHelper;
    private final TicketFieldRepository ticketFieldRepository;
    private final PaymentManager paymentManager;
    private final EuVatChecker vatChecker;
    private final RecaptchaService recaptchaService;
    private final TicketReservationRepository ticketReservationRepository;
    private final ExtensionManager extensionManager;

    @RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/book", method = RequestMethod.GET)
    public String showBookingPage(@PathVariable("eventName") String eventName,
                                  @PathVariable("reservationId") String reservationId,
                                  Model model,
                                  Locale locale) {

        return eventRepository.findOptionalByShortName(eventName)
            .map(event -> ticketReservationManager.findById(reservationId)
                .map(reservation -> {

                    if (reservation.getStatus() != TicketReservationStatus.PENDING) {
                        return redirectReservation(Optional.of(reservation), eventName, reservationId);
                    }

                    TicketReservationAdditionalInfo additionalInfo = ticketReservationRepository.getAdditionalInfo(reservationId);
                    if (additionalInfo.hasBeenValidated()) {
                        return "redirect:/event/" + eventName + "/reservation/" + reservationId + "/overview";
                    }

                    Function<ConfigurationKeys, Configuration.ConfigurationPathKey> partialConfig = Configuration.from(event);

                    Configuration.ConfigurationPathKey forceAssignmentKey = partialConfig.apply(FORCE_TICKET_OWNER_ASSIGNMENT_AT_RESERVATION);
                    boolean forceAssignment = configurationManager.getBooleanConfigValue(forceAssignmentKey, false);

                    List<Ticket> ticketsInReservation = ticketReservationManager.findTicketsInReservation(reservationId);

                    model.addAttribute("postponeAssignment", false)
                         .addAttribute("showPostpone", !forceAssignment && ticketsInReservation.size() > 1 && !ticketReservationManager.containsCategoriesLinkedToGroups(reservationId, event.getId()));


                    OrderSummary orderSummary = ticketReservationManager.orderSummaryForReservationId(reservationId, event);

                    //FIXME recaptcha for free orders

                    boolean invoiceAllowed = configurationManager.hasAllConfigurationsForInvoice(event) || vatChecker.isReverseChargeEnabledFor(event.getOrganizationId());
                    boolean onlyInvoice = invoiceAllowed && configurationManager.isInvoiceOnly(event);


                    ContactAndTicketsForm contactAndTicketsForm = ContactAndTicketsForm.fromExistingReservation(reservation, additionalInfo);
                    model.addAttribute("orderSummary", orderSummary)
                        .addAttribute("reservationId", reservationId)
                        .addAttribute("reservation", reservation)
                        .addAttribute("pageTitle", "reservation-page.header.title")
                        .addAttribute("event", event)
                        .addAttribute("expressCheckoutEnabled", isExpressCheckoutEnabled(event, orderSummary))
                        .addAttribute("useFirstAndLastName", event.mustUseFirstAndLastName())
                        .addAttribute("countries", TicketHelper.getLocalizedCountries(locale))
                        .addAttribute("countriesForVat", TicketHelper.getLocalizedCountriesForVat(locale))
                        .addAttribute("euCountriesForVat", TicketHelper.getLocalizedEUCountriesForVat(locale, configurationManager.getRequiredValue(getSystemConfiguration(ConfigurationKeys.EU_COUNTRIES_LIST))))
                        .addAttribute("euVatCheckingEnabled", vatChecker.isReverseChargeEnabledFor(event.getOrganizationId()))
                        .addAttribute("invoiceIsAllowed", !orderSummary.getFree() && invoiceAllowed)
                        .addAttribute("onlyInvoice", !orderSummary.getFree() && onlyInvoice)
                        .addAttribute("vatNrIsLinked", orderSummary.isVatExempt() || contactAndTicketsForm.getHasVatCountryCode())
                        .addAttribute("attendeeAutocompleteEnabled", ticketsInReservation.size() == 1 && configurationManager.getBooleanConfigValue(partialConfig.apply(ENABLE_ATTENDEE_AUTOCOMPLETE), true))
                        .addAttribute("billingAddressLabel", invoiceAllowed ? "reservation-page.billing-address" : "reservation-page.receipt-address")
                        .addAttribute("customerReferenceEnabled", configurationManager.getBooleanConfigValue(partialConfig.apply(ENABLE_CUSTOMER_REFERENCE), false))
                        .addAttribute("enabledItalyEInvoicing", configurationManager.getBooleanConfigValue(partialConfig.apply(ENABLE_ITALY_E_INVOICING), false))
                        .addAttribute("vatNumberStrictlyRequired", configurationManager.getBooleanConfigValue(partialConfig.apply(VAT_NUMBER_IS_REQUIRED), false));

                    Map<String, Object> modelMap = model.asMap();
                    modelMap.putIfAbsent("paymentForm", contactAndTicketsForm);
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
                                    ticketHelper::findTicketFieldConfigurationAndValue,
                                    true, (t) -> "tickets["+t.getUuid()+"].");
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
                    SessionUtil.cleanupSession(request);
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
                                    ticketHelper::findTicketFieldConfigurationAndValue,
                                    tickets.size() == 1, TicketDecorator.EMPTY_PREFIX_GENERATOR);
                                return Pair.of(category, decorators);
                            })
                            .collect(toList()));
                    boolean ticketsAllAssigned = tickets.stream().allMatch(Ticket::getAssigned);
                    model.addAttribute("ticketsAreAllAssigned", ticketsAllAssigned)
                        .addAttribute("displayTransferInfo", ticketsAllAssigned && configurationManager.getBooleanConfigValue(Configuration.from(ev).apply(ENABLE_TICKET_TRANSFER), true))
                        .addAttribute("collapseEnabled", tickets.size() > 1 && !ticketsAllAssigned)
                        .addAttribute("additionalServicesOnly", tickets.isEmpty() && !additionalServices.isEmpty())
                        .addAttribute("additionalServices", additionalServices)
                        .addAttribute("countries", TicketHelper.getLocalizedCountries(locale))
                        .addAttribute("pageTitle", "reservation-page-complete.header.title")
                        .addAttribute("event", ev)
                        .addAttribute("useFirstAndLastName", ev.mustUseFirstAndLastName())
                        .addAttribute("userCanDownloadReceiptOrInvoice", configurationManager.canGenerateReceiptOrInvoiceToCustomer(ev));

                    model.asMap().putIfAbsent("validationResult", ValidationResult.success());

                    return "/event/reservation-page-complete";
                }).orElseGet(() -> redirectReservation(tr, eventName, reservationId));
        }).orElse("redirect:/");
    }

    @RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/validate-to-overview", method = RequestMethod.POST)
    public String validateToOverview(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId,
                                     ContactAndTicketsForm contactAndTicketsForm, BindingResult bindingResult,
                                     HttpServletRequest request, RedirectAttributes redirectAttributes, Locale locale) {

        Optional<Event> eventOptional = eventRepository.findOptionalByShortName(eventName);
        return redirectIfNotValid(contactAndTicketsForm.isBackFromOverview(), contactAndTicketsForm.shouldCancelReservation(), eventName, reservationId, request, eventOptional)
            .orElseGet(() -> {
                Event event = eventOptional.orElseThrow();


                var reservation = ticketReservationManager.findById(reservationId).orElseThrow();
                final TotalPrice reservationCost = ticketReservationManager.totalReservationCostWithVAT(reservation.withVatStatus(event.getVatStatus()));
                Configuration.ConfigurationPathKey forceAssignmentKey = Configuration.from(event, ConfigurationKeys.FORCE_TICKET_OWNER_ASSIGNMENT_AT_RESERVATION);
                boolean forceAssignment = configurationManager.getBooleanConfigValue(forceAssignmentKey, false);

                if(forceAssignment || ticketReservationManager.containsCategoriesLinkedToGroups(reservationId, event.getId())) {
                    contactAndTicketsForm.setPostponeAssignment(false);
                }

                boolean invoiceOnly = configurationManager.isInvoiceOnly(event);

                if(invoiceOnly && reservationCost.getPriceWithVAT() > 0) {
                    //override, that's why we save it
                    contactAndTicketsForm.setInvoiceRequested(true);
                }

                CustomerName customerName = new CustomerName(contactAndTicketsForm.getFullName(), contactAndTicketsForm.getFirstName(), contactAndTicketsForm.getLastName(), event.mustUseFirstAndLastName(), false);


                ticketReservationRepository.resetVat(reservationId, contactAndTicketsForm.isInvoiceRequested(), event.getVatStatus(),
                    reservation.getSrcPriceCts(), reservationCost.getPriceWithVAT(), reservationCost.getVAT(), Math.abs(reservationCost.getDiscount()), reservation.getCurrencyCode());
                if(contactAndTicketsForm.isBusiness()) {
                    checkAndApplyVATRules(eventName, reservationId, contactAndTicketsForm, bindingResult, event);
                }

                //persist data
                ticketReservationManager.updateReservation(reservationId, customerName, contactAndTicketsForm.getEmail(),
                    contactAndTicketsForm.getBillingAddressCompany(), contactAndTicketsForm.getBillingAddressLine1(), contactAndTicketsForm.getBillingAddressLine2(),
                    contactAndTicketsForm.getBillingAddressZip(), contactAndTicketsForm.getBillingAddressCity(), contactAndTicketsForm.getVatCountryCode(),
                    contactAndTicketsForm.getCustomerReference(), contactAndTicketsForm.getVatNr(), contactAndTicketsForm.isInvoiceRequested(),
                    contactAndTicketsForm.getAddCompanyBillingDetails(), contactAndTicketsForm.canSkipVatNrCheck(), false, locale);

                boolean italyEInvoicing = configurationManager.getBooleanConfigValue(Configuration.from(event, ENABLE_ITALY_E_INVOICING), false);

                if(italyEInvoicing) {
                    ticketReservationManager.updateReservationInvoicingAdditionalInformation(reservationId,
                        new TicketReservationInvoicingAdditionalInfo(getItalianInvoicingInfo(contactAndTicketsForm))
                    );
                }

                assignTickets(event.getShortName(), reservationId, contactAndTicketsForm, bindingResult, request, true, true);
                //

                Map<ConfigurationKeys, Boolean> formValidationParameters = Collections.singletonMap(ENABLE_ITALY_E_INVOICING, italyEInvoicing);
                //
                contactAndTicketsForm.validate(bindingResult, event,
                    ticketFieldRepository.findAdditionalFieldsForEvent(event.getId()),
                    new SameCountryValidator(configurationManager, extensionManager, event.getOrganizationId(), event.getId(), reservationId, vatChecker),
                    formValidationParameters);
                //

                if(!bindingResult.hasErrors()) {
                    extensionManager.handleReservationValidation(event, reservation, contactAndTicketsForm, bindingResult);
                }

                if(bindingResult.hasErrors()) {
                    SessionUtil.addToFlash(bindingResult, redirectAttributes);
                    return "redirect:/event/" + eventName + "/reservation/" + reservationId + "/book";
                }
                ticketReservationRepository.updateValidationStatus(reservationId, true);


                return "redirect:/event/" + eventName + "/reservation/" + reservationId + "/overview";
            });

    }

    private ItalianEInvoicing getItalianInvoicingInfo(ContactAndTicketsForm contactAndTicketsForm) {
        if("IT".equalsIgnoreCase(contactAndTicketsForm.getVatCountryCode())) {
            return new ItalianEInvoicing(contactAndTicketsForm.getItalyEInvoicingFiscalCode(),
                contactAndTicketsForm.getItalyEInvoicingReferenceType(),
                contactAndTicketsForm.getItalyEInvoicingReferenceAddresseeCode(),
                contactAndTicketsForm.getItalyEInvoicingReferencePEC());
        }
        return null;
    }

    private void checkAndApplyVATRules(String eventName, String reservationId, ContactAndTicketsForm contactAndTicketsForm, BindingResult bindingResult, Event event) {
        // VAT handling
        String country = contactAndTicketsForm.getVatCountryCode();

        // validate VAT presence if EU mode is enabled
        if(vatChecker.isReverseChargeEnabledFor(event.getOrganizationId()) && isEUCountry(country)) {
            ValidationUtils.rejectIfEmptyOrWhitespace(bindingResult, "vatNr", "error.emptyField");
        }

        try {
            Optional<VatDetail> vatDetail = eventRepository.findOptionalByShortName(eventName)
                .flatMap(e -> ticketReservationRepository.findOptionalReservationById(reservationId).map(r -> Pair.of(e, r)))
                .filter(e -> EnumSet.of(INCLUDED, NOT_INCLUDED).contains(e.getKey().getVatStatus()))
                .filter(e -> vatChecker.isReverseChargeEnabledFor(e.getKey().getOrganizationId()))
                .flatMap(e -> vatChecker.checkVat(contactAndTicketsForm.getVatNr(), country, e.getKey()));


            vatDetail.ifPresent(vatValidation -> {
                if (!vatValidation.isValid()) {
                    bindingResult.rejectValue("vatNr", "error.vat");
                } else {
                    var reservation = ticketReservationManager.findById(reservationId).orElseThrow();
                    PriceContainer.VatStatus vatStatus = determineVatStatus(event.getVatStatus(), vatValidation.isVatExempt());
                    var updatedPrice = ticketReservationManager.totalReservationCostWithVAT(reservation.withVatStatus(vatStatus));// update VatStatus to the new value for calculating the new price
                    var calculator = new ReservationPriceCalculator(reservation.withVatStatus(vatStatus), updatedPrice, ticketReservationManager.findTicketsInReservation(reservationId), event);
                    ticketReservationRepository.updateBillingData(vatStatus, reservation.getSrcPriceCts(),
                        unitToCents(calculator.getFinalPrice()), unitToCents(calculator.getVAT()), unitToCents(calculator.getAppliedDiscount()),
                        reservation.getCurrencyCode(), StringUtils.trimToNull(vatValidation.getVatNr()),
                        country, contactAndTicketsForm.isInvoiceRequested(), reservationId);
                    vatChecker.logSuccessfulValidation(vatValidation, reservationId, event.getId());
                }
            });
        } catch (IllegalStateException ise) {//vat checker failure
            bindingResult.rejectValue("vatNr", "error.vatVIESDown");
        }
    }

    @RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/overview", method = RequestMethod.GET)
    public String showOverview(@PathVariable("eventName") String eventName,
                               @PathVariable("reservationId") String reservationId,
                               Locale locale,
                               Model model,
                               HttpSession session) {

        return eventRepository.findOptionalByShortName(eventName)
            .map(event -> ticketReservationManager.findById(reservationId)
                .map(reservation -> {
                    if (reservation.getStatus() != TicketReservationStatus.PENDING) {
                        return redirectReservation(Optional.of(reservation), eventName, reservationId);
                    }
                    TicketReservationAdditionalInfo additionalInfo = ticketReservationRepository.getAdditionalInfo(reservationId);
                    if (!additionalInfo.hasBeenValidated()) {
                        return "redirect:/event/" + eventName + "/reservation/" + reservationId + "/book";
                    }

                    OrderSummary orderSummary = ticketReservationManager.orderSummaryForReservationId(reservationId, event);

                    List<PaymentProxy> activePaymentMethods;
                    if(session.getAttribute(PaymentManager.PAYMENT_TOKEN) != null) {
                        model.addAttribute("tokenAcquired", true);
                        activePaymentMethods = Collections.singletonList(((PaymentToken)session.getAttribute(PaymentManager.PAYMENT_TOKEN)).getPaymentProvider());
                    } else {
                        activePaymentMethods = paymentManager.getPaymentMethods(event)
                            .stream()
                            .filter(p -> TicketReservationManager.isValidPaymentMethod(p, event, configurationManager))
                            .map(PaymentManager.PaymentMethodDTO::getPaymentProxy)
                            .collect(toList());
                    }

                    model.addAllAttributes(paymentManager.loadModelOptionsFor(activePaymentMethods, event));

                    model.addAttribute("multiplePaymentMethods" , activePaymentMethods.size() > 1 )
                        .addAttribute("activePaymentMethods", activePaymentMethods);

                    model.addAttribute("orderSummary", orderSummary)
                        .addAttribute("reservationId", reservationId)
                        .addAttribute("reservation", reservation)
                        .addAttribute("pageTitle", "reservation-page.header.title")
                        .addAttribute("event", event)
                        .addAttribute("billingDetails", ticketReservationRepository.getBillingDetailsForReservation(reservationId))
                        .addAttribute("displayInvoiceData", !orderSummary.getFree() && reservation.isInvoiceRequested());
                    return "/event/overview";
                }).orElseGet(() -> redirectReservation(Optional.empty(), eventName, reservationId)))
            .orElse("redirect:/");
    }


    @RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/failure", method = RequestMethod.GET)
    public String showFailurePage(@PathVariable("eventName") String eventName,
                                       @PathVariable("reservationId") String reservationId,
                                       @RequestParam(value = "confirmation-email-sent", required = false, defaultValue = "false") boolean confirmationEmailSent,
                                       @RequestParam(value = "ticket-email-sent", required = false, defaultValue = "false") boolean ticketEmailSent,
                                       Model model) {
        Optional<Event> event = eventRepository.findOptionalByShortName(eventName);
        if (event.isEmpty()) {
            return "redirect:/";
        }

        Optional<TicketReservation> reservation = ticketReservationManager.findById(reservationId);
        Optional<TicketReservationStatus> status = reservation.map(TicketReservation::getStatus);

        if(status.isEmpty()) {
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
        if (event.isEmpty()) {
            return "redirect:/";
        }

        return redirectReservation(ticketReservationManager.findById(reservationId), eventName, reservationId);
    }

    @RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/notfound", method = RequestMethod.GET)
    public String showNotFoundPage(@PathVariable("eventName") String eventName,
                                   @PathVariable("reservationId") String reservationId,
                                   Model model) {

        Optional<Event> event = eventRepository.findOptionalByShortName(eventName);
        if (event.isEmpty()) {
            return "redirect:/";
        }

        Optional<TicketReservation> reservation = ticketReservationManager.findById(reservationId);

        if(reservation.isEmpty()) {
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
        if (event.isEmpty()) {
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
            model.addAttribute("paymentReason", ev.getShortName() + " " + ticketReservationManager.getShortReservationID(ev, ticketReservation));
            model.addAttribute("pageTitle", "reservation-page-waiting.header.title");
            model.addAttribute("bankAccount", configurationManager.getStringConfigValue(Configuration.from(ev, BANK_ACCOUNT_NR)).orElse(""));


            Optional<String> maybeAccountOwner = configurationManager.getStringConfigValue(Configuration.from(ev, BANK_ACCOUNT_OWNER));
            model.addAttribute("hasBankAccountOwnerSet", maybeAccountOwner.isPresent());
            model.addAttribute("bankAccountOwner", Arrays.asList(maybeAccountOwner.orElse("").split("\n")));

            model.addAttribute("expires", ZonedDateTime.ofInstant(ticketReservation.getValidity().toInstant(), ev.getZoneId()));
            model.addAttribute("event", ev);
            model.addAttribute("userCanDownloadReceiptOrInvoice", configurationManager.canGenerateReceiptOrInvoiceToCustomer(ev));
            return "/event/reservation-waiting-for-payment";
        }

        return redirectReservation(reservation, eventName, reservationId);
    }

    @RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/processing-payment", method = RequestMethod.GET)
    public String showProcessingPayment(@PathVariable("eventName") String eventName,
                                        @PathVariable("reservationId") String reservationId,
                                        Model model, Locale locale) {

        Optional<Event> event = eventRepository.findOptionalByShortName(eventName);
        if (event.isEmpty()) {
            return "redirect:/";
        }

        Optional<TicketReservation> reservation = ticketReservationManager.findById(reservationId);
        TicketReservationStatus status = reservation.map(TicketReservation::getStatus).orElse(TicketReservationStatus.PENDING);
        if(reservation.isPresent() && (status == TicketReservationStatus.EXTERNAL_PROCESSING_PAYMENT || status == TicketReservationStatus.WAITING_EXTERNAL_CONFIRMATION)) {
            Event ev = event.get();
            TicketReservation ticketReservation = reservation.get();
            OrderSummary orderSummary = ticketReservationManager.orderSummaryForReservationId(reservationId, ev);

            model.addAttribute("orderSummary", orderSummary)
                .addAttribute("reservationId", reservationId)
                .addAttribute("reservation", ticketReservation)
                .addAttribute("pageTitle", "reservation-page.header.title")
                .addAttribute("paymentMethod", paymentManager.getPaymentMethodForReservation(ticketReservation))
                .addAttribute("event", ev);

            return "/event/reservation-processing-payment";
        }

        return redirectReservation(reservation, eventName, reservationId);
    }


    public String redirectReservation(Optional<TicketReservation> ticketReservation, String eventName, String reservationId) {
        String baseUrl = "redirect:/event/" + eventName + "/reservation/" + reservationId;
        if(ticketReservation.isEmpty()) {
            return baseUrl + "/notfound";
        }
        TicketReservation reservation = ticketReservation.get();

        switch(reservation.getStatus()) {
            case PENDING:
                TicketReservationAdditionalInfo additionalInfo = ticketReservationRepository.getAdditionalInfo(reservationId);
                return additionalInfo.hasBeenValidated() ? baseUrl + "/overview" : baseUrl + "/book";
            case COMPLETE:
                return baseUrl + "/success";
            case OFFLINE_PAYMENT:
                return baseUrl + "/waitingPayment";
            case EXTERNAL_PROCESSING_PAYMENT:
            case WAITING_EXTERNAL_CONFIRMATION:
                return baseUrl + "/processing-payment";
            case IN_PAYMENT:
            case STUCK:
                return baseUrl + "/failure";
        }

        return "redirect:/";
    }


    @RequestMapping(value = "/event/{eventName}/reservation/{reservationId}", method = RequestMethod.POST)
    public String handleReservation(@PathVariable("eventName") String eventName,
                                    @PathVariable("reservationId") String reservationId,
                                    PaymentForm paymentForm,
                                    BindingResult bindingResult,
                                    Model model,
                                    HttpServletRequest request,
                                    Locale locale,
                                    RedirectAttributes redirectAttributes,
                                    HttpSession session) {

        Optional<Event> eventOptional = eventRepository.findOptionalByShortName(eventName);

        return redirectIfNotValid(paymentForm.isBackFromOverview(), paymentForm.shouldCancelReservation(), eventName, reservationId, request, eventOptional)
            .orElseGet(() -> {
                Event event = eventOptional.orElseThrow(IllegalStateException::new);
                Optional<TicketReservation> optionalReservation = ticketReservationManager.findById(reservationId);
                if (optionalReservation.isEmpty()) {
                    return redirectReservation(optionalReservation, eventName, reservationId);
                }
                if (paymentForm.shouldCancelReservation()) {
                    ticketReservationManager.cancelPendingReservation(reservationId, false, null);
                    SessionUtil.cleanupSession(request);
                    return "redirect:/event/" + eventName + "/";
                }
                if (!optionalReservation.get().getValidity().after(new Date())) {
                    bindingResult.reject(ErrorsCode.STEP_2_ORDER_EXPIRED);
                }

                final TicketReservation ticketReservation = optionalReservation.get();

                final TotalPrice reservationCost = ticketReservationManager.totalReservationCostWithVAT(reservationId);

                paymentForm.validate(bindingResult, event, reservationCost);
                if (bindingResult.hasErrors()) {
                    SessionUtil.addToFlash(bindingResult, redirectAttributes);
                    return redirectReservation(optionalReservation, eventName, reservationId);
                }

                if(isCaptchaInvalid(reservationCost.getPriceWithVAT(), paymentForm.getPaymentMethod(), request, event)) {
                    log.debug("captcha validation failed.");
                    bindingResult.reject(ErrorsCode.STEP_2_CAPTCHA_VALIDATION_FAILED);
                }

                if(!bindingResult.hasErrors()) {
                    extensionManager.handleReservationValidation(event, ticketReservation, paymentForm, bindingResult);
                }

                if (bindingResult.hasErrors()) {
                    SessionUtil.addToFlash(bindingResult, redirectAttributes);
                    return redirectReservation(Optional.of(ticketReservation), eventName, reservationId);
                }

                CustomerName customerName = new CustomerName(ticketReservation.getFullName(), ticketReservation.getFirstName(), ticketReservation.getLastName(), event.mustUseFirstAndLastName());

                OrderSummary orderSummary = ticketReservationManager.orderSummaryForReservationId(reservationId, event);

                PaymentToken paymentToken = (PaymentToken) session.getAttribute(PaymentManager.PAYMENT_TOKEN);
                if(paymentToken == null && StringUtils.isNotEmpty(paymentForm.getGatewayToken())) {
                    paymentToken = paymentManager.buildPaymentToken(paymentForm.getGatewayToken(), paymentForm.getPaymentMethod(), new PaymentContext(event, reservationId));
                }
                PaymentSpecification spec = new PaymentSpecification(reservationId, paymentToken, reservationCost.getPriceWithVAT(),
                    event, ticketReservation.getEmail(), customerName, ticketReservation.getBillingAddress(), ticketReservation.getCustomerReference(),
                    locale, ticketReservation.isInvoiceRequested(), !ticketReservation.isDirectAssignmentRequested(),
                    orderSummary, ticketReservation.getVatCountryCode(), ticketReservation.getVatNr(), ticketReservation.getVatStatus(),
                    Boolean.TRUE.equals(paymentForm.getTermAndConditionsAccepted()), Boolean.TRUE.equals(paymentForm.getPrivacyPolicyAccepted()));

                final PaymentResult status = ticketReservationManager.performPayment(spec, reservationCost, SessionUtil.retrieveSpecialPriceSessionId(request),
                        Optional.ofNullable(paymentForm.getPaymentMethod()));

                //
                model.addAttribute("paymentResultStatus", status);
                //

                if (status.isRedirect()) {
                    return "redirect:" + status.getRedirectUrl();
                }

                if(!status.isSuccessful()) {
                    String errorMessageCode = status.getErrorCode().orElse(StripeCreditCardManager.STRIPE_UNEXPECTED);
                    MessageSourceResolvable message = new DefaultMessageSourceResolvable(new String[]{errorMessageCode, StripeCreditCardManager.STRIPE_UNEXPECTED});
                    bindingResult.reject(ErrorsCode.STEP_2_PAYMENT_PROCESSING_ERROR, new Object[]{messageSource.getMessage(message, locale)}, null);
                    SessionUtil.addToFlash(bindingResult, redirectAttributes);
                    SessionUtil.removePaymentToken(request);
                    return redirectReservation(optionalReservation, eventName, reservationId);
                }

                //
                SessionUtil.cleanupSession(request);

                return "redirect:/event/" + eventName + "/reservation/" + reservationId + "/success";
            });
    }

    private boolean isCaptchaInvalid(int cost, PaymentProxy paymentMethod, HttpServletRequest request, EventAndOrganizationId event) {
        return (cost == 0 || paymentMethod == PaymentProxy.OFFLINE || paymentMethod == PaymentProxy.ON_SITE)
                && configurationManager.isRecaptchaForOfflinePaymentEnabled(event)
                && !recaptchaService.checkRecaptcha(null, request);
    }

    private void assignTickets(String eventName, String reservationId, ContactAndTicketsForm contactAndTicketsForm, BindingResult bindingResult, HttpServletRequest request, boolean preAssign, boolean skipValidation) {
        if(!contactAndTicketsForm.isPostponeAssignment()) {
            contactAndTicketsForm.getTickets().forEach((ticketId, owner) -> {
                if (preAssign) {
                    Optional<Errors> bindingResultOptional = skipValidation ? Optional.empty() : Optional.of(bindingResult);
                    ticketHelper.preAssignTicket(eventName, reservationId, ticketId, owner, bindingResultOptional, request, (tr) -> {
                    }, Optional.empty());
                } else {
                    ticketHelper.assignTicket(eventName, ticketId, owner, Optional.of(bindingResult), request, (tr) -> {
                    }, Optional.empty(), true);
                }
            });
        }
    }

    @RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/re-send-email", method = RequestMethod.POST)
    public String reSendReservationConfirmationEmail(@PathVariable("eventName") String eventName,
            @PathVariable("reservationId") String reservationId, HttpServletRequest request) {

        Optional<Event> event = eventRepository.findOptionalByShortName(eventName);
        if (event.isEmpty()) {
            return "redirect:/";
        }

        Optional<TicketReservation> ticketReservation = ticketReservationManager.findById(reservationId);
        if (ticketReservation.isEmpty()) {
            return "redirect:/event/" + eventName + "/";
        }

        Locale locale = RequestContextUtils.getLocale(request);
        ticketReservationManager.sendConfirmationEmail(event.get(), ticketReservation.orElseThrow(IllegalStateException::new), locale);
        return "redirect:/event/" + eventName + "/reservation/" + reservationId + "/success?confirmation-email-sent=true";
    }


    @RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/ticket/{ticketIdentifier}/assign", method = RequestMethod.POST)
    public String assignTicketToPerson(@PathVariable("eventName") String eventName,
                                       @PathVariable("reservationId") String reservationId,
                                       @PathVariable("ticketIdentifier") String ticketIdentifier,
                                       UpdateTicketOwnerForm updateTicketOwner,
                                       BindingResult bindingResult,
                                       HttpServletRequest request,
                                       Model model) {

        Optional<Triple<ValidationResult, Event, Ticket>> result = ticketHelper.assignTicket(eventName, ticketIdentifier, updateTicketOwner, Optional.of(bindingResult), request, model);
        return result.map(t -> "redirect:/event/"+t.getMiddle().getShortName()+"/reservation/"+t.getRight().getTicketsReservationId()+"/success").orElse("redirect:/");
    }

    private boolean isExpressCheckoutEnabled(Event event, OrderSummary orderSummary) {
        return orderSummary.getTicketAmount() == 1 && ticketFieldRepository.countRequiredAdditionalFieldsForEvent(event.getId()) == 0;
    }

    private Optional<String> redirectIfNotValid(boolean backFromOverview,
                                                boolean cancelReservation,
                                                String eventName,
                                                String reservationId,
                                                HttpServletRequest request,
                                                Optional<Event> eventOptional) {

        if (eventOptional.isEmpty()) {
            return Optional.of("redirect:/");
        }

        Optional<TicketReservation> ticketReservation = ticketReservationManager.findById(reservationId);
        if (ticketReservation.isEmpty() || ticketReservation.get().getStatus() != TicketReservationStatus.PENDING) {
            return Optional.of(redirectReservation(ticketReservation, eventName, reservationId));
        }

        if(backFromOverview) {
            ticketReservationRepository.updateValidationStatus(reservationId, false);
            return Optional.of("redirect:/event/" + eventName + "/reservation/" + reservationId);
        }

        if (cancelReservation) {
            ticketReservationManager.cancelPendingReservation(reservationId, false, null);    //FIXME
            SessionUtil.cleanupSession(request);
            return Optional.of("redirect:/event/" + eventName + "/");
        }
        return Optional.empty();
    }

    private boolean isEUCountry(String countryCode) {
        return configurationManager.getRequiredValue(getSystemConfiguration(ConfigurationKeys.EU_COUNTRIES_LIST)).contains(countryCode);
    }


    private static PriceContainer.VatStatus determineVatStatus(PriceContainer.VatStatus current, boolean isVatExempt) {
        if(!isVatExempt) {
            return current;
        }
        return current == NOT_INCLUDED ? NOT_INCLUDED_EXEMPT : INCLUDED_EXEMPT;
    }
}
