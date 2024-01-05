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

import alfio.controller.api.support.AdditionalServiceWithData;
import alfio.controller.api.support.BookingInfoTicketLoader;
import alfio.controller.api.support.TicketHelper;
import alfio.controller.api.v2.model.PaymentProxyWithParameters;
import alfio.controller.api.v2.model.ReservationInfo;
import alfio.controller.api.v2.model.ReservationInfo.TicketsByTicketCategory;
import alfio.controller.api.v2.model.ReservationPaymentResult;
import alfio.controller.api.v2.model.ReservationStatusInfo;
import alfio.controller.api.v2.user.support.ReservationAccessDenied;
import alfio.controller.form.ContactAndTicketsForm;
import alfio.controller.form.PaymentForm;
import alfio.controller.form.ReservationCodeForm;
import alfio.controller.support.CustomBindingResult;
import alfio.controller.support.TemplateProcessor;
import alfio.manager.*;
import alfio.manager.i18n.MessageSourceManager;
import alfio.manager.payment.PaymentSpecification;
import alfio.manager.payment.StripeCreditCardManager;
import alfio.manager.support.AdditionalServiceHelper;
import alfio.manager.support.PaymentResult;
import alfio.manager.support.response.ValidatedResponse;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.PublicUserManager;
import alfio.model.*;
import alfio.model.PurchaseContext.PurchaseContextType;
import alfio.model.metadata.SubscriptionMetadata;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.model.subscription.UsageDetails;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.*;
import alfio.repository.*;
import alfio.util.*;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.BindingResult;
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

import static alfio.controller.api.support.BookingInfoTicketLoader.toAdditionalFieldsStream;
import static alfio.model.PurchaseContextFieldConfiguration.EVENT_RELATED_CONTEXTS;
import static alfio.model.system.ConfigurationKeys.ENABLE_ITALY_E_INVOICING;
import static alfio.model.system.ConfigurationKeys.FORCE_TICKET_OWNER_ASSIGNMENT_AT_RESERVATION;
import static java.util.Objects.requireNonNullElse;
import static java.util.Objects.requireNonNullElseGet;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v2/public/")
@Log4j2
public class ReservationApiV2Controller {

    private final EventManager eventManager;
    private final EventRepository eventRepository;
    private final TicketReservationManager ticketReservationManager;
    private final TicketReservationRepository ticketReservationRepository;
    private final PurchaseContextFieldRepository purchaseContextFieldRepository;
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
    private final BillingDocumentManager billingDocumentManager;
    private final PurchaseContextManager purchaseContextManager;
    private final SubscriptionRepository subscriptionRepository;
    private final TicketRepository ticketRepository;
    private final PublicUserManager publicUserManager;
    private final ReverseChargeManager reverseChargeManager;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final AdditionalServiceManager additionalServiceManager;
    private final AdditionalServiceHelper additionalServiceHelper;
    private final PurchaseContextFieldManager purchaseContextFieldManager;

    /**
     * Note: now it will return for any states of the reservation.
     *
     * @param reservationId
     * @return
     */
    @GetMapping({"/reservation/{reservationId}",
        "/event/{eventName}/reservation/{reservationId}" //<-deprecated
    })
    public ResponseEntity<ReservationInfo> getReservationInfo(@PathVariable("reservationId") String reservationId, Principal principal) {

        Optional<ReservationInfo> res = purchaseContextManager.findByReservationId(reservationId).flatMap(purchaseContext -> ticketReservationManager.findById(reservationId).flatMap(reservation -> {

            validateAccessToReservation(principal, reservation);

            var orderSummary = ticketReservationManager.orderSummaryForReservationId(reservationId, purchaseContext);
            List<TicketsByTicketCategory> ticketsInReservation = null;
            Set<Integer> categoryIds = null;
            var additionalInfo = ticketReservationRepository.getAdditionalInfo(reservationId);
            boolean containsCategoriesLinkedToGroups = false;
            List<AdditionalServiceWithData> additionalServices = null;
            var descriptionsByFieldId = purchaseContextFieldManager.findDescriptionsGroupedByFieldId(purchaseContext);
            if (purchaseContext.ofType(PurchaseContextType.event)) {
                var event = (Event) purchaseContext;
                var tickets = ticketReservationManager.findTicketsInReservation(reservationId);
                var ticketIds = tickets.stream().map(Ticket::getId).collect(Collectors.toSet());

                // check if the user can cancel ticket
                if (!ticketIds.isEmpty()) {
                    var valuesByTicketIds = purchaseContextFieldRepository.findAllValuesByTicketIds(ticketIds)
                        .stream()
                        .collect(Collectors.groupingBy(PurchaseContextFieldValue::getTicketId));

                    var ticketFieldsFilterer = bookingInfoTicketLoader.getTicketFieldsFilterer(reservationId, event);
                    var ticketsByCategory = tickets.stream().collect(Collectors.groupingBy(Ticket::getCategoryId));
                    var hasPaidSupplement = ticketReservationManager.hasPaidSupplements(event.getId(), reservationId);
                    var categories = ticketCategoryRepository.findCategoriesInReservation(reservationId);
                    ticketsInReservation = ticketsByCategory
                        .entrySet()
                        .stream()
                        .map(e -> {
                            var tc = categories.stream().filter(t -> t.getId() == e.getKey()).findFirst().orElseThrow();
                            var context = event.supportsLinkedAdditionalServices() ? EnumSet.of(PurchaseContextFieldConfiguration.Context.ATTENDEE) : EVENT_RELATED_CONTEXTS;
                            var ts = e.getValue().stream()
                                .map(t -> bookingInfoTicketLoader.toBookingInfoTicket(t, hasPaidSupplement, event, ticketFieldsFilterer, descriptionsByFieldId, valuesByTicketIds, Map.of(), false, context))
                                .collect(Collectors.toList());
                            return new TicketsByTicketCategory(tc.getName(), tc.getTicketAccessType(), ts);
                        })
                        .collect(Collectors.toList());
                    containsCategoriesLinkedToGroups = ticketReservationManager.containsCategoriesLinkedToGroups(reservationId, event.getId());
                    categoryIds = ticketsByCategory.keySet();
                    var additionalServiceItems = additionalServiceManager.findItemsInReservation(event.getId(), reservationId);
                    Map<Integer, List<AdditionalServiceFieldValue>> additionalServicesByItemId = additionalServiceItems.isEmpty() ? Map.of() :
                        purchaseContextFieldRepository.findAdditionalServicesValueByItemIds(additionalServiceItems.stream().map(AdditionalServiceItem::getId).collect(Collectors.toList()))
                            .stream().collect(groupingBy(AdditionalServiceFieldValue::getAdditionalServiceItemId));
                    additionalServices = additionalServiceHelper.getAdditionalServicesWithData(event,
                        additionalServiceItems,
                        additionalServicesByItemId,
                        descriptionsByFieldId,
                        tickets);
                }
            }



            var shortReservationId =  configurationManager.getShortReservationID(purchaseContext, reservation);
            //

            var formattedExpirationDate = reservation.getValidity() != null ? formatDateForLocales(purchaseContext, ZonedDateTime.ofInstant(reservation.getValidity().toInstant(), purchaseContext.getZoneId()), "datetime.pattern") : null;

            var paymentToken = paymentManager.getPaymentToken(reservationId);
            boolean tokenAcquired = paymentToken.isPresent();
            PaymentProxy selectedPaymentProxy = paymentToken.map(PaymentToken::getPaymentProvider).orElse(null);


            //
            List<ReservationInfo.SubscriptionInfo> subscriptionInfos = null;
            if (purchaseContext.ofType(PurchaseContextType.subscription)) {
                subscriptionInfos = subscriptionRepository.findSubscriptionsByReservationId(reservationId).stream()
                    .limit(1) // since we support only one subscription for now, it makes sense to limit the result to avoid N+1
                    .map(s -> {
                        int usageCount = ticketRepository.countSubscriptionUsage(s.getId(), null);
                        var metadata = requireNonNullElseGet(subscriptionRepository.getSubscriptionMetadata(s.getId()), SubscriptionMetadata::empty);
                        var fields = purchaseContextFieldManager.findAdditionalFields(purchaseContext);
                        var valuesById = purchaseContextFieldRepository.findAllValuesBySubscriptionIds(List.of(s.getId()))
                            .stream()
                            .collect(Collectors.groupingBy(PurchaseContextFieldValue::getFieldConfigurationId));
                        var additionalFields = fields.stream()
                            .sorted(Comparator.comparing(PurchaseContextFieldConfiguration::getOrder))
                            .flatMap(tfc -> toAdditionalFieldsStream(descriptionsByFieldId, tfc, valuesById))
                            .collect(Collectors.toList());
                        return new ReservationInfo.SubscriptionInfo(
                            s.getStatus() == AllocationStatus.ACQUIRED ? s.getId() : null,
                            s.getStatus() == AllocationStatus.ACQUIRED ? s.getPin() : null,
                            UsageDetails.fromSubscription(s, usageCount),
                            new ReservationInfo.SubscriptionOwner(s.getFirstName(), s.getLastName(), s.getEmail()),
                            metadata.getConfiguration(),
                            additionalFields
                        );
                    })
                    .collect(Collectors.toList());
            }

            return Optional.of(new ReservationInfo(reservation.getId(), shortReservationId,
                reservation.getFirstName(), reservation.getLastName(), reservation.getEmail(),
                reservation.getValidity().getTime(),
                requireNonNullElse(ticketsInReservation, List.of()),
                new ReservationInfo.ReservationInfoOrderSummary(orderSummary), reservation.getStatus(),
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
                getActivePaymentMethods(purchaseContext, requireNonNullElse(categoryIds, Set.of()), orderSummary, reservationId),
                subscriptionInfos,
                ticketReservationRepository.getMetadata(reservationId),
                requireNonNullElse(additionalServices, List.of())
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
                                                                                       HttpServletRequest request,
                                                                                       Principal principal) {

        return getReservation(reservationId).map(er -> {
            var event = er.getLeft();
            var reservation = er.getRight();
            var locale = LocaleUtil.forLanguageTag(lang, event);

            validateAccessToReservation(principal, reservation);

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

            final PaymentResult status = ticketReservationManager.performPayment(spec, reservationCost, paymentForm.getPaymentProxy(), paymentForm.getSelectedPaymentMethod(), principal);

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
                                                                         @RequestParam(value = "ignoreWarnings", defaultValue = "false") boolean ignoreWarnings,
                                                                         @RequestBody ContactAndTicketsForm contactAndTicketsForm,
                                                                         BindingResult br,
                                                                         Authentication principal) {

        var bindingResult = new CustomBindingResult(br);

        return getPurchaseContextAndReservationWithPendingStatus(reservationId).map(er -> {
            var purchaseContext = er.getLeft();
            var reservation = er.getRight();

            validateAccessToReservation(principal, reservation);

            var locale = LocaleUtil.forLanguageTag(lang, purchaseContext);
            final TotalPrice reservationCost = ticketReservationManager.totalReservationCostWithVAT(reservation.withVatStatus(purchaseContext.getVatStatus())).getLeft();

            purchaseContext.event().ifPresent(event -> {
                boolean forceAssignment = configurationManager.getFor(FORCE_TICKET_OWNER_ASSIGNMENT_AT_RESERVATION, purchaseContext.getConfigurationLevel()).getValueAsBooleanOrDefault();
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

            var optionalCustomTaxPolicy = extensionManager.handleCustomTaxPolicy(purchaseContext, reservationId, contactAndTicketsForm, reservationCost);
            if (optionalCustomTaxPolicy.isPresent()) {
                log.debug("Custom tax policy returned for reservation {}. Applying it.", reservationId);
                reverseChargeManager.applyCustomTaxPolicy(
                    purchaseContext,
                    optionalCustomTaxPolicy.get(),
                    reservationId,
                    contactAndTicketsForm,
                    bindingResult);
            } else if(reservationCost.getPriceWithVAT() > 0 && (contactAndTicketsForm.isBusiness() || configurationManager.noTaxesFlagDefinedFor(ticketCategoryRepository.findCategoriesInReservation(reservationId)))) {
                reverseChargeManager.checkAndApplyVATRules(purchaseContext, reservationId, contactAndTicketsForm, bindingResult);
            } else if(reservationCost.getPriceWithVAT() > 0) {
                reverseChargeManager.resetVat(purchaseContext, reservationId);
            }

            //persist data
            ticketReservationManager.updateReservation(reservationId, customerName, contactAndTicketsForm.getEmail(),
                contactAndTicketsForm.getBillingAddressCompany(), contactAndTicketsForm.getBillingAddressLine1(), contactAndTicketsForm.getBillingAddressLine2(),
                contactAndTicketsForm.getBillingAddressZip(), contactAndTicketsForm.getBillingAddressCity(), contactAndTicketsForm.getBillingAddressState(), contactAndTicketsForm.getVatCountryCode(),
                contactAndTicketsForm.getCustomerReference(), contactAndTicketsForm.getVatNr(), contactAndTicketsForm.isInvoiceRequested(),
                contactAndTicketsForm.getAddCompanyBillingDetails(), contactAndTicketsForm.canSkipVatNrCheck(), false, locale, principal);

            boolean italyEInvoicing = configurationManager.getFor(ENABLE_ITALY_E_INVOICING, purchaseContext.getConfigurationLevel()).getValueAsBooleanOrDefault();

            if(italyEInvoicing) {
                ticketReservationManager.updateReservationInvoicingAdditionalInformation(reservationId, purchaseContext,
                    new TicketReservationInvoicingAdditionalInfo(getItalianInvoicingInfo(contactAndTicketsForm))
                );
            }

            if (purchaseContext.ofType(PurchaseContextType.event)) {
                var event = (Event) purchaseContext;
                if (event.supportsLinkedAdditionalServices() && contactAndTicketsForm.hasAdditionalServices()) {
                    var tickets = ticketReservationManager.findTicketsInReservation(reservationId);
                    additionalServiceManager.linkItemsToTickets(reservationId, contactAndTicketsForm.getAdditionalServices(), tickets);
                    additionalServiceManager.persistFieldsForAdditionalItems(event.getId(), event.getOrganizationId(), contactAndTicketsForm.getAdditionalServices(), tickets);
                }
                assignTickets(event.getShortName(), reservationId, contactAndTicketsForm, bindingResult, locale, true, true);
            }

            if(purchaseContext.ofType(PurchaseContextType.subscription)) {
                var owner = contactAndTicketsForm.getSubscriptionOwner();
                if (contactAndTicketsForm.isDifferentSubscriptionOwner()) {
                    Validate.isTrue(subscriptionRepository.assignSubscription(reservationId, owner.getFirstName(), owner.getLastName(), owner.getEmail()) == 1);
                } else {
                    // cleanup data
                    Validate.isTrue(subscriptionRepository.assignSubscription(reservationId, null, null, null) == 1);
                }
                if (MapUtils.isNotEmpty(owner.getAdditional())) {
                    purchaseContextFieldManager.updateFieldsForReservation(owner, purchaseContext, null, subscriptionRepository.findSubscriptionsByReservationId(reservationId).get(0).getId());
                }
            }
            //

            Map<ConfigurationKeys, Boolean> formValidationParameters = Collections.singletonMap(ENABLE_ITALY_E_INVOICING, italyEInvoicing);

            var fieldsFilterer = purchaseContext.event()
                .map(event -> bookingInfoTicketLoader.getTicketFieldsFilterer(reservationId, event))
                .or(() -> Optional.of(bookingInfoTicketLoader.getSubscriptionFieldsFilterer(reservationId, (SubscriptionDescriptor) purchaseContext)));

            //
            contactAndTicketsForm.validate(bindingResult, purchaseContext, new SameCountryValidator(configurationManager, extensionManager, purchaseContext, reservationId, vatChecker),
                formValidationParameters, fieldsFilterer, reservationCost.requiresPayment(), extensionManager,
                () -> additionalServiceManager.findItemsInReservation(purchaseContext, reservationId));
            //

            if(!bindingResult.hasErrors()) {
                extensionManager.handleReservationValidation(purchaseContext, reservation, contactAndTicketsForm, bindingResult);
            }

            if(!bindingResult.hasErrors() && (!bindingResult.hasWarnings() || ignoreWarnings)) {
                ticketReservationManager.flagAsValidated(reservationId, purchaseContext, bindingResult.getWarnings());
                // save customer data
                if(principal != null && configurationManager.isPublicOpenIdEnabled()) {
                    var additionalData = contactAndTicketsForm.getTickets().values()
                        .stream()
                        .filter(f -> principal.getName().equals(f.getEmail()) && !f.getAdditional().isEmpty())
                        .limit(1)
                        .map(form -> publicUserManager.buildAdditionalInfoWithLabels(principal, purchaseContext, form))
                        .findFirst()
                        .orElse(Map.of());

                    publicUserManager.persistProfileForPublicUser(principal,
                        contactAndTicketsForm,
                        bindingResult,
                        ticketReservationManager.loadAdditionalInfo(reservationId),
                        additionalData);
                }
            }

            var body = ValidatedResponse.toResponse(bindingResult, !bindingResult.hasErrors());
            return ResponseEntity.status(bindingResult.hasErrors() ? HttpStatus.UNPROCESSABLE_ENTITY : HttpStatus.OK).body(body);
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private void validateAccessToReservation(Principal principal, TicketReservation reservation) {
        if(!ticketReservationManager.validateAccessToReservation(reservation, principal)) {
            log.warn("Access to reservation {} has been denied to principal {}", reservation.getId(), principal);
            throw new ReservationAccessDenied();
        }
    }

    private TicketReservationInvoicingAdditionalInfo.ItalianEInvoicing getItalianInvoicingInfo(ContactAndTicketsForm contactAndTicketsForm) {
        return new TicketReservationInvoicingAdditionalInfo.ItalianEInvoicing(
            StringUtils.upperCase(contactAndTicketsForm.getItalyEInvoicingFiscalCode()),
            contactAndTicketsForm.getItalyEInvoicingReferenceType(),
            contactAndTicketsForm.getItalyEInvoicingReferenceAddresseeCode(),
            contactAndTicketsForm.getItalyEInvoicingReferencePEC(),
            contactAndTicketsForm.isItalyEInvoicingSplitPayment());
    }

    private void assignTickets(String eventName, String reservationId, ContactAndTicketsForm contactAndTicketsForm, BindingResult bindingResult, Locale locale, boolean preAssign, boolean skipValidation) {
        if(!contactAndTicketsForm.isPostponeAssignment()) {
            contactAndTicketsForm.getTickets().forEach((ticketId, owner) -> {
                if (preAssign) {
                    Optional<BindingResult> bindingResultOptional = skipValidation ? Optional.empty() : Optional.of(bindingResult);
                    ticketHelper.preAssignTicket(eventName, reservationId, ticketId, owner, bindingResultOptional, locale);
                } else {
                    ticketHelper.assignTicket(eventName, ticketId, owner, Optional.of(bindingResult), locale, Optional.empty(), true);
                }
            });
        }
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
                    validateAccessToReservation(principal, ticketReservation);
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

        Optional<ResponseEntity<TransactionInitializationToken>> responseEntity = purchaseContextManager.getReservationWithPurchaseContext(reservationId)
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

        var res = purchaseContextManager.getReservationWithPurchaseContext(reservationId).map(et -> paymentManager.removePaymentTokenReservation(et.getRight().getId())).orElse(false);
        return ResponseEntity.ok(res);
    }

    @DeleteMapping({
        "/reservation/{reservationId}/payment",
        "/event/{eventName}/reservation/{reservationId}/payment" //<-deprecated
    })
    public ResponseEntity<Boolean> deletePaymentAttempt(@PathVariable("reservationId") String reservationId) {

        var res = purchaseContextManager.getReservationWithPurchaseContext(reservationId).map(et -> ticketReservationManager.cancelPendingPayment(et.getRight().getId(), et.getLeft())).orElse(false);
        return ResponseEntity.ok(res);
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

        return purchaseContextManager.getReservationWithPurchaseContext(reservationId)
            .flatMap(pair -> paymentManager.getTransactionStatus(pair.getRight(), paymentMethod))
            .map(pr -> ResponseEntity.ok(new ReservationPaymentResult(pr.isSuccessful(), pr.isRedirect(), pr.getRedirectUrl(), pr.isFailed(), pr.getGatewayIdOrNull())))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/reservation/{reservationId}/apply-code")
    public ResponseEntity<ValidatedResponse<Boolean>> applyCode(@PathVariable("reservationId") String reservationId, @RequestBody ReservationCodeForm reservationCodeForm, BindingResult bindingResult) {
        if(reservationCodeForm.getType() != ReservationCodeForm.ReservationCodeType.SUBSCRIPTION) {
            throw new IllegalStateException(reservationCodeForm.getType() + " not supported");
        }
        boolean res = purchaseContextManager.getReservationWithPurchaseContext(reservationId)
            .map(et -> ticketReservationManager.validateAndApplySubscriptionCode(et.getLeft(),
                et.getRight(),
                reservationCodeForm.getCodeAsUUID(),
                reservationCodeForm.getCode(),
                reservationCodeForm.getEmail(),
                bindingResult))
            .orElse(false);
        return ResponseEntity.ok(ValidatedResponse.toResponse(bindingResult, res));
    }

    @DeleteMapping("/reservation/{reservationId}/remove-code")
    public ResponseEntity<Boolean> removeCode(@PathVariable("reservationId") String reservationId, @RequestParam("type") ReservationCodeForm.ReservationCodeType type) {
        boolean res = false;
        if (type == ReservationCodeForm.ReservationCodeType.SUBSCRIPTION) {
            res = purchaseContextManager.getReservationWithPurchaseContext(reservationId).map(et -> ticketReservationManager.removeSubscription(et.getRight())).orElse(false);
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

    private boolean isCaptchaInvalid(int cost, PaymentProxy paymentMethod, String recaptchaResponse, HttpServletRequest request, Configurable configurable) {
        return (cost == 0 || paymentMethod == PaymentProxy.OFFLINE || paymentMethod == PaymentProxy.ON_SITE)
            && configurationManager.isRecaptchaForOfflinePaymentAndFreeEnabled(configurable.getConfigurationLevel())
            && !recaptchaService.checkRecaptcha(recaptchaResponse, request);
    }
}
