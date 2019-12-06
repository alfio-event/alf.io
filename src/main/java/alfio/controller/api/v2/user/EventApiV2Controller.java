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

import alfio.controller.api.v2.model.AdditionalService;
import alfio.controller.api.v2.model.EventWithAdditionalInfo;
import alfio.controller.api.v2.model.TicketCategory;
import alfio.controller.api.v2.model.*;
import alfio.controller.decorator.SaleableAdditionalService;
import alfio.controller.decorator.SaleableTicketCategory;
import alfio.controller.form.ReservationForm;
import alfio.controller.form.WaitingQueueSubscriptionForm;
import alfio.controller.support.Formatters;
import alfio.manager.*;
import alfio.manager.i18n.I18nManager;
import alfio.manager.i18n.MessageSourceManager;
import alfio.manager.support.response.ValidatedResponse;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.model.modification.support.LocationDescriptor;
import alfio.model.result.ValidationResult;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentProxy;
import alfio.repository.*;
import alfio.repository.user.OrganizationRepository;
import alfio.util.*;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static alfio.model.PromoCodeDiscount.categoriesOrNull;
import static alfio.model.system.ConfigurationKeys.*;
import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Collectors.toList;


@RestController
@RequestMapping("/api/v2/public/")
@AllArgsConstructor
public class EventApiV2Controller {

    private final EventManager eventManager;
    private final EventRepository eventRepository;
    private final ConfigurationManager configurationManager;
    private final OrganizationRepository organizationRepository;
    private final EventDescriptionRepository eventDescriptionRepository;
    private final TicketCategoryDescriptionRepository ticketCategoryDescriptionRepository;
    private final PaymentManager paymentManager;
    private final MessageSourceManager messageSourceManager;
    private final AdditionalServiceRepository additionalServiceRepository;
    private final AdditionalServiceTextRepository additionalServiceTextRepository;
    private final WaitingQueueManager waitingQueueManager;
    private final I18nManager i18nManager;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final TicketRepository ticketRepository;
    private final TicketReservationManager ticketReservationManager;
    private final PromoCodeDiscountRepository promoCodeRepository;
    private final EventStatisticsManager eventStatisticsManager;
    private final RecaptchaService recaptchaService;
    private final PromoCodeRequestManager promoCodeRequestManager;


    @GetMapping("events")
    public ResponseEntity<List<BasicEventInfo>> listEvents() {

        var contentLanguages = i18nManager.getSupportedLanguages();

        var events = eventManager.getPublishedEvents()
            .stream()
            .map(e -> {
                var messageSource = messageSourceManager.getMessageSourceForEvent(e);
                var formattedDates = Formatters.getFormattedDates(e, messageSource, contentLanguages);
                return new BasicEventInfo(e.getShortName(), e.getFileBlobId(), e.getDisplayName(), e.getLocation(),
                    e.getTimeZone(), DatesWithTimeZoneOffset.fromEvent(e), e.getSameDay(), formattedDates.beginDate, formattedDates.beginTime,
                    formattedDates.endDate, formattedDates.endTime);
            })
            .collect(Collectors.toList());
        return new ResponseEntity<>(events, getCorsHeaders(), HttpStatus.OK);
    }

    @GetMapping("event/{eventName}")
    public ResponseEntity<EventWithAdditionalInfo> getEvent(@PathVariable("eventName") String eventName, HttpSession session) {
        return eventRepository.findOptionalByShortName(eventName).filter(e -> e.getStatus() != Event.Status.DISABLED)//
            .map(event -> {
                //
                var messageSourceAndOverride = messageSourceManager.getMessageSourceForEventAndOverride(event);
                var messageSource = messageSourceAndOverride.getLeft();
                var i18nOverride = messageSourceAndOverride.getRight();

                var descriptions = applyCommonMark(eventDescriptionRepository.findDescriptionByEventIdAsMap(event.getId()));

                var organization = organizationRepository.getContactById(event.getOrganizationId());

                var configurationsValues = configurationManager.getFor(List.of(
                    MAPS_PROVIDER,
                    MAPS_CLIENT_API_KEY,
                    MAPS_HERE_APP_ID,
                    MAPS_HERE_APP_CODE,
                    RECAPTCHA_API_KEY,
                    BANK_ACCOUNT_NR,
                    BANK_ACCOUNT_OWNER,
                    ENABLE_CUSTOMER_REFERENCE,
                    ENABLE_ITALY_E_INVOICING,
                    VAT_NUMBER_IS_REQUIRED,
                    FORCE_TICKET_OWNER_ASSIGNMENT_AT_RESERVATION,
                    ENABLE_ATTENDEE_AUTOCOMPLETE,
                    ENABLE_TICKET_TRANSFER,
                    DISPLAY_DISCOUNT_CODE_BOX,
                    USE_PARTNER_CODE_INSTEAD_OF_PROMOTIONAL,
                    GOOGLE_ANALYTICS_KEY,
                    GOOGLE_ANALYTICS_ANONYMOUS_MODE,
                    // captcha
                    ENABLE_CAPTCHA_FOR_TICKET_SELECTION,
                    RECAPTCHA_API_KEY,
                    ENABLE_CAPTCHA_FOR_OFFLINE_PAYMENTS,
                    //
                    GENERATE_ONLY_INVOICE,
                    //
                    INVOICE_ADDRESS,
                    VAT_NR,
                    // required by EuVatChecker.reverseChargeEnabled
                    ENABLE_EU_VAT_DIRECTIVE,
                    COUNTRY_OF_BUSINESS,

                    DISPLAY_TICKETS_LEFT_INDICATOR
                ), ConfigurationLevel.event(event));

                var locationDescriptor = LocationDescriptor.fromGeoData(event.getLatLong(), TimeZone.getTimeZone(event.getTimeZone()), configurationsValues);

                //
                boolean captchaForTicketSelection = isRecaptchaForTicketSelectionEnabled(configurationsValues);
                String recaptchaApiKey = null;
                if (captchaForTicketSelection) {
                    recaptchaApiKey = configurationsValues.get(RECAPTCHA_API_KEY).getValueOrDefault(null);
                }
                //
                boolean captchaForOfflinePaymentAndFreeEnabled = configurationManager.isRecaptchaForOfflinePaymentAndFreeEnabled(configurationsValues);
                var captchaConf = new EventWithAdditionalInfo.CaptchaConfiguration(captchaForTicketSelection, captchaForOfflinePaymentAndFreeEnabled, recaptchaApiKey);


                //
                String bankAccount = configurationsValues.get(BANK_ACCOUNT_NR).getValueOrDefault("");
                List<String> bankAccountOwner = Arrays.asList(configurationsValues.get(BANK_ACCOUNT_OWNER).getValueOrDefault("").split("\n"));
                //

                var formattedDates = Formatters.getFormattedDates(event, messageSource, event.getContentLanguages());

                //invoicing information
                boolean canGenerateReceiptOrInvoiceToCustomer = configurationManager.canGenerateReceiptOrInvoiceToCustomer(configurationsValues);
                boolean euVatCheckingEnabled = EuVatChecker.reverseChargeEnabled(configurationsValues);
                boolean invoiceAllowed = configurationManager.hasAllConfigurationsForInvoice(configurationsValues);
                boolean onlyInvoice = invoiceAllowed && configurationManager.isInvoiceOnly(configurationsValues);
                boolean customerReferenceEnabled = configurationsValues.get(ENABLE_CUSTOMER_REFERENCE).getValueAsBooleanOrDefault(false);
                boolean enabledItalyEInvoicing = configurationsValues.get(ENABLE_ITALY_E_INVOICING).getValueAsBooleanOrDefault(false);
                boolean vatNumberStrictlyRequired = configurationsValues.get(VAT_NUMBER_IS_REQUIRED).getValueAsBooleanOrDefault(false);

                var invoicingConf = new EventWithAdditionalInfo.InvoicingConfiguration(canGenerateReceiptOrInvoiceToCustomer,
                    euVatCheckingEnabled, invoiceAllowed, onlyInvoice,
                    customerReferenceEnabled, enabledItalyEInvoicing, vatNumberStrictlyRequired);
                //

                //
                boolean forceAssignment = configurationsValues.get(FORCE_TICKET_OWNER_ASSIGNMENT_AT_RESERVATION).getValueAsBooleanOrDefault(true);
                boolean enableAttendeeAutocomplete = configurationsValues.get(ENABLE_ATTENDEE_AUTOCOMPLETE).getValueAsBooleanOrDefault(true);
                boolean enableTicketTransfer = configurationsValues.get(ENABLE_TICKET_TRANSFER).getValueAsBooleanOrDefault(true);
                var assignmentConf = new EventWithAdditionalInfo.AssignmentConfiguration(forceAssignment, enableAttendeeAutocomplete, enableTicketTransfer);
                //


                //promotion codes
                boolean hasAccessPromotions = configurationsValues.get(DISPLAY_DISCOUNT_CODE_BOX).getValueAsBooleanOrDefault(true) &&
                    (ticketCategoryRepository.countAccessRestrictedRepositoryByEventId(event.getId()) > 0 ||
                        promoCodeRepository.countByEventAndOrganizationId(event.getId(), event.getOrganizationId()) > 0);
                boolean usePartnerCode = configurationsValues.get(USE_PARTNER_CODE_INSTEAD_OF_PROMOTIONAL).getValueAsBooleanOrDefault(false);
                var promoConf = new EventWithAdditionalInfo.PromotionsConfiguration(hasAccessPromotions, usePartnerCode);
                //

                //analytics configuration
                var analyticsConf = AnalyticsConfiguration.build(configurationsValues, session);
                //

                Integer availableTicketsCount = null;
                if(configurationsValues.get(DISPLAY_TICKETS_LEFT_INDICATOR).getValueAsBooleanOrDefault(false)) {
                    availableTicketsCount = ticketRepository.countFreeTicketsForPublicStatistics(event.getId());
                }

                return new ResponseEntity<>(new EventWithAdditionalInfo(event, locationDescriptor.getMapUrl(), organization, descriptions,
                    bankAccount, bankAccountOwner,
                    formattedDates.beginDate, formattedDates.beginTime,
                    formattedDates.endDate, formattedDates.endTime,
                    invoicingConf, captchaConf, assignmentConf, promoConf, analyticsConf,
                    i18nOverride, availableTicketsCount), getCorsHeaders(), HttpStatus.OK);
            })
            .orElseGet(() -> ResponseEntity.notFound().headers(getCorsHeaders()).build());
    }

    private List<PaymentProxy> getActivePaymentMethods(Event event) {
        if(!event.isFreeOfCharge()) {
            return paymentManager.getPaymentMethods(event)
                .stream()
                .filter(p -> TicketReservationManager.isValidPaymentMethod(p, event, configurationManager))
                .map(PaymentManager.PaymentMethodDTO::getPaymentProxy)
                .collect(toList());
        } else {
            return Collections.emptyList();
        }
    }

    private static Map<String, String> applyCommonMark(Map<String, String> in) {
        if (in == null) {
            return Collections.emptyMap();
        }

        var res = new HashMap<String, String>();
        in.forEach((k, v) -> {
            res.put(k, MustacheCustomTag.renderToHtmlCommonmarkEscaped(v));
        });
        return res;
    }

    @PostMapping("event/{eventName}/waiting-list/subscribe")
    public ResponseEntity<ValidatedResponse<Boolean>> subscribeToWaitingList(@PathVariable("eventName") String eventName,
                                                                             @RequestBody WaitingQueueSubscriptionForm subscription,
                                                                             BindingResult bindingResult) {

        Optional<ResponseEntity<ValidatedResponse<Boolean>>> res = eventRepository.findOptionalByShortName(eventName).map(event -> {
            Validator.validateWaitingQueueSubscription(subscription, bindingResult, event);
            if (bindingResult.hasErrors()) {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ValidatedResponse.toResponse(bindingResult, null));
            } else {
                var subscriptionResult = waitingQueueManager.subscribe(event, subscription.toCustomerName(event), subscription.getEmail(), subscription.getSelectedCategory(), subscription.getUserLanguage());
                return ResponseEntity.ok(new ValidatedResponse<>(ValidationResult.success(), subscriptionResult));
            }
        });

        return res.orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("event/{eventName}/ticket-categories")
    public ResponseEntity<ItemsByCategory> getTicketCategories(@PathVariable("eventName") String eventName, @RequestParam(value = "code", required = false) String code) {

        //
        return eventRepository.findOptionalByShortName(eventName).filter(e -> e.getStatus() != Event.Status.DISABLED).map(event -> {

            var configurations = configurationManager.getFor(List.of(DISPLAY_TICKETS_LEFT_INDICATOR, MAX_AMOUNT_OF_TICKETS_BY_RESERVATION, DISPLAY_EXPIRED_CATEGORIES), ConfigurationLevel.event(event));
            var ticketCategoryLevelConfiguration = configurationManager.getAllCategoriesAndValueWith(event, MAX_AMOUNT_OF_TICKETS_BY_RESERVATION);
            var messageSource = messageSourceManager.getMessageSourceForEvent(event);
            var appliedPromoCode = promoCodeRequestManager.checkCode(event, code);


            Optional<SpecialPrice> specialCode = appliedPromoCode.getValue().getLeft();
            Optional<PromoCodeDiscount> promoCodeDiscount = appliedPromoCode.getValue().getRight();

            final ZonedDateTime now = ZonedDateTime.now(event.getZoneId());
            //hide access restricted ticket categories
            var ticketCategories = ticketCategoryRepository.findAllTicketCategories(event.getId());

            List<SaleableTicketCategory> saleableTicketCategories = ticketCategories.stream()
                .filter((c) -> !c.isAccessRestricted() || shouldDisplayRestrictedCategory(specialCode, c, promoCodeDiscount))
                .map((m) -> {
                    int maxTickets = getMaxAmountOfTicketsPerReservation(configurations, ticketCategoryLevelConfiguration, m.getId());
                    PromoCodeDiscount filteredPromoCode = promoCodeDiscount.filter(promoCode -> shouldApplyDiscount(promoCode, m)).orElse(null);
                    if (specialCode.isPresent()) {
                        maxTickets = Math.min(1, maxTickets);
                    } else if (filteredPromoCode != null && filteredPromoCode.getMaxUsage() != null) {
                        maxTickets = filteredPromoCode.getMaxUsage() - promoCodeRepository.countConfirmedPromoCode(filteredPromoCode.getId(), categoriesOrNull(filteredPromoCode), null, categoriesOrNull(filteredPromoCode) != null ? "X" : null);
                    }
                    return new SaleableTicketCategory(m,
                        now, event, ticketReservationManager.countAvailableTickets(event, m), maxTickets,
                        filteredPromoCode);
                })
                .collect(Collectors.toList());


            var valid = saleableTicketCategories.stream().filter(tc -> !tc.getExpired()).collect(Collectors.toList());

            //

            var ticketCategoryIds = valid.stream().map(SaleableTicketCategory::getId).collect(Collectors.toList());
            var ticketCategoryDescriptions = ticketCategoryDescriptionRepository.descriptionsByTicketCategory(ticketCategoryIds);

            boolean displayTicketsLeft = configurations.get(DISPLAY_TICKETS_LEFT_INDICATOR).getValueAsBooleanOrDefault(false);
            var categoriesByExpiredFlag = saleableTicketCategories.stream()
                .map(stc -> {
                    var description = applyCommonMark(ticketCategoryDescriptions.getOrDefault(stc.getId(), Collections.emptyMap()));
                    var expiration = Formatters.getFormattedDate(event, stc.getZonedExpiration(), "common.ticket-category.date-format", messageSource);
                    var inception = Formatters.getFormattedDate(event, stc.getZonedInception(), "common.ticket-category.date-format", messageSource);
                    return new TicketCategory(stc, description, inception, expiration, displayTicketsLeft && !stc.isAccessRestricted());
                })
                .sorted(Comparator.comparingInt(TicketCategory::getOrdinal))
                .collect(partitioningBy(TicketCategory::isExpired));


            var promoCode = Optional.of(appliedPromoCode).filter(ValidatedResponse::isSuccess)
                .map(ValidatedResponse::getValue)
                .flatMap(Pair::getRight);

            //
            var saleableAdditionalServices = additionalServiceRepository.loadAllForEvent(event.getId())
                .stream()
                .map(as -> new SaleableAdditionalService(event, as, promoCode.orElse(null)))
                .filter(SaleableAdditionalService::isNotExpired)
                .collect(Collectors.toList());

            // will be used for fetching descriptions and titles for all the languages
            var saleableAdditionalServicesIds = saleableAdditionalServices.stream().map(SaleableAdditionalService::getId).collect(Collectors.toList());

            var additionalServiceTexts = additionalServiceTextRepository.getDescriptionsByAdditionalServiceIds(saleableAdditionalServicesIds);

            var additionalServicesRes = saleableAdditionalServices.stream().map(as -> {
                var expiration = Formatters.getFormattedDate(event, as.getZonedExpiration(), "common.ticket-category.date-format", messageSource);
                var inception = Formatters.getFormattedDate(event, as.getZonedInception(), "common.ticket-category.date-format", messageSource);
                var title = additionalServiceTexts.getOrDefault(as.getId(), Collections.emptyMap()).getOrDefault(AdditionalServiceText.TextType.TITLE, Collections.emptyMap());
                var description = applyCommonMark(additionalServiceTexts.getOrDefault(as.getId(), Collections.emptyMap()).getOrDefault(AdditionalServiceText.TextType.DESCRIPTION, Collections.emptyMap()));
                return new AdditionalService(as.getId(), as.getType(), as.getSupplementPolicy(),
                    as.isFixPrice(), as.getAvailableQuantity(), as.getMaxQtyPerOrder(),
                    as.getFree(), as.getFormattedFinalPrice(), as.getSupportsDiscount(), as.getDiscountedPrice(), as.getVatApplies(), as.getVatIncluded(), as.getVatPercentage().toString(),
                    as.isExpired(), as.getSaleInFuture(),
                    inception, expiration, title, description);
            }).collect(Collectors.toList());
            //

            // waiting queue parameters
            boolean displayWaitingQueueForm = EventUtil.displayWaitingQueueForm(event, saleableTicketCategories, configurationManager, eventStatisticsManager.noSeatsAvailable());
            boolean preSales = EventUtil.isPreSales(event, saleableTicketCategories);
            Predicate<SaleableTicketCategory> waitingQueueTargetCategory = tc -> !tc.getExpired() && !tc.isBounded();
            List<SaleableTicketCategory> unboundedCategories = saleableTicketCategories.stream().filter(waitingQueueTargetCategory).collect(Collectors.toList());
            var tcForWaitingList = unboundedCategories.stream().map(stc -> new ItemsByCategory.TicketCategoryForWaitingList(stc.getId(), stc.getName())).collect(toList());
            //
            var activeCategories = categoriesByExpiredFlag.get(false);
            var expiredCategories = configurations.get(DISPLAY_EXPIRED_CATEGORIES).getValueAsBooleanOrDefault(true) ? categoriesByExpiredFlag.get(true) : List.<TicketCategory>of();

            return new ResponseEntity<>(new ItemsByCategory(activeCategories, expiredCategories, additionalServicesRes, displayWaitingQueueForm, preSales, tcForWaitingList), getCorsHeaders(), HttpStatus.OK);
        }).orElseGet(() -> ResponseEntity.notFound().headers(getCorsHeaders()).build());
    }

    private static int getMaxAmountOfTicketsPerReservation(Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> eventLevelConf,
                                                           Map<Integer, String> ticketCategoryLevelConf,
                                                           int ticketCategory) {

        if (ticketCategoryLevelConf.containsKey(ticketCategory)) {
            return Integer.parseInt(ticketCategoryLevelConf.get(ticketCategory));
        }
        return eventLevelConf.get(MAX_AMOUNT_OF_TICKETS_BY_RESERVATION).getValueAsIntOrDefault(5);
    }

    @GetMapping("event/{eventName}/calendar/{locale}")
    public void getCalendar(@PathVariable("eventName") String eventName,
                            @PathVariable("locale") String locale,
                            @RequestParam(value = "type", required = false) String calendarType,
                            @RequestParam(value = "ticketId", required = false) String ticketId,
                            HttpServletResponse response) {

        eventRepository.findOptionalByShortName(eventName).ifPresentOrElse((ev -> {
            var description = eventDescriptionRepository.findDescriptionByEventIdTypeAndLocale(ev.getId(), EventDescription.EventDescriptionType.DESCRIPTION, locale).orElse("");
            var category = ticketRepository.findOptionalByUUID(ticketId).map(t -> ticketCategoryRepository.getById(t.getCategoryId())).orElse(null);
            if ("google".equals(calendarType)) {
                try {
                    response.sendRedirect(EventUtil.getGoogleCalendarURL(ev, category, description));
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            } else {
                EventUtil.getIcalForEvent(ev, category, description).ifPresentOrElse(ical -> {
                    response.setContentType("text/calendar");
                    response.setHeader("Content-Disposition", "inline; filename=\"calendar.ics\"");
                    try (var os = response.getOutputStream()){
                        os.write(ical);
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                }, () -> {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                });
            }
        }), () -> response.setStatus(HttpServletResponse.SC_NOT_FOUND));
    }

    /**
     * Create a new reservation.
     *
     * @param eventName
     * @param lang
     * @param reservation
     * @param bindingResult
     * @param request
     * @return
     */
    @PostMapping(value = "event/{eventName}/reserve-tickets")
    public ResponseEntity<ValidatedResponse<String>> reserveTickets(@PathVariable("eventName") String eventName,
                                                                    @RequestParam("lang") String lang,
                                                                    @RequestBody ReservationForm reservation,
                                                                    BindingResult bindingResult,
                                                                    ServletWebRequest request) {



        Optional<ResponseEntity<ValidatedResponse<String>>> r = eventRepository.findOptionalByShortName(eventName).map(event -> {

            Locale locale = LocaleUtil.forLanguageTag(lang, event);

            Optional<ValidatedResponse<Pair<Optional<SpecialPrice>, Optional<PromoCodeDiscount>>>> codeCheck = Optional.empty();

            if(StringUtils.trimToNull(reservation.getPromoCode()) != null) {
                var resCheck = promoCodeRequestManager.checkCode(event, reservation.getPromoCode());
                if(!resCheck.isSuccess()) {
                    bindingResult.reject(ErrorsCode.STEP_1_CODE_NOT_FOUND, ErrorsCode.STEP_1_CODE_NOT_FOUND);
                }
                codeCheck = Optional.of(resCheck);
            }

            Optional<String> promoCodeDiscount = codeCheck.map(ValidatedResponse::getValue).flatMap(Pair::getRight).map(PromoCodeDiscount::getPromoCode);
            var configurationValues = configurationManager.getFor(List.of(
                ENABLE_CAPTCHA_FOR_TICKET_SELECTION,
                RECAPTCHA_API_KEY), ConfigurationLevel.event(event));

            if (isCaptchaInvalid(reservation.getCaptcha(), request.getRequest(), configurationValues)) {
                bindingResult.reject(ErrorsCode.STEP_2_CAPTCHA_VALIDATION_FAILED);
            }

            Optional<String> reservationIdRes = createTicketReservation(reservation, bindingResult, event, locale, promoCodeDiscount);

            if (bindingResult.hasErrors()) {
                return new ResponseEntity<>(ValidatedResponse.toResponse(bindingResult, null), getCorsHeaders(), HttpStatus.UNPROCESSABLE_ENTITY);
            } else {
                var reservationIdentifier = reservationIdRes.orElseThrow(IllegalStateException::new);
                return ResponseEntity.ok(new ValidatedResponse<>(ValidationResult.success(), reservationIdentifier));
            }
        });

        return r.orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Optional<String> createTicketReservation(ReservationForm reservation,
                                                     BindingResult bindingResult,
                                                     Event event,
                                                     Locale locale,
                                                     Optional<String> promoCodeDiscount) {
        return reservation.validate(bindingResult, ticketReservationManager, eventManager, promoCodeDiscount.orElse(null), event)
            .flatMap(selected -> ticketReservationManager.createTicketReservation(event, selected.getLeft(), selected.getRight(), promoCodeDiscount, locale, bindingResult));
    }

    @GetMapping("event/{eventName}/validate-code")
    public ResponseEntity<ValidatedResponse<EventCode>> validateCode(@PathVariable("eventName") String eventName,
                                                                     @RequestParam("code") String code) {

        var res = promoCodeRequestManager.checkCode(eventName, code);
        if(res.isSuccess()) {
            var value = res.getValue();
            var eventCode = value.getLeft()
                .map(sp -> new EventCode(code, EventCode.EventCodeType.SPECIAL_PRICE, PromoCodeDiscount.DiscountType.NONE, null))
                .orElseGet(() -> {
                    var promoCodeDiscount = value.getRight().orElseThrow();
                    var type = promoCodeDiscount.getCodeType() == PromoCodeDiscount.CodeType.ACCESS ? EventCode.EventCodeType.ACCESS : EventCode.EventCodeType.DISCOUNT;
                    String formattedDiscountAmount =  promoCodeDiscount.getDiscountType() == PromoCodeDiscount.DiscountType.FIXED_AMOUNT ? MonetaryUtil.formatCents(promoCodeDiscount.getDiscountAmount(), value.getMiddle().getCurrency()) : Integer.toString(promoCodeDiscount.getDiscountAmount());
                    return new EventCode(code, type, promoCodeDiscount.getDiscountType(), formattedDiscountAmount);
                });

            return ResponseEntity.ok(res.withValue(eventCode));
        } else {
            return new ResponseEntity<>(res.withValue(new EventCode(code,null, null, null)), HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }


    @GetMapping("event/{eventName}/code/{code}")
    public ResponseEntity<Void> handleCode(@PathVariable("eventName") String eventName, @PathVariable("code") String code, ServletWebRequest request) {
        String trimmedCode = StringUtils.trimToNull(code);
        Map<String, String> queryStrings = new HashMap<>();

        Function<Pair<Optional<String>, BindingResult>, Optional<String>> handleErrors = (res) -> {
            if (res.getRight().hasErrors()) {
                queryStrings.put("errors", res.getRight().getAllErrors().stream().map(DefaultMessageSourceResolvable::getCode).collect(Collectors.joining(",")));
            }
            return res.getLeft();
        };

        var url = promoCodeRequestManager.createReservationFromPromoCode(eventName, trimmedCode, queryStrings::put, handleErrors, request).map(reservationId ->
            UriComponentsBuilder.fromPath("/event/{eventShortName}/reservation/{reservationId}")
                .build(Map.of("eventShortName", eventName, "reservationId", reservationId))
                .toString())
            .orElseGet(() -> {
                    var backToEvent = UriComponentsBuilder.fromPath("/event/{eventShortName}");
                    queryStrings.forEach(backToEvent::queryParam);
                    return backToEvent.build(Map.of("eventShortName", eventName)).toString();
                }
            );
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY).header(HttpHeaders.LOCATION, url).build();
    }

    private boolean shouldDisplayRestrictedCategory(Optional<SpecialPrice> specialCode, alfio.model.TicketCategory c, Optional<PromoCodeDiscount> optionalPromoCode) {
        if(optionalPromoCode.isPresent()) {
            var promoCode = optionalPromoCode.get();
            if(promoCode.getCodeType() == PromoCodeDiscount.CodeType.ACCESS && c.getId() == promoCode.getHiddenCategoryId()) {
                return true;
            }
        }
        return specialCode.filter(sc -> sc.getTicketCategoryId() == c.getId()).isPresent();
    }

    private static boolean shouldApplyDiscount(PromoCodeDiscount promoCodeDiscount, alfio.model.TicketCategory ticketCategory) {
        if(promoCodeDiscount.getCodeType() == PromoCodeDiscount.CodeType.DISCOUNT) {
            return promoCodeDiscount.getCategories().isEmpty() || promoCodeDiscount.getCategories().contains(ticketCategory.getId());
        }
        return ticketCategory.isAccessRestricted() && ticketCategory.getId() == promoCodeDiscount.getHiddenCategoryId();
    }

    private static HttpHeaders getCorsHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Access-Control-Allow-Origin", "*");
        return headers;
    }

    private boolean isRecaptchaForTicketSelectionEnabled(Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> configurationValues) {
        Validate.isTrue(configurationValues.containsKey(ENABLE_CAPTCHA_FOR_TICKET_SELECTION) && configurationValues.containsKey(RECAPTCHA_API_KEY));
        return configurationValues.get(ENABLE_CAPTCHA_FOR_TICKET_SELECTION).getValueAsBooleanOrDefault(false) &&
            configurationValues.get(RECAPTCHA_API_KEY).getValueOrDefault(null) != null;
    }

    private boolean isCaptchaInvalid(String recaptchaResponse, HttpServletRequest request, Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> configurationValues) {
        return isRecaptchaForTicketSelectionEnabled(configurationValues)
            && !recaptchaService.checkRecaptcha(recaptchaResponse, request);
    }


}
