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

import alfio.controller.api.v2.model.EventWithAdditionalInfo;
import alfio.controller.api.v2.model.*;
import alfio.controller.api.v2.user.support.EventLoader;
import alfio.controller.form.ReservationForm;
import alfio.controller.form.SearchOptions;
import alfio.controller.form.WaitingQueueSubscriptionForm;
import alfio.controller.support.Formatters;
import alfio.manager.*;
import alfio.manager.i18n.I18nManager;
import alfio.manager.i18n.MessageSourceManager;
import alfio.manager.support.response.ValidatedResponse;
import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.model.modification.TicketReservationModification;
import alfio.model.result.ValidationResult;
import alfio.model.system.ConfigurationKeys;
import alfio.repository.EventDescriptionRepository;
import alfio.repository.EventRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.TicketRepository;
import alfio.util.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
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

import java.io.IOException;
import java.security.Principal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static alfio.model.system.ConfigurationKeys.*;
import static java.util.stream.Collectors.*;


@RestController
@RequestMapping("/api/v2/public/")
public class EventApiV2Controller {

    private final EventManager eventManager;
    private final EventRepository eventRepository;
    private final ConfigurationManager configurationManager;
    private final EventDescriptionRepository eventDescriptionRepository;
    private final TicketCategoryAvailabilityManager ticketCategoryAvailabilityManager;
    private final MessageSourceManager messageSourceManager;
    private final WaitingQueueManager waitingQueueManager;
    private final I18nManager i18nManager;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final TicketRepository ticketRepository;
    private final TicketReservationManager ticketReservationManager;
    private final RecaptchaService recaptchaService;
    private final PromoCodeRequestManager promoCodeRequestManager;
    private final EventLoader eventLoader;
    private final ExtensionManager extensionManager;
    private final AdditionalServiceManager additionalServiceManager;

    public EventApiV2Controller(EventManager eventManager,
                                EventRepository eventRepository,
                                ConfigurationManager configurationManager,
                                EventDescriptionRepository eventDescriptionRepository,
                                TicketCategoryAvailabilityManager ticketCategoryAvailabilityManager,
                                MessageSourceManager messageSourceManager,
                                WaitingQueueManager waitingQueueManager,
                                I18nManager i18nManager,
                                TicketCategoryRepository ticketCategoryRepository,
                                TicketRepository ticketRepository,
                                TicketReservationManager ticketReservationManager,
                                RecaptchaService recaptchaService,
                                PromoCodeRequestManager promoCodeRequestManager,
                                EventLoader eventLoader,
                                ExtensionManager extensionManager,
                                AdditionalServiceManager additionalServiceManager) {
        this.eventManager = eventManager;
        this.eventRepository = eventRepository;
        this.configurationManager = configurationManager;
        this.eventDescriptionRepository = eventDescriptionRepository;
        this.ticketCategoryAvailabilityManager = ticketCategoryAvailabilityManager;
        this.messageSourceManager = messageSourceManager;
        this.waitingQueueManager = waitingQueueManager;
        this.i18nManager = i18nManager;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.ticketRepository = ticketRepository;
        this.ticketReservationManager = ticketReservationManager;
        this.recaptchaService = recaptchaService;
        this.promoCodeRequestManager = promoCodeRequestManager;
        this.eventLoader = eventLoader;
        this.extensionManager = extensionManager;
        this.additionalServiceManager = additionalServiceManager;
    }


    @GetMapping("events")
    public ResponseEntity<List<BasicEventInfo>> listEvents(SearchOptions searchOptions) {

        var contentLanguages = i18nManager.getAvailableLanguages();

        var events = eventManager.getPublishedEvents(searchOptions)
            .stream()
            .map(e -> {
                var messageSource = messageSourceManager.getMessageSourceFor(e);
                var formattedDates = Formatters.getFormattedDates(e, messageSource, contentLanguages);
                return new BasicEventInfo(e.getShortName(), e.getFileBlobId(), e.getTitle(), e.getFormat(), e.getLocation(),
                    e.getTimeZone(), DatesWithTimeZoneOffset.fromEvent(e), e.getSameDay(), formattedDates.beginDate, formattedDates.beginTime,
                    formattedDates.endDate, formattedDates.endTime,
                    e.getContentLanguages().stream().map(cl -> new Language(cl.locale().getLanguage(), cl.getDisplayLanguage())).collect(toList()));
            })
            .collect(Collectors.toList());
        return new ResponseEntity<>(events, getCorsHeaders(), HttpStatus.OK);
    }

    @GetMapping("event/{eventName}")
    public ResponseEntity<EventWithAdditionalInfo> getEvent(@PathVariable String eventName, HttpSession session) {
        return eventLoader.loadEventInfo(eventName, session).map(eventWithAdditionalInfo -> new ResponseEntity<>(eventWithAdditionalInfo, getCorsHeaders(), HttpStatus.OK))
            .orElseGet(() -> ResponseEntity.notFound().headers(getCorsHeaders()).build());
    }

    @PostMapping("event/{eventName}/waiting-list/subscribe")
    public ResponseEntity<ValidatedResponse<Boolean>> subscribeToWaitingList(@PathVariable String eventName,
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
    public ResponseEntity<ItemsByCategory> getTicketCategories(@PathVariable String eventName, @RequestParam(value = "code", required = false) String code) {

        //
        return ticketCategoryAvailabilityManager
            .getTicketCategories(eventName, code).map(i -> new ResponseEntity<>(i, getCorsHeaders(), HttpStatus.OK))
            .orElseGet(() -> ResponseEntity.notFound().headers(getCorsHeaders()).build());
    }


    @GetMapping("event/{eventName}/calendar/{locale}")
    public void getCalendar(@PathVariable String eventName,
                            @PathVariable String locale,
                            @RequestParam(value = "type", required = false) String calendarType,
                            @RequestParam(value = "ticketId", required = false) String ticketId,
                            HttpServletResponse response) {

        eventRepository.findOptionalByShortName(eventName).ifPresentOrElse(ev -> {
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
        }, () -> response.setStatus(HttpServletResponse.SC_NOT_FOUND));
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
    public ResponseEntity<ValidatedResponse<String>> reserveTickets(@PathVariable String eventName,
                                                                    @RequestParam("lang") String lang,
                                                                    @RequestBody ReservationForm reservation,
                                                                    BindingResult bindingResult,
                                                                    ServletWebRequest request,
                                                                    Principal principal) {



        Optional<ResponseEntity<ValidatedResponse<String>>> r = eventRepository.findOptionalByShortName(eventName).map(event -> {

            Locale locale = LocaleUtil.forLanguageTag(lang, event);

            Optional<String> promoCodeDiscount = ReservationUtil.checkPromoCode(reservation, event, promoCodeRequestManager, bindingResult);
            var configurationValues = configurationManager.getFor(List.of(
                ENABLE_CAPTCHA_FOR_TICKET_SELECTION,
                RECAPTCHA_API_KEY), event.getConfigurationLevel());

            if (isCaptchaInvalid(reservation.getCaptcha(), request.getRequest(), configurationValues)) {
                bindingResult.reject(ErrorsCode.STEP_2_CAPTCHA_VALIDATION_FAILED);
            }

            Optional<String> reservationIdRes = createTicketReservation(reservation, bindingResult, event, locale, promoCodeDiscount, principal);

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
                                                     Optional<String> promoCodeDiscount,
                                                     Principal principal) {
        return ReservationUtil.validateCreateRequest(reservation, bindingResult, ticketReservationManager, eventManager, additionalServiceManager, promoCodeDiscount.orElse(null), event)
            .flatMap(selected -> ReservationUtil.handleReservationCreationErrors(() -> ticketReservationManager.createTicketReservation(event, selected.getLeft(), selected.getRight(), promoCodeDiscount, locale, principal), bindingResult, event.getType()));
    }

    @GetMapping("event/{eventName}/validate-code")
    public ResponseEntity<ValidatedResponse<EventCode>> validateCode(@PathVariable String eventName,
                                                                     @RequestParam("code") String code) {

        var res = promoCodeRequestManager.checkCode(eventName, code);
        if(res.isSuccess()) {
            var value = res.getValue();
            var eventCode = value.getLeft()
                .map(sp -> new EventCode(code, EventCode.EventCodeType.SPECIAL_PRICE, PromoCodeDiscount.DiscountType.NONE, null))
                .orElseGet(() -> {
                    var promoCodeDiscount = value.getRight().orElseThrow();
                    var type = promoCodeDiscount.getCodeType() == PromoCodeDiscount.CodeType.ACCESS ? EventCode.EventCodeType.ACCESS : EventCode.EventCodeType.DISCOUNT;
                    String formattedDiscountAmount = PromoCodeDiscount.format(promoCodeDiscount, value.getMiddle().getCurrency());
                    return new EventCode(code, type, promoCodeDiscount.getDiscountType(), formattedDiscountAmount);
                });

            return ResponseEntity.ok(res.withValue(eventCode));
        } else {
            return new ResponseEntity<>(res.withValue(new EventCode(code,null, null, null)), HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    @PostMapping("event/{eventName}/check-discount")
    public ResponseEntity<DynamicDiscount> checkDiscount(@PathVariable String eventName, @RequestBody ReservationForm reservation) {
        return eventRepository.findOptionalByShortName(eventName)
            .flatMap(event -> {
                Map<Integer, Long> quantityByCategory = reservation.getReservation().stream()
                    .filter(trm -> trm.getQuantity() > 0)
                    .collect(groupingBy(TicketReservationModification::getTicketCategoryId, summingLong(TicketReservationModification::getQuantity)));
                if(quantityByCategory.isEmpty() || ticketCategoryRepository.countPaidCategoriesInReservation(quantityByCategory.keySet()) == 0) {
                    return Optional.empty();
                }
                return extensionManager.handleDynamicDiscount(event, quantityByCategory, null)
                    .filter(d -> d.getDiscountType() != PromoCodeDiscount.DiscountType.NONE)
                    .map(d -> {
                        String formattedDiscount;
                        if(d.getDiscountType() == PromoCodeDiscount.DiscountType.PERCENTAGE) {
                           formattedDiscount = String.valueOf(d.getDiscountAmount());
                        } else {
                            formattedDiscount = MonetaryUtil.formatCents(d.getDiscountAmount(), event.getCurrency());
                        }
                        return new DynamicDiscount(formattedDiscount, d.getDiscountType(), formatDynamicCodeMessage(event, d));
                    });
            })
            .filter(d -> d.getDiscountType() != PromoCodeDiscount.DiscountType.NONE)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("event/{eventName}/code/{code}")
    public ResponseEntity<Void> handleCode(@PathVariable String eventName, @PathVariable String code, ServletWebRequest request, Principal principal) {
        String trimmedCode = StringUtils.trimToNull(code);
        Map<String, String> queryStrings = new HashMap<>();

        Function<Pair<Optional<String>, BindingResult>, Optional<String>> handleErrors = (res) -> {
            if (res.getRight().hasErrors()) {
                queryStrings.put("errors", res.getRight().getAllErrors().stream().map(DefaultMessageSourceResolvable::getCode).collect(Collectors.joining(",")));
            }
            return res.getLeft();
        };

        var url = promoCodeRequestManager.createReservationFromPromoCode(eventName, trimmedCode, queryStrings::put, handleErrors, request, principal).map(reservationId ->
            UriComponentsBuilder.fromPath("/event/{eventShortName}/reservation/{reservationId}/book")
                .build(Map.of("eventShortName", eventName, "reservationId", reservationId))
                .toString())
            .orElseGet(() -> {
                    var backToEvent = UriComponentsBuilder.fromPath("/event/{eventShortName}");
                    queryStrings.forEach(backToEvent::queryParam);
                    return backToEvent.build(Map.of("eventShortName", eventName)).toString();
                }
            );
        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT).header(HttpHeaders.LOCATION, url).build();
    }

    private static HttpHeaders getCorsHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Access-Control-Allow-Origin", "*");
        return headers;
    }


    private boolean isCaptchaInvalid(String recaptchaResponse, HttpServletRequest request, Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> configurationValues) {
        return eventLoader.isRecaptchaForTicketSelectionEnabled(configurationValues)
            && !recaptchaService.checkRecaptcha(recaptchaResponse, request);
    }

    private Map<String, String> formatDynamicCodeMessage(Event event, PromoCodeDiscount promoCodeDiscount) {
        Validate.isTrue(promoCodeDiscount != null && promoCodeDiscount.getDiscountType() != PromoCodeDiscount.DiscountType.NONE);
        var messageSource = messageSourceManager.getMessageSourceFor(event);
        Map<String, String> res = new HashMap<>();
        String code;
        String amount;
        switch(promoCodeDiscount.getDiscountType()) {
            case PERCENTAGE:
                code = "reservation.dynamic.discount.confirmation.percentage.message";
                amount = String.valueOf(promoCodeDiscount.getDiscountAmount());
                break;
            case FIXED_AMOUNT:
                amount = event.getCurrency() + " " + MonetaryUtil.formatCents(promoCodeDiscount.getDiscountAmount(), event.getCurrency());
                code = "reservation.dynamic.discount.confirmation.fix-per-ticket.message";
                break;
            case FIXED_AMOUNT_RESERVATION:
                amount = event.getCurrency() + " " + MonetaryUtil.formatCents(promoCodeDiscount.getDiscountAmount(), event.getCurrency());
                code = "reservation.dynamic.discount.confirmation.fix-per-reservation.message";
                break;
            default:
                throw new IllegalStateException("Unexpected discount code type");
        }

        for (ContentLanguage cl : event.getContentLanguages()) {
            res.put(cl.locale().getLanguage(), messageSource.getMessage(code, new Object[]{amount}, cl.locale()));
        }
        return res;
    }


}
