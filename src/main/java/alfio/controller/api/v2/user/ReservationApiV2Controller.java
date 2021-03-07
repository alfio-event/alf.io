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
package alfio.controller.api.v2.user;

import alfio.controller.api.support.TicketHelper;
import alfio.controller.api.v2.model.PaymentProxyWithParameters;
import alfio.controller.api.v2.model.ReservationInfo;
import alfio.controller.api.v2.model.ReservationInfo.TicketsByTicketCategory;
import alfio.controller.api.v2.model.ReservationPaymentResult;
import alfio.controller.api.v2.model.ReservationStatusInfo;
import alfio.controller.api.v2.user.support.BookingInfoTicketLoader;
import alfio.controller.form.ContactAndTicketsForm;
import alfio.controller.form.PaymentForm;
import alfio.controller.form.ReservationCodeForm;
import alfio.controller.support.TemplateProcessor;
import alfio.manager.*;
import alfio.manager.i18n.MessageSourceManager;
import alfio.manager.payment.PaymentSpecification;
import alfio.manager.payment.StripeCreditCardManager;
import alfio.manager.support.PaymentResult;
import alfio.manager.support.response.ValidatedResponse;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.system.ReservationPriceCalculator;
import alfio.model.*;
import alfio.model.PurchaseContext.PurchaseContextType;
import alfio.model.subscription.Subscription;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.*;
import alfio.repository.*;
import alfio.util.*;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static alfio.model.PriceContainer.VatStatus.*;
import static alfio.model.system.ConfigurationKeys.*;
import static alfio.util.MonetaryUtil.unitToCents;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v2/public/")
@Log4j2
public class ReservationApiV2Controller {

    private final EventManager eventManager;
    private final EventRepository eventRepository;
    private final TicketReservationManager ticketReservationManager;
    private final TicketReservationRepository ticketReservationRepository;
    private final TicketFieldRepository ticketFieldRepository;
    private final MessageSourceManager messageSourceManager;
    private final ConfigurationManager configurationManager;
    private final PaymentManager paymentManager;
    private final FileUploadManager fileUploadManager;
    private final TemplateManager templateManager;
    private final ExtensionManager extensionManager;
    private final TicketHelper ticketHelper;
    private final EuVatChecker vatChecker;
    private final RecaptchaService recaptchaService;
    private final BookingInfoTicketLoader bookingInfoTicketLoader;
    private final PromoCodeDiscountRepository promoCodeDiscountRepository;
    private final AdditionalServiceItemRepository additionalServiceItemRepository;
    private final AdditionalServiceRepository additionalServiceRepository;
    private final BillingDocumentManager billingDocumentManager;
    private final PurchaseContextManager purchaseContextManager;
    private final SubscriptionRepository subscriptionRepository;

    /**
     * Note: now it will return for any states of the reservation.
     *
     * @param reservationId
     * @return
     */
    @GetMapping({"/reservation/{reservationId}",
        "/event/{eventName}/reservation/{reservationId}" //<-deprecated
    })
    public ResponseEntity<ReservationInfo> getReservationInfo(@PathVariable("reservationId") String reservationId) {

        Optional<ReservationInfo> res = purchaseContextManager.findByReservationId(reservationId).flatMap(purchaseContext -> ticketReservationManager.findById(reservationId).flatMap(reservation -> {

            var orderSummary = ticketReservationManager.orderSummaryForReservationId(reservationId, purchaseContext);

            var tickets = ticketReservationManager.findTicketsInReservation(reservationId);

            var ticketIds = tickets.stream().map(Ticket::getId).collect(Collectors.toSet());


            // check if the user can cancel ticket
            boolean hasPaidSupplement = ticketReservationManager.hasPaidSupplements(reservationId);
            //

            var ticketsInfo = purchaseContext.event().map(event -> {
                var valuesByTicketIds = ticketFieldRepository.findAllValuesByTicketIds(ticketIds)
                    .stream()
                    .collect(Collectors.groupingBy(TicketFieldValue::getTicketId));

                var descriptionsByTicketFieldId = ticketFieldRepository.findDescriptions(event.getShortName())
                    .stream()
                    .collect(Collectors.groupingBy(TicketFieldDescription::getTicketFieldConfigurationId));

                var ticketFieldsFilterer = bookingInfoTicketLoader.getTicketFieldsFilterer(reservationId, event);
                var ticketsByCategory = tickets.stream().collect(Collectors.groupingBy(Ticket::getCategoryId));
                //TODO: cleanup this transformation, we most likely don't need to fully load the ticket category
                var ticketsInReservation = ticketsByCategory
                    .entrySet()
                    .stream()
                    .map(e -> {
                        var tc = eventManager.getTicketCategoryById(e.getKey(), event.getId());
                        var ts = e.getValue().stream()
                            .map(t -> bookingInfoTicketLoader.toBookingInfoTicket(t, hasPaidSupplement, event, ticketFieldsFilterer, descriptionsByTicketFieldId, valuesByTicketIds, Map.of(), false))
                            .collect(Collectors.toList());
                        return new TicketsByTicketCategory(tc.getName(), tc.getTicketAccessType(), ts);
                    })
                    .collect(Collectors.toList());
                return Pair.of(ticketsByCategory, ticketsInReservation);
            });
            var ticketsByCategory = ticketsInfo.map(Pair::getLeft).orElse(Map.of());
            var ticketsInReservation = ticketsInfo.map(Pair::getRight).orElse(List.of());


            var additionalInfo = ticketReservationRepository.getAdditionalInfo(reservationId);

            var shortReservationId =  ticketReservationManager.getShortReservationID(purchaseContext, reservation);
            var italianInvoicing = additionalInfo.getInvoicingAdditionalInfo().getItalianEInvoicing() == null ?
                new TicketReservationInvoicingAdditionalInfo.ItalianEInvoicing(null, null, null, null, false) :
                additionalInfo.getInvoicingAdditionalInfo().getItalianEInvoicing();
            //


            var formattedExpirationDate = reservation.getValidity() != null ? formatDateForLocales(purchaseContext, ZonedDateTime.ofInstant(reservation.getValidity().toInstant(), purchaseContext.getZoneId()), "datetime.pattern") : null;

            var paymentToken = paymentManager.getPaymentToken(reservationId);
            boolean tokenAcquired = paymentToken.isPresent();
            PaymentProxy selectedPaymentProxy = paymentToken.map(PaymentToken::getPaymentProvider).orElse(null);

            //
            var containsCategoriesLinkedToGroups = purchaseContext.event().map(event -> ticketReservationManager.containsCategoriesLinkedToGroups(reservationId, event.getId())).orElse(false);
            //
            List<ReservationInfo.SubscriptionInfo> subscriptionInfos = null;
            if (purchaseContext.getType() == PurchaseContextType.subscription) {
                subscriptionInfos = subscriptionRepository.findSubscriptionsByReservationId(reservationId).stream().map(s -> new ReservationInfo.SubscriptionInfo(s.getId(), s.getPin())).collect(Collectors.toList());
            }

            return Optional.of(new ReservationInfo(reservation.getId(), shortReservationId,
                reservation.getFirstName(), reservation.getLastName(), reservation.getEmail(),
                reservation.getValidity().getTime(),
                ticketsInReservation, new ReservationInfo.ReservationInfoOrderSummary(orderSummary), reservation.getStatus(),
                additionalInfo.hasBeenValidated(),
                formattedExpirationDate,
                reservation.getInvoiceNumber(),
                reservation.isInvoiceRequested(),
                reservation.getHasInvoiceOrReceiptDocument(),
                reservation.getHasBeenPaid(),
                tokenAcquired,
                selectedPaymentProxy != null ? selectedPaymentProxy : reservation.getPaymentMethod(),
                //
                additionalInfo.getAddCompanyBillingDetails(),
                reservation.getCustomerReference(),
                additionalInfo.getSkipVatNr(),
                reservation.getBillingAddress(),
                additionalInfo.getBillingDetails(),
                //
                containsCategoriesLinkedToGroups,
                getActivePaymentMethods(purchaseContext, ticketsByCategory.keySet(), orderSummary, reservationId),
                subscriptionInfos
                ));
        }));

        //
        return res.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Map<PaymentMethod, PaymentProxyWithParameters> getActivePaymentMethods(PurchaseContext purchaseContext,
                                                                                   Collection<Integer> categoryIds,
                                                                                   OrderSummary orderSummary,
                                                                                   String reservationId) {
        if(!purchaseContext.isFreeOfCharge()) {
            var blacklistedMethodsForReservation = configurationManager.getBlacklistedMethodsForReservation(purchaseContext, categoryIds);
            return paymentManager.getPaymentMethods(purchaseContext, new TransactionRequest(orderSummary.getOriginalTotalPrice(), ticketReservationRepository.getBillingDetailsForReservation(reservationId)))
                .stream()
                .filter(p -> !blacklistedMethodsForReservation.contains(p.getPaymentMethod()))
                .filter(p -> TicketReservationManager.isValidPaymentMethod(p, purchaseContext, configurationManager))
                .collect(toMap(PaymentManager.PaymentMethodDTO::getPaymentMethod, pm -> new PaymentProxyWithParameters(pm.getPaymentProxy(), paymentManager.loadModelOptionsFor(List.of(pm.getPaymentProxy()), purchaseContext))));
        } else {
            return Map.of();
        }
    }

    @GetMapping({
        "/reservation/{reservationId}/status",
        "/event/{eventName}/reservation/{reservationId}/status" //<- deprecated
    })
    public ResponseEntity<ReservationStatusInfo> getReservationStatus(@PathVariable("reservationId") String reservationId) {

        Optional<ReservationStatusInfo> res = ticketReservationRepository.findOptionalStatusAndValidationById(reservationId)
            .map(status -> new ReservationStatusInfo(status.getStatus(), Boolean.TRUE.equals(status.getValidated())));
        return res.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }


    @DeleteMapping({
        "/reservation/{reservationId}",
        "/event/{eventName}/reservation/{reservationId}" //<- deprecated
    })
    public ResponseEntity<Boolean> cancelPendingReservation(@PathVariable("reservationId") String reservationId) {
        getReservationWithPendingStatus(reservationId).ifPresent(er -> ticketReservationManager.cancelPendingReservation(reservationId, false, null));
        return ResponseEntity.ok(true);
    }

    @PostMapping({
        "/reservation/{reservationId}/back-to-booking",
        "/event/{eventName}/reservation/{reservationId}/back-to-booking" //<- deprecated
    })
    public ResponseEntity<Boolean> backToBooking(@PathVariable("reservationId") String reservationId) {
        getReservationWithPendingStatus(reservationId).ifPresent(er -> ticketReservationRepository.updateValidationStatus(reservationId, false));
        return ResponseEntity.ok(true);
    }

    @PostMapping({
        "/reservation/{reservationId}",
        "/event/{eventName}/reservation/{reservationId}"// <- deprecated
    })
    public ResponseEntity<ValidatedResponse<ReservationPaymentResult>> confirmOverview(@PathVariable("reservationId") String reservationId,
                                                                                       @RequestParam("lang") String lang,
                                                                                       @RequestBody  PaymentForm paymentForm,
                                                                                       BindingResult bindingResult,
                                                                                       HttpServletRequest request) {

        return getReservation(reservationId).map(er -> {

           var event = er.getLeft();
           var reservation = er.getRight();
           var locale = LocaleUtil.forLanguageTag(lang, event);

            if (!reservation.getValidity().after(new Date())) {
                bindingResult.reject(ErrorsCode.STEP_2_ORDER_EXPIRED);
            }


            final TotalPrice reservationCost = ticketReservationManager.totalReservationCostWithVAT(reservationId).getLeft();

            paymentForm.validate(bindingResult, event, reservationCost);
            if (bindingResult.hasErrors()) {
                return buildReservationPaymentStatus(bindingResult);
            }

            if(isCaptchaInvalid(reservationCost.getPriceWithVAT(), paymentForm.getPaymentProxy(), paymentForm.getCaptcha(), request, event)) {
                log.debug("captcha validation failed.");
                bindingResult.reject(ErrorsCode.STEP_2_CAPTCHA_VALIDATION_FAILED);
            }

            if(!bindingResult.hasErrors()) {
                extensionManager.handleReservationValidation(event, reservation, paymentForm, bindingResult);
            }

            if (bindingResult.hasErrors()) {
                return buildReservationPaymentStatus(bindingResult);
            }

            CustomerName customerName = new CustomerName(reservation.getFullName(), reservation.getFirstName(), reservation.getLastName(), true);

            OrderSummary orderSummary = ticketReservationManager.orderSummaryForReservationId(reservationId, event);

            PaymentToken paymentToken = paymentManager.getPaymentToken(reservationId).orElse(null);
            if(paymentToken == null && StringUtils.isNotEmpty(paymentForm.getGatewayToken())) {
                paymentToken = paymentManager.buildPaymentToken(paymentForm.getGatewayToken(), paymentForm.getPaymentProxy(),
                    new PaymentContext(event, reservationId));
            }
            PaymentSpecification spec = new PaymentSpecification(reservationId, paymentToken, reservationCost.getPriceWithVAT(),
                event, reservation.getEmail(), customerName, reservation.getBillingAddress(), reservation.getCustomerReference(),
                locale, reservation.isInvoiceRequested(), !reservation.isDirectAssignmentRequested(),
                orderSummary, reservation.getVatCountryCode(), reservation.getVatNr(), reservation.getVatStatus(),
                Boolean.TRUE.equals(paymentForm.getTermAndConditionsAccepted()), Boolean.TRUE.equals(paymentForm.getPrivacyPolicyAccepted()));

            final PaymentResult status = ticketReservationManager.performPayment(spec, reservationCost, paymentForm.getPaymentProxy(), paymentForm.getSelectedPaymentMethod());

            if (status.isRedirect()) {
                var body = ValidatedResponse.toResponse(bindingResult,
                    new ReservationPaymentResult(!bindingResult.hasErrors(), true, status.getRedirectUrl(), status.isFailed(), status.getGatewayIdOrNull()));
                return ResponseEntity.ok(body);
            }

            if (!status.isSuccessful()) {
                String errorMessageCode = status.getErrorCode().orElse(StripeCreditCardManager.STRIPE_UNEXPECTED);
                MessageSourceResolvable message = new DefaultMessageSourceResolvable(new String[]{errorMessageCode, StripeCreditCardManager.STRIPE_UNEXPECTED});
                bindingResult.reject(ErrorsCode.STEP_2_PAYMENT_PROCESSING_ERROR, new Object[]{messageSourceManager.getMessageSourceFor(event).getMessage(message, locale)}, null);
                return buildReservationPaymentStatus(bindingResult);
            }


            return buildReservationPaymentStatus(bindingResult);

        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static ResponseEntity<ValidatedResponse<ReservationPaymentResult>> buildReservationPaymentStatus(BindingResult bindingResult) {
        var body = ValidatedResponse.toResponse(bindingResult, new ReservationPaymentResult(!bindingResult.hasErrors(), false, null, true, null));
        return ResponseEntity.status(bindingResult.hasErrors() ? HttpStatus.UNPROCESSABLE_ENTITY : HttpStatus.OK).body(body);
    }

    @PostMapping({
        "/reservation/{reservationId}/validate-to-overview",
        "/event/{eventName}/reservation/{reservationId}/validate-to-overview" //<-deprecated
    })
    public ResponseEntity<ValidatedResponse<Boolean>> validateToOverview(@PathVariable("reservationId") String reservationId,
                                                                         @RequestParam("lang") String lang,
                                                                         @RequestBody ContactAndTicketsForm contactAndTicketsForm,
                                                                         BindingResult bindingResult) {


        return getPurchaseContextAndReservationWithPendingStatus(reservationId).map(er -> {
            var purchaseContext = er.getLeft();
            var reservation = er.getRight();
            var locale = LocaleUtil.forLanguageTag(lang, purchaseContext);
            final TotalPrice reservationCost = ticketReservationManager.totalReservationCostWithVAT(reservation.withVatStatus(purchaseContext.getVatStatus())).getLeft();
            boolean forceAssignment = configurationManager.getFor(FORCE_TICKET_OWNER_ASSIGNMENT_AT_RESERVATION, purchaseContext.getConfigurationLevel()).getValueAsBooleanOrDefault();

            purchaseContext.event().ifPresent(event -> {
                if (forceAssignment || ticketReservationManager.containsCategoriesLinkedToGroups(reservationId, event.getId())) {
                    contactAndTicketsForm.setPostponeAssignment(false);
                }
            });

            boolean invoiceOnly = configurationManager.isInvoiceOnly(purchaseContext);

            if(invoiceOnly && reservationCost.getPriceWithVAT() > 0) {
                //override, that's why we save it
                contactAndTicketsForm.setInvoiceRequested(true);
            } else if (reservationCost.getPriceWithVAT() == 0) {
                contactAndTicketsForm.setInvoiceRequested(false);
            }

            CustomerName customerName = new CustomerName(contactAndTicketsForm.getFullName(), contactAndTicketsForm.getFirstName(), contactAndTicketsForm.getLastName(), true, false);


            ticketReservationRepository.resetVat(reservationId, contactAndTicketsForm.isInvoiceRequested(), purchaseContext.getVatStatus(),
                reservation.getSrcPriceCts(), reservationCost.getPriceWithVAT(), reservationCost.getVAT(), Math.abs(reservationCost.getDiscount()), reservation.getCurrencyCode());
            if(contactAndTicketsForm.isBusiness()) {
                checkAndApplyVATRules(purchaseContext, reservationId, contactAndTicketsForm, bindingResult);
            }

            //persist data
            ticketReservationManager.updateReservation(reservationId, customerName, contactAndTicketsForm.getEmail(),
                contactAndTicketsForm.getBillingAddressCompany(), contactAndTicketsForm.getBillingAddressLine1(), contactAndTicketsForm.getBillingAddressLine2(),
                contactAndTicketsForm.getBillingAddressZip(), contactAndTicketsForm.getBillingAddressCity(), contactAndTicketsForm.getBillingAddressState(), contactAndTicketsForm.getVatCountryCode(),
                contactAndTicketsForm.getCustomerReference(), contactAndTicketsForm.getVatNr(), contactAndTicketsForm.isInvoiceRequested(),
                contactAndTicketsForm.getAddCompanyBillingDetails(), contactAndTicketsForm.canSkipVatNrCheck(), false, locale);

            boolean italyEInvoicing = configurationManager.getFor(ENABLE_ITALY_E_INVOICING, purchaseContext.getConfigurationLevel()).getValueAsBooleanOrDefault();

            if(italyEInvoicing) {
                ticketReservationManager.updateReservationInvoicingAdditionalInformation(reservationId, purchaseContext,
                    new TicketReservationInvoicingAdditionalInfo(getItalianInvoicingInfo(contactAndTicketsForm))
                );
            }

            purchaseContext.event().ifPresent(event -> {
                assignTickets(event.getShortName(), reservationId, contactAndTicketsForm, bindingResult, locale, true, true);
            });
            //

            Map<ConfigurationKeys, Boolean> formValidationParameters = Collections.singletonMap(ENABLE_ITALY_E_INVOICING, italyEInvoicing);

            var ticketFieldFilterer = purchaseContext.event().map(event -> bookingInfoTicketLoader.getTicketFieldsFilterer(reservationId, event));

            //
            contactAndTicketsForm.validate(bindingResult, purchaseContext, new SameCountryValidator(configurationManager, extensionManager, purchaseContext, reservationId, vatChecker),
                formValidationParameters, ticketFieldFilterer);
            //

            if(!bindingResult.hasErrors()) {
                extensionManager.handleReservationValidation(purchaseContext, reservation, contactAndTicketsForm, bindingResult);
            }

            if(!bindingResult.hasErrors()) {
                ticketReservationRepository.updateValidationStatus(reservationId, true);
            }

            var body = ValidatedResponse.toResponse(bindingResult, !bindingResult.hasErrors());
            return ResponseEntity.status(bindingResult.hasErrors() ? HttpStatus.UNPROCESSABLE_ENTITY : HttpStatus.OK).body(body);
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private TicketReservationInvoicingAdditionalInfo.ItalianEInvoicing getItalianInvoicingInfo(ContactAndTicketsForm contactAndTicketsForm) {
        if("IT".equalsIgnoreCase(contactAndTicketsForm.getVatCountryCode())) {
            return new TicketReservationInvoicingAdditionalInfo.ItalianEInvoicing(
                StringUtils.upperCase(contactAndTicketsForm.getItalyEInvoicingFiscalCode()),
                contactAndTicketsForm.getItalyEInvoicingReferenceType(),
                contactAndTicketsForm.getItalyEInvoicingReferenceAddresseeCode(),
                contactAndTicketsForm.getItalyEInvoicingReferencePEC(),
                contactAndTicketsForm.isItalyEInvoicingSplitPayment());
        }
        return null;
    }

    private void assignTickets(String eventName, String reservationId, ContactAndTicketsForm contactAndTicketsForm, BindingResult bindingResult, Locale locale, boolean preAssign, boolean skipValidation) {
        if(!contactAndTicketsForm.isPostponeAssignment()) {
            contactAndTicketsForm.getTickets().forEach((ticketId, owner) -> {
                if (preAssign) {
                    Optional<Errors> bindingResultOptional = skipValidation ? Optional.empty() : Optional.of(bindingResult);
                    ticketHelper.preAssignTicket(eventName, reservationId, ticketId, owner, bindingResultOptional, locale, Optional.empty());
                } else {
                    ticketHelper.assignTicket(eventName, ticketId, owner, Optional.of(bindingResult), locale, Optional.empty(), true);
                }
            });
        }
    }

    private void checkAndApplyVATRules(PurchaseContext purchaseContext, String reservationId, ContactAndTicketsForm contactAndTicketsForm, BindingResult bindingResult) {
        // VAT handling
        String country = contactAndTicketsForm.getVatCountryCode();

        // validate VAT presence if EU mode is enabled
        if (vatChecker.isReverseChargeEnabledFor(purchaseContext) && (country == null || isEUCountry(country))) {
            ValidationUtils.rejectIfEmptyOrWhitespace(bindingResult, "vatNr", "error.emptyField");
        }

        try {
            var optionalReservation = ticketReservationRepository.findOptionalReservationById(reservationId);
            Optional<VatDetail> vatDetail = optionalReservation
                .filter(e -> EnumSet.of(INCLUDED, NOT_INCLUDED).contains(purchaseContext.getVatStatus()))
                .filter(e -> vatChecker.isReverseChargeEnabledFor(purchaseContext))
                .flatMap(e -> vatChecker.checkVat(contactAndTicketsForm.getVatNr(), country, purchaseContext));


            if(vatDetail.isPresent()) {
                var vatValidation = vatDetail.get();
                if (!vatValidation.isValid()) {
                    bindingResult.rejectValue("vatNr", "error.STEP_2_INVALID_VAT");
                } else {
                    var reservation = ticketReservationManager.findById(reservationId).orElseThrow();
                    var currencyCode = reservation.getCurrencyCode();
                    PriceContainer.VatStatus vatStatus = determineVatStatus(purchaseContext.getVatStatus(), vatValidation.isVatExempt());
                    updateBillingData(reservationId, contactAndTicketsForm, purchaseContext, country, trimToNull(vatValidation.getVatNr()), reservation, vatStatus);
                    vatChecker.logSuccessfulValidation(vatValidation, reservationId, purchaseContext.event().map(Event::getId).orElse(null));
                }
            } else if(optionalReservation.isPresent() && contactAndTicketsForm.isItalyEInvoicingSplitPayment()) {
                var reservation = optionalReservation.get();
                var vatStatus = purchaseContext.getVatStatus() == INCLUDED ? INCLUDED_NOT_CHARGED : NOT_INCLUDED_NOT_CHARGED;
                updateBillingData(reservationId, contactAndTicketsForm, purchaseContext, country, trimToNull(contactAndTicketsForm.getVatNr()), reservation, vatStatus);
            }
        } catch (IllegalStateException ise) {//vat checker failure
            bindingResult.rejectValue("vatNr", "error.vatVIESDown");
        }
    }

    private void updateBillingData(String reservationId, ContactAndTicketsForm contactAndTicketsForm, PurchaseContext purchaseContext, String country, String vatNr, TicketReservation reservation, PriceContainer.VatStatus vatStatus) {
        var discount = reservation.getPromoCodeDiscountId() != null ? promoCodeDiscountRepository.findById(reservation.getPromoCodeDiscountId()) : null;
        var additionalServiceItems = additionalServiceItemRepository.findByReservationUuid(reservation.getId());
        var tickets = ticketReservationManager.findTicketsInReservation(reservation.getId());
        var additionalServices = purchaseContext.event().map(event -> additionalServiceRepository.loadAllForEvent(event.getId())).orElse(List.of());
        var subscriptions = subscriptionRepository.findSubscriptionsByReservationId(reservationId);
        var appliedSubscription = subscriptionRepository.findAppliedSubscriptionByReservationId(reservationId);
        var calculator = new ReservationPriceCalculator(reservation.withVatStatus(vatStatus), discount, tickets, additionalServiceItems, additionalServices, purchaseContext, subscriptions, appliedSubscription);
        var currencyCode = reservation.getCurrencyCode();
        ticketReservationRepository.updateBillingData(vatStatus, reservation.getSrcPriceCts(),
            unitToCents(calculator.getFinalPrice(), currencyCode), unitToCents(calculator.getVAT(), currencyCode), unitToCents(calculator.getAppliedDiscount(), currencyCode),
            reservation.getCurrencyCode(), vatNr,
            country, contactAndTicketsForm.isInvoiceRequested(), reservationId);
    }

    private Optional<Pair<PurchaseContext, TicketReservation>> getReservation(String reservationId) {
        return purchaseContextManager.findByReservationId(reservationId)
            .flatMap(purchaseContext -> ticketReservationManager.findById(reservationId)
                .flatMap(reservation -> Optional.of(Pair.of(purchaseContext, reservation))));
    }

    private Optional<TicketReservation> getReservationWithPendingStatus(String reservationId) {
        return ticketReservationManager.findById(reservationId).filter(reservation -> reservation.getStatus() == TicketReservation.TicketReservationStatus.PENDING);
    }

    private Optional<Pair<PurchaseContext, TicketReservation>> getPurchaseContextAndReservationWithPendingStatus(String reservationId) {
        return purchaseContextManager.findByReservationId(reservationId)
            .flatMap(event -> ticketReservationManager.findById(reservationId)
                .filter(reservation -> reservation.getStatus() == TicketReservation.TicketReservationStatus.PENDING)
                .flatMap(reservation -> Optional.of(Pair.of(event, reservation))));
    }

    @PostMapping("/{purchaseContextType}/{publicIdentifier}/reservation/{reservationId}/re-send-email")
    public ResponseEntity<Boolean> reSendReservationConfirmationEmail(@PathVariable("purchaseContextType") PurchaseContextType purchaseContextType,
                                                                      @PathVariable("publicIdentifier") String publicIdentifier,
                                                                      @PathVariable("reservationId") String reservationId,
                                                                      @RequestParam("lang") String lang,
                                                                      Principal principal) {


        return ResponseEntity.of(purchaseContextManager.findBy(purchaseContextType, publicIdentifier)
            .map(purchaseContext -> ticketReservationManager.findById(reservationId)
                .map(ticketReservation -> {
                    ticketReservationManager.sendConfirmationEmail(purchaseContext, ticketReservation, LocaleUtil.forLanguageTag(lang, purchaseContext), principal != null ? principal.getName() : null);
                    return true;
                }).orElse(false)));
    }


    //
    @GetMapping("/event/{eventName}/reservation/{reservationId}/receipt")
    public ResponseEntity<Void> getReceipt(@PathVariable("eventName") String eventName,
                                           @PathVariable("reservationId") String reservationId,
                                           HttpServletResponse response,
                                           Authentication authentication) {
        return handleReservationWith(eventName, reservationId, authentication, generatePdfFunction(false, response));
    }

    @GetMapping("/event/{eventName}/reservation/{reservationId}/invoice")
    public ResponseEntity<Void> getInvoice(@PathVariable("eventName") String eventName,
                                           @PathVariable("reservationId") String reservationId,
                                           HttpServletResponse response,
                                           Authentication authentication) {
        return handleReservationWith(eventName, reservationId, authentication, generatePdfFunction(true, response));
    }
    //

    private ResponseEntity<Void> handleReservationWith(String eventName, String reservationId, Authentication authentication,
                                                       BiFunction<Event, TicketReservation, ResponseEntity<Void>> with) {
        ResponseEntity<Void> notFound = ResponseEntity.notFound().build();
        ResponseEntity<Void> badRequest = ResponseEntity.badRequest().build();



        return eventRepository.findOptionalByShortName(eventName).map(event -> {
                if(canAccessReceiptOrInvoice(event, authentication)) {
                    return ticketReservationManager.findById(reservationId).map(ticketReservation -> with.apply(event, ticketReservation)).orElse(notFound);
                } else {
                    return badRequest;
                }
            }
        ).orElse(notFound);
    }

    private boolean canAccessReceiptOrInvoice(Configurable configurable, Authentication authentication) {
        return configurationManager.canGenerateReceiptOrInvoiceToCustomer(configurable) || !isAnonymous(authentication);
    }


    private boolean isAnonymous(Authentication authentication) {
        return authentication == null ||
            authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch("ROLE_ANONYMOUS"::equals);
    }

    private BiFunction<Event, TicketReservation, ResponseEntity<Void>> generatePdfFunction(boolean forInvoice, HttpServletResponse response) {
        return (event, reservation) -> {
            if((forInvoice ^ reservation.getInvoiceNumber() != null) || reservation.isCancelled()) {
                return ResponseEntity.notFound().build();
            }

            BillingDocument billingDocument = billingDocumentManager.getOrCreateBillingDocument(event, reservation, null, ticketReservationManager.orderSummaryForReservation(reservation, event));

            try {
                FileUtil.sendHeaders(response, event.getShortName(), reservation.getId(), billingDocument);
                TemplateProcessor.buildReceiptOrInvoicePdf(event, fileUploadManager, LocaleUtil.forLanguageTag(reservation.getUserLanguage()),
                    templateManager, billingDocument.getModel(), forInvoice ? TemplateResource.INVOICE_PDF : TemplateResource.RECEIPT_PDF,
                    extensionManager, response.getOutputStream());
                return ResponseEntity.ok().build();
            } catch (IOException ioe) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        };
    }
    //----------------


    @PostMapping({
        "/reservation/{reservationId}/payment/{method}/init",
        "/event/{eventName}/reservation/{reservationId}/payment/{method}/init" //<-deprecated
    })
    public ResponseEntity<TransactionInitializationToken> initTransaction(@PathVariable("reservationId") String reservationId,
                                                                          @PathVariable("method") String paymentMethodStr,
                                                                          @RequestParam MultiValueMap<String, String> allParams) {
        var paymentMethod = PaymentMethod.safeParse(paymentMethodStr);

        if(paymentMethod == null) {
            return ResponseEntity.badRequest().build();
        }

        Optional<ResponseEntity<TransactionInitializationToken>> responseEntity = getEventReservationPair(reservationId)
            .map(pair -> {
                var event = pair.getLeft();
                return ticketReservationManager.initTransaction(event, reservationId, paymentMethod, allParams)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
            });
        return responseEntity.orElseGet(() -> ResponseEntity.badRequest().build());
    }

    @DeleteMapping({
        "/reservation/{reservationId}/payment/token",
        "/event/{eventName}/reservation/{reservationId}/payment/token" //<-deprecated
    })
    public ResponseEntity<Boolean> removeToken(@PathVariable("eventName") String eventName,
                                               @PathVariable("reservationId") String reservationId) {

        var res = getEventReservationPair(reservationId).map(et -> paymentManager.removePaymentTokenReservation(et.getRight().getId())).orElse(false);
        return ResponseEntity.ok(res);
    }

    @DeleteMapping({
        "/reservation/{reservationId}/payment",
        "/event/{eventName}/reservation/{reservationId}/payment" //<-deprecated
    })
    public ResponseEntity<Boolean> deletePaymentAttempt(@PathVariable("reservationId") String reservationId) {

        var res = getEventReservationPair(reservationId).map(et -> ticketReservationManager.cancelPendingPayment(et.getRight().getId(), et.getLeft())).orElse(false);
        return ResponseEntity.ok(res);
    }

    //FIXME: rename ->getPurchaseContextReservationPair
    private Optional<Pair<PurchaseContext, TicketReservation>> getEventReservationPair(String reservationId) {
        return purchaseContextManager.findByReservationId(reservationId)
            .map(event -> Pair.of(event, ticketReservationManager.findById(reservationId)))
            .filter(pair -> pair.getRight().isPresent())
            .map(pair -> Pair.of(pair.getLeft(), pair.getRight().orElseThrow()));
    }

    @GetMapping({
        "/reservation/{reservationId}/payment/{method}/status",
        "/event/{eventName}/reservation/{reservationId}/payment/{method}/status" //<-deprecated
    })
    public ResponseEntity<ReservationPaymentResult> getTransactionStatus(
                                                              @PathVariable("reservationId") String reservationId,
                                                              @PathVariable("method") String paymentMethodStr) {

        var paymentMethod = PaymentMethod.safeParse(paymentMethodStr);

        if(paymentMethod == null) {
            return ResponseEntity.badRequest().build();
        }

        return getEventReservationPair(reservationId)
            .flatMap(pair -> paymentManager.getTransactionStatus(pair.getRight(), paymentMethod))
            .map(pr -> ResponseEntity.ok(new ReservationPaymentResult(pr.isSuccessful(), pr.isRedirect(), pr.getRedirectUrl(), pr.isFailed(), pr.getGatewayIdOrNull())))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/reservation/{reservationId}/apply-code")
    public ResponseEntity<ValidatedResponse<Boolean>> applyCode(@PathVariable("reservationId") String reservationId, @RequestBody ReservationCodeForm reservationCodeForm, BindingResult bindingResult) {
        if(reservationCodeForm.getType() != ReservationCodeForm.ReservationCodeType.SUBSCRIPTION) {
            throw new IllegalStateException(reservationCodeForm.getType() + " not supported");
        }
        boolean res = getEventReservationPair(reservationId).map(et -> {
            boolean isUUID = reservationCodeForm.isCodeUUID();
            log.trace("is code UUID {}", isUUID);
            var pin = reservationCodeForm.getCode();
            if (!isUUID && !PinGenerator.isPinValid(pin, Subscription.PIN_LENGTH)) {
                bindingResult.reject("error.restrictedValue");
                return false;
            }

            //ensure pin length, as we will do a like concat(pin,'%'), it could be dangerous to have an empty string...
            Assert.isTrue(pin.length() >= Subscription.PIN_LENGTH, "Pin must have a length of at least 8 characters");

            var partialUuid = !isUUID ? PinGenerator.pinToPartialUuid(pin, Subscription.PIN_LENGTH) : pin;
            var email = reservationCodeForm.getEmail();
            var requireEmail = false;
            int count;
            if (isUUID) {
                count = subscriptionRepository.countSubscriptionById(UUID.fromString(pin));
            } else {
                count = subscriptionRepository.countSubscriptionByPartialUuid(partialUuid);
                if (count > 1) {
                    count = subscriptionRepository.countSubscriptionByPartialUuidAndEmail(partialUuid, email);
                    requireEmail = true;
                }
            }
            log.trace("code count is {}", count);
            if (count == 0) {
                bindingResult.reject(isUUID ? "subscription.uuid.not.found" : "subscription.pin.not.found");
            }
            if (count > 1) {
                bindingResult.reject("subscription.code.insert.full");
            }

            if (bindingResult.hasErrors()) {
                return false;
            }

            var subscriptionId = isUUID ? UUID.fromString(pin) : requireEmail ? subscriptionRepository.getSubscriptionIdByPartialUuidAndEmail(partialUuid, email) : subscriptionRepository.getSubscriptionIdByPartialUuid(partialUuid);
            var subscriptionDescriptor = subscriptionRepository.findDescriptorBySubscriptionId(subscriptionId);
            var subscription = subscriptionRepository.findSubscriptionById(subscriptionId);
            subscription.isValid(subscriptionDescriptor, Optional.of(bindingResult));
            if (bindingResult.hasErrors()) {
                return false;
            }
            return ticketReservationManager.applySubscriptionCode(et.getRight(), subscriptionId, reservationCodeForm.getAmount());
        }).orElse(false);
        return ResponseEntity.ok(ValidatedResponse.toResponse(bindingResult, res));
    }

    @DeleteMapping("/reservation/{reservationId}/remove-code")
    public ResponseEntity<Boolean> removeCode(@PathVariable("reservationId") String reservationId, @RequestParam("type") ReservationCodeForm.ReservationCodeType type) {
        boolean res = false;
        if (type == ReservationCodeForm.ReservationCodeType.SUBSCRIPTION) {
            res = getEventReservationPair(reservationId).map(et -> ticketReservationManager.removeSubscription(et.getRight())).orElse(false);
        }
        return ResponseEntity.ok(res);
    }


    private Map<String, String> formatDateForLocales(PurchaseContext purchaseContext, ZonedDateTime date, String formattingCode) {

        var messageSource = messageSourceManager.getMessageSourceFor(purchaseContext);

        Map<String, String> res = new HashMap<>();
        for (ContentLanguage cl : purchaseContext.getContentLanguages()) {
            var formatter = messageSource.getMessage(formattingCode, null, cl.getLocale());
            res.put(cl.getLocale().getLanguage(), DateTimeFormatter.ofPattern(formatter, cl.getLocale()).format(date));
        }
        return res;
    }

    private boolean isEUCountry(String countryCode) {
        return configurationManager.getForSystem(EU_COUNTRIES_LIST).getRequiredValue().contains(countryCode);
    }

    private static PriceContainer.VatStatus determineVatStatus(PriceContainer.VatStatus current, boolean isVatExempt) {
        if(!isVatExempt) {
            return current;
        }
        return current == NOT_INCLUDED ? NOT_INCLUDED_EXEMPT : INCLUDED_EXEMPT;
    }


    private boolean isCaptchaInvalid(int cost, PaymentProxy paymentMethod, String recaptchaResponse, HttpServletRequest request, Configurable configurable) {
        return (cost == 0 || paymentMethod == PaymentProxy.OFFLINE || paymentMethod == PaymentProxy.ON_SITE)
            && configurationManager.isRecaptchaForOfflinePaymentAndFreeEnabled(configurable.getConfigurationLevel())
            && !recaptchaService.checkRecaptcha(recaptchaResponse, request);
    }
}
