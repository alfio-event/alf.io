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


import alfio.controller.decorator.EventDescriptor;
import alfio.controller.decorator.SaleableAdditionalService;
import alfio.controller.decorator.SaleableTicketCategory;
import alfio.controller.form.ReservationForm;
import alfio.controller.support.SessionUtil;
import alfio.manager.EventManager;
import alfio.manager.EventStatisticsManager;
import alfio.manager.RecaptchaService;
import alfio.manager.TicketReservationManager;
import alfio.manager.i18n.I18nManager;
import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.model.modification.TicketReservationModification;
import alfio.model.modification.support.LocationDescriptor;
import alfio.model.result.ValidationResult;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.repository.*;
import alfio.repository.user.OrganizationRepository;
import alfio.util.ErrorsCode;
import alfio.util.EventUtil;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static alfio.controller.support.SessionUtil.addToFlash;
import static alfio.model.PromoCodeDiscount.categoriesOrNull;
import static alfio.model.system.Configuration.getSystemConfiguration;
import static alfio.model.system.ConfigurationKeys.RECAPTCHA_API_KEY;

@Controller
@AllArgsConstructor
public class EventController {

    private static final String REDIRECT = "redirect:";
    private final EventRepository eventRepository;
    private final EventDescriptionRepository eventDescriptionRepository;
    private final I18nManager i18nManager;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final TicketCategoryDescriptionRepository ticketCategoryDescriptionRepository;
    private final ConfigurationManager configurationManager;
    private final OrganizationRepository organizationRepository;
    private final SpecialPriceRepository specialPriceRepository;
    private final PromoCodeDiscountRepository promoCodeRepository;
    private final EventManager eventManager;
    private final TicketReservationManager ticketReservationManager;
    private final EventStatisticsManager eventStatisticsManager;
    private final AdditionalServiceRepository additionalServiceRepository;
    private final AdditionalServiceTextRepository additionalServiceTextRepository;
    private final TicketRepository ticketRepository;
    private final RecaptchaService recaptchaService;
    private final MessageSource messageSource;


    @RequestMapping(value = "/", method = RequestMethod.HEAD)
    public ResponseEntity<String> replyToProxy() {
        return ResponseEntity.ok("Up and running!");
    }

    @RequestMapping(value = "/healthz", method = RequestMethod.GET)
    public ResponseEntity<String> replyToK8s() {
        return ResponseEntity.ok("Up and running!");
    }

    @RequestMapping(value = {"/"}, method = RequestMethod.GET)
    public String listEvents(Model model, Locale locale) {
        List<Event> events = eventManager.getPublishedEvents();
        if(events.size() == 1) {
            return REDIRECT + "/event/" + events.get(0).getShortName() + "/";
        } else {
            model.addAttribute("events", events.stream().map(e -> {
                String eventDescription = eventDescriptionRepository.findDescriptionByEventIdTypeAndLocale(e.getId(), EventDescription.EventDescriptionType.DESCRIPTION, locale.getLanguage()).orElse("");
                return new EventDescriptor(e, eventDescription);
            }).collect(Collectors.toList()));
            model.addAttribute("pageTitle", "event-list.header.title");
            model.addAttribute("event", null);

            model.addAttribute("showAvailableLanguagesInPageTop", true);
            model.addAttribute("availableLanguages", i18nManager.getSupportedLanguages());
            return "/event/event-list";
        }
    }


    @RequestMapping("/session-expired")
    public String sessionExpired(Model model) {
        model.addAttribute("pageTitle", "session-expired.header.title");
        model.addAttribute("event", null);
        return "/event/session-expired";
    }

    @RequestMapping(value = "/event/{eventName}/promoCode/{promoCode}", method = RequestMethod.POST)
    @ResponseBody
    public ValidationResult savePromoCode(@PathVariable("eventName") String eventName,
                                 @PathVariable("promoCode") String promoCode,
                                 Model model,
                                 HttpServletRequest request) {
        
        SessionUtil.cleanupSession(request);

        Optional<EventAndOrganizationId> optional = eventRepository.findOptionalEventAndOrganizationIdByShortName(eventName);
        if(optional.isEmpty()) {
            return ValidationResult.failed(new ValidationResult.ErrorDescriptor("event", ""));
        }
        EventAndOrganizationId event = optional.get();
        ZoneId eventZoneId = eventRepository.getZoneIdByEventId(event.getId());
        ZonedDateTime now = ZonedDateTime.now(eventZoneId);
        Optional<String> maybeSpecialCode = Optional.ofNullable(StringUtils.trimToNull(promoCode));
        Optional<SpecialPrice> specialCode = maybeSpecialCode.flatMap(specialPriceRepository::getByCode);
        Optional<PromoCodeDiscount> promotionCodeDiscount = maybeSpecialCode.flatMap((trimmedCode) -> promoCodeRepository.findPromoCodeInEventOrOrganization(event.getId(), trimmedCode));
        
        if(specialCode.isPresent()) {
            if (eventManager.getOptionalByIdAndActive(specialCode.get().getTicketCategoryId(), event.getId()).isEmpty()) {
                return ValidationResult.failed(new ValidationResult.ErrorDescriptor("promoCode", ""));
            }
            
            if (specialCode.get().getStatus() != SpecialPrice.Status.FREE) {
                return ValidationResult.failed(new ValidationResult.ErrorDescriptor("promoCode", ""));
            }
            
        } else if (promotionCodeDiscount.isPresent() && !promotionCodeDiscount.get().isCurrentlyValid(eventZoneId, now)) {
            return ValidationResult.failed(new ValidationResult.ErrorDescriptor("promoCode", ""));
        } else if (promotionCodeDiscount.isPresent() && isDiscountCodeUsageExceeded(promotionCodeDiscount.get())){
            return ValidationResult.failed(new ValidationResult.ErrorDescriptor("usage", ""));
        } else if(promotionCodeDiscount.isEmpty()) {
            return ValidationResult.failed(new ValidationResult.ErrorDescriptor("promoCode", ""));
        }

        if(!model.asMap().containsKey("hasErrors")) {
            if(specialCode.isPresent()) {
                SessionUtil.saveSpecialPriceCode(maybeSpecialCode.get(), request);
            } else {
                SessionUtil.savePromotionCodeDiscount(maybeSpecialCode.get(), request);
            }
            return ValidationResult.success();
        }
        return ValidationResult.failed(new ValidationResult.ErrorDescriptor("promoCode", ""));
    }

    private boolean isDiscountCodeUsageExceeded(PromoCodeDiscount discount) {
        return discount.getMaxUsage() != null && discount.getMaxUsage() <= promoCodeRepository.countConfirmedPromoCode(discount.getId(), categoriesOrNull(discount), null, categoriesOrNull(discount) != null ? "X" : null);
    }

    @RequestMapping(value = "/event/{eventName}", method = {RequestMethod.GET, RequestMethod.HEAD})
    public String showEvent(@PathVariable("eventName") String eventName,
                            Model model, HttpServletRequest request, Locale locale) {

        return eventRepository.findOptionalByShortName(eventName).filter(e -> e.getStatus() != Event.Status.DISABLED).map(event -> {
            Optional<String> maybeSpecialCode = SessionUtil.retrieveSpecialPriceCode(request);
            Optional<SpecialPrice> specialCode = maybeSpecialCode.flatMap(specialPriceRepository::getByCode);

            Optional<PromoCodeDiscount> promoCodeDiscount = SessionUtil.retrievePromotionCodeDiscount(request)
                .flatMap((code) -> promoCodeRepository.findPromoCodeInEventOrOrganization(event.getId(), code));

            final ZonedDateTime now = ZonedDateTime.now(event.getZoneId());
            //hide access restricted ticket categories
            List<TicketCategory> ticketCategories = ticketCategoryRepository.findAllTicketCategories(event.getId());
            Map<Integer, String> categoriesDescription = ticketCategoryDescriptionRepository.descriptionsByTicketCategory(ticketCategories.stream().map(TicketCategory::getId).collect(Collectors.toList()), locale.getLanguage());

            List<SaleableTicketCategory> saleableTicketCategories = ticketCategories.stream()
                .filter((c) -> !c.isAccessRestricted() || shouldDisplayRestrictedCategory(specialCode, c, promoCodeDiscount))
                .map((m) -> {
                    int maxTickets = configurationManager.getIntConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), m.getId(), ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION), 5);
                    PromoCodeDiscount filteredPromoCode = promoCodeDiscount.filter(promoCode -> shouldApplyDiscount(promoCode, m)).orElse(null);
                    if(specialCode.isPresent()) {
                        maxTickets = Math.min(1, maxTickets);
                    } else if(filteredPromoCode != null && filteredPromoCode.getMaxUsage() != null) {
                        maxTickets = filteredPromoCode.getMaxUsage() - promoCodeRepository.countConfirmedPromoCode(filteredPromoCode.getId(), categoriesOrNull(filteredPromoCode), null, categoriesOrNull(filteredPromoCode) != null ? "X" : null);
                    }
                    return new SaleableTicketCategory(m, categoriesDescription.getOrDefault(m.getId(), ""),
                        now, event, ticketReservationManager.countAvailableTickets(event, m), maxTickets,
                        filteredPromoCode);
                })
                .collect(Collectors.toList());
            //

            Map<ConfigurationKeys, Optional<String>> geoInfoConfiguration = configurationManager.getStringConfigValueFrom(
                Configuration.from(event, ConfigurationKeys.MAPS_PROVIDER),
                Configuration.from(event, ConfigurationKeys.MAPS_CLIENT_API_KEY),
                Configuration.from(event, ConfigurationKeys.MAPS_HERE_APP_ID),
                Configuration.from(event, ConfigurationKeys.MAPS_HERE_APP_CODE));

            LocationDescriptor ld = LocationDescriptor.fromGeoData(event.getLatLong(), TimeZone.getTimeZone(event.getTimeZone()), geoInfoConfiguration);

            final boolean hasAccessPromotions = configurationManager.getBooleanConfigValue(Configuration.from(event, ConfigurationKeys.DISPLAY_DISCOUNT_CODE_BOX), true) &&
                (ticketCategoryRepository.countAccessRestrictedRepositoryByEventId(event.getId()) > 0 ||
                promoCodeRepository.countByEventAndOrganizationId(event.getId(), event.getOrganizationId()) > 0);

            String eventDescription = eventDescriptionRepository.findDescriptionByEventIdTypeAndLocale(event.getId(), EventDescription.EventDescriptionType.DESCRIPTION, locale.getLanguage()).orElse("");

            final EventDescriptor eventDescriptor = new EventDescriptor(event, eventDescription);
            List<SaleableTicketCategory> expiredCategories = saleableTicketCategories.stream().filter(SaleableTicketCategory::getExpired).collect(Collectors.toList());
            List<SaleableTicketCategory> validCategories = saleableTicketCategories.stream().filter(tc -> !tc.getExpired()).collect(Collectors.toList());
            List<SaleableAdditionalService> additionalServices = additionalServiceRepository.loadAllForEvent(event.getId()).stream().map((as) -> getSaleableAdditionalService(event, locale, as, promoCodeDiscount.orElse(null))).collect(Collectors.toList());
            Predicate<SaleableTicketCategory> waitingQueueTargetCategory = tc -> !tc.getExpired() && !tc.isBounded();

            List<SaleableAdditionalService> notExpiredServices = additionalServices.stream().filter(SaleableAdditionalService::isNotExpired).collect(Collectors.toList());

            List<SaleableAdditionalService> supplements = adjustIndex(0, notExpiredServices.stream().filter(a -> a.getType() == AdditionalService.AdditionalServiceType.SUPPLEMENT).collect(Collectors.toList()));
            List<SaleableAdditionalService> donations = adjustIndex(supplements.size(), notExpiredServices.stream().filter(a -> a.getType() == AdditionalService.AdditionalServiceType.DONATION).collect(Collectors.toList()));

            boolean usePartnerCode = configurationManager.getBooleanConfigValue(Configuration.from(event, ConfigurationKeys.USE_PARTNER_CODE_INSTEAD_OF_PROMOTIONAL), false);
            model.addAttribute("event", eventDescriptor)//
                .addAttribute("organization", organizationRepository.getById(event.getOrganizationId()))
                .addAttribute("ticketCategories", validCategories)//
                .addAttribute("expiredCategories", expiredCategories)//
                .addAttribute("containsExpiredCategories", configurationManager.getBooleanConfigValue(Configuration.from(event, ConfigurationKeys.DISPLAY_EXPIRED_CATEGORIES), true) && !expiredCategories.isEmpty())//
                .addAttribute("showNoCategoriesWarning", validCategories.isEmpty())
                .addAttribute("hasAccessPromotions", hasAccessPromotions)
                .addAttribute("promoCode", specialCode.map(SpecialPrice::getCode).orElse(null))
                .addAttribute("locationDescriptor", ld)
                .addAttribute("pageTitle", "show-event.header.title")
                .addAttribute("hasPromoCodeDiscount", promoCodeDiscount.filter(c -> c.getCodeType() == PromoCodeDiscount.CodeType.DISCOUNT).isPresent())
                .addAttribute("hasAccessCode", promoCodeDiscount.filter(c -> c.getCodeType() == PromoCodeDiscount.CodeType.ACCESS).isPresent())
                .addAttribute("promoCodeDiscount", promoCodeDiscount.orElse(null))
                .addAttribute("displayWaitingQueueForm", EventUtil.displayWaitingQueueForm(event, saleableTicketCategories, configurationManager, eventStatisticsManager.noSeatsAvailable()))
                .addAttribute("displayCategorySelectionForWaitingQueue", saleableTicketCategories.stream().filter(waitingQueueTargetCategory).count() > 1)
                .addAttribute("unboundedCategories", saleableTicketCategories.stream().filter(waitingQueueTargetCategory).collect(Collectors.toList()))
                .addAttribute("preSales", EventUtil.isPreSales(event, saleableTicketCategories))
                .addAttribute("userLanguage", locale.getLanguage())
                .addAttribute("showAdditionalServices", !notExpiredServices.isEmpty())
                .addAttribute("showAdditionalServicesDonations", !donations.isEmpty())
                .addAttribute("showAdditionalServicesSupplements", !supplements.isEmpty())
                .addAttribute("enabledAdditionalServicesDonations", donations)
                .addAttribute("enabledAdditionalServicesSupplements", supplements)
                .addAttribute("forwardButtonDisabled", saleableTicketCategories.stream().noneMatch(SaleableTicketCategory::getSaleable))
                .addAttribute("useFirstAndLastName", event.mustUseFirstAndLastName())
                .addAttribute("validityStart", event.getBegin())
                .addAttribute("validityEnd", event.getEnd())
                .addAttribute("usePartnerCode", usePartnerCode)
                .addAttribute("promoCodeDescription", messageSource.getMessage("show-event.promo-code-type."+(usePartnerCode ? "partner" : "promotional"), null, null, locale));

            if(configurationManager.isRecaptchaForTicketSelectionEnabled(event)) {
                model.addAttribute("captchaForTicketSelectionEnabled", true)
                    .addAttribute("recaptchaApiKey", configurationManager.getStringConfigValue(getSystemConfiguration(RECAPTCHA_API_KEY), null));
            }

            model.asMap().putIfAbsent("hasErrors", false);//
            return "/event/show-event";
        }).orElse(REDIRECT + "/");
    }

    private boolean shouldDisplayRestrictedCategory(Optional<SpecialPrice> specialCode, TicketCategory c, Optional<PromoCodeDiscount> optionalPromoCode) {
        if(optionalPromoCode.isPresent()) {
            var promoCode = optionalPromoCode.get();
            if(promoCode.getCodeType() == PromoCodeDiscount.CodeType.ACCESS && c.getId() == promoCode.getHiddenCategoryId()) {
                return true;
            }
        }
        return specialCode.filter(sc -> sc.getTicketCategoryId() == c.getId()).isPresent();
    }


    enum CodeType {
        SPECIAL_PRICE, PROMO_CODE_DISCOUNT, TICKET_CATEGORY_CODE, NOT_FOUND
    }

    //not happy with that code...
    private CodeType getCodeType(int eventId, String code) {
        String trimmedCode = StringUtils.trimToNull(code);
        if(trimmedCode == null) {
            return CodeType.NOT_FOUND;
        }  else if(specialPriceRepository.getByCode(trimmedCode).isPresent()) {
            return CodeType.SPECIAL_PRICE;
        } else if (promoCodeRepository.findPromoCodeInEventOrOrganization(eventId, trimmedCode).isPresent()) {
            return CodeType.PROMO_CODE_DISCOUNT;
        } else if (ticketCategoryRepository.findCodeInEvent(eventId, trimmedCode).isPresent()) {
            return CodeType.TICKET_CATEGORY_CODE;
        } else {
            return CodeType.NOT_FOUND;
        }
    }

    @RequestMapping(value = "/event/{eventName}/code/{code}", method = {RequestMethod.GET, RequestMethod.HEAD})
    @Transactional
    public String handleCode(@PathVariable("eventName") String eventName, @PathVariable("code") String code, Model model, ServletWebRequest request, RedirectAttributes redirectAttributes, Locale locale) {
        String trimmedCode = StringUtils.trimToNull(code);
        return eventRepository.findOptionalByShortName(eventName).map(event -> {
            String redirectToEvent = "redirect:/event/" + eventName + "/";
            ValidationResult res = savePromoCode(eventName, trimmedCode, model, request.getRequest());
            CodeType codeType = getCodeType(event.getId(), trimmedCode);
            if(res.isSuccess() && codeType == CodeType.PROMO_CODE_DISCOUNT) {
                return redirectToEvent;
            } else if (codeType == CodeType.TICKET_CATEGORY_CODE) {
                TicketCategory category = ticketCategoryRepository.findCodeInEvent(event.getId(), trimmedCode).orElseThrow();
                if(!category.isAccessRestricted()) {
                    return makeSimpleReservation(eventName, request, redirectAttributes, locale, null, event, category.getId());
                } else {
                    return initReservationForHiddenCategory(eventName, model, request, redirectAttributes, locale, event, redirectToEvent, category.getId());
                }
            } else if (res.isSuccess() && codeType == CodeType.SPECIAL_PRICE) {
                int ticketCategoryId = specialPriceRepository.getByCode(trimmedCode).orElseThrow().getTicketCategoryId();
                return makeSimpleReservation(eventName, request, redirectAttributes, locale, trimmedCode, event, ticketCategoryId);
            } else {
                return redirectToEvent;
            }
        }).orElse("redirect:/");
    }

    private String initReservationForHiddenCategory(String eventName, Model model, ServletWebRequest request, RedirectAttributes redirectAttributes, Locale locale, Event event, String redirectToEvent, int categoryId) {
        Optional<SpecialPrice> specialPrice = specialPriceRepository.findFirstActiveNotAssignedForUpdate(categoryId);
        if(specialPrice.isEmpty()) {
            return redirectToEvent;
        }
        savePromoCode(eventName, specialPrice.get().getCode(), model, request.getRequest());
        return makeSimpleReservation(eventName, request, redirectAttributes, locale, specialPrice.get().getCode(), event, categoryId);
    }


    private String makeSimpleReservation(String eventName, ServletWebRequest request, RedirectAttributes redirectAttributes, Locale locale, String trimmedCode, Event event, int ticketCategoryId) {
        ReservationForm form = new ReservationForm();
        form.setPromoCode(trimmedCode);
        TicketReservationModification reservation = new TicketReservationModification();
        reservation.setAmount(1);
        reservation.setTicketCategoryId(ticketCategoryId);
        form.setReservation(Collections.singletonList(reservation));
        return validateAndReserve(eventName, form, new BeanPropertyBindingResult(form, "reservationForm"), request, redirectAttributes, locale, event);
    }

    @RequestMapping(value = "/event/{eventName}/calendar/locale/{locale}", method = {RequestMethod.GET, RequestMethod.HEAD})
    public void calendar(@PathVariable("eventName") String eventName,
                         @PathVariable("locale") String locale,
                         @RequestParam(value = "type", required = false) String calendarType,
                         @RequestParam(value = "ticketId", required = false) String ticketId,
                         HttpServletResponse response) throws IOException {
        Optional<Event> event = eventRepository.findOptionalByShortName(eventName);
        if (event.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        //meh
        Event ev = event.get();

        String description = eventDescriptionRepository.findDescriptionByEventIdTypeAndLocale(ev.getId(), EventDescription.EventDescriptionType.DESCRIPTION, locale).orElse("");
        TicketCategory category = ticketRepository.findOptionalByUUID(ticketId).map(t -> ticketCategoryRepository.getById(t.getCategoryId())).orElse(null);

        if("google".equals(calendarType)) {
            response.sendRedirect(EventUtil.getGoogleCalendarURL(ev, category, description));
        } else {
            Optional<byte[]> ical = EventUtil.getIcalForEvent(ev, category, description);
            //meh, checked exceptions don't work well with Function & co :(
            if(ical.isPresent()) {
                response.setContentType("text/calendar");
                response.setHeader("Content-Disposition", "inline; filename=\"calendar.ics\"");
                response.getOutputStream().write(ical.get());
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
        }
    }
    
    
    @RequestMapping(value = "/event/{eventName}/reserve-tickets", method = { RequestMethod.POST, RequestMethod.GET, RequestMethod.HEAD })
    public String reserveTicket(@PathVariable("eventName") String eventName,
            @ModelAttribute ReservationForm reservation, BindingResult bindingResult, Model model,
            ServletWebRequest request, RedirectAttributes redirectAttributes, Locale locale) {

        return eventRepository.findOptionalByShortName(eventName).map(event -> {
            if (request.getHttpMethod() == HttpMethod.GET) {
                return "redirect:/event/" + eventName + "/";
            } else {
                return validateAndReserve(eventName, reservation, bindingResult, request, redirectAttributes, locale, event);
            }
        }).orElse("redirect:/");

    }

    private String validateAndReserve(String eventName, ReservationForm reservation, BindingResult bindingResult, ServletWebRequest request, RedirectAttributes redirectAttributes, Locale locale, Event event) {

        if(isCaptchaInvalid(request.getRequest(), event)) {
            bindingResult.reject(ErrorsCode.STEP_2_CAPTCHA_VALIDATION_FAILED);
        }

        final String redirectToEvent = "redirect:/event/" + eventName + "/";
        return reservation.validate(bindingResult, ticketReservationManager, additionalServiceRepository, eventManager, event)
            .map(selected -> {

                Date expiration = DateUtils.addMinutes(new Date(), ticketReservationManager.getReservationTimeout(event));

                try {
                    String reservationId = ticketReservationManager.createTicketReservation(event,
                            selected.getLeft(), selected.getRight(), expiration,
                            SessionUtil.retrieveSpecialPriceSessionId(request.getRequest()),
                            SessionUtil.retrievePromotionCodeDiscount(request.getRequest()),
                            locale, false);
                    return "redirect:/event/" + eventName + "/reservation/" + reservationId + "/book";
                } catch (TicketReservationManager.NotEnoughTicketsException nete) {
                    bindingResult.reject(ErrorsCode.STEP_1_NOT_ENOUGH_TICKETS);
                    addToFlash(bindingResult, redirectAttributes);
                    return redirectToEvent;
                } catch (TicketReservationManager.MissingSpecialPriceTokenException missing) {
                    bindingResult.reject(ErrorsCode.STEP_1_ACCESS_RESTRICTED);
                    addToFlash(bindingResult, redirectAttributes);
                    return redirectToEvent;
                } catch (TicketReservationManager.InvalidSpecialPriceTokenException invalid) {
                    bindingResult.reject(ErrorsCode.STEP_1_CODE_NOT_FOUND);
                    addToFlash(bindingResult, redirectAttributes);
                    SessionUtil.cleanupSession(request.getRequest());
                    return redirectToEvent;
                } catch (TicketReservationManager.TooManyTicketsForDiscountCodeException tooMany) {
                    bindingResult.reject(ErrorsCode.STEP_2_DISCOUNT_CODE_USAGE_EXCEEDED);
                    addToFlash(bindingResult, redirectAttributes);
                    return redirectToEvent;
                }
            }).orElseGet(() -> {
                addToFlash(bindingResult, redirectAttributes);
                return redirectToEvent;
            });
    }

    private SaleableAdditionalService getSaleableAdditionalService(Event event, Locale locale, AdditionalService as, PromoCodeDiscount promoCodeDiscount) {
        return new SaleableAdditionalService(event, as, additionalServiceTextRepository.findBestMatchByLocaleAndType(as.getId(), locale.getLanguage(), AdditionalServiceText.TextType.TITLE).getValue(),
            additionalServiceTextRepository.findBestMatchByLocaleAndType(as.getId(), locale.getLanguage(), AdditionalServiceText.TextType.DESCRIPTION).getValue(), promoCodeDiscount, 0);
    }

    private static List<SaleableAdditionalService> adjustIndex(int offset, List<SaleableAdditionalService> l) {
        List<SaleableAdditionalService> n = new ArrayList<>(l.size());

        for(int i = 0; i < l.size(); i++) {
            n.add(l.get(i).withIndex(i+offset));
        }
        return n;
    }

    private static boolean shouldApplyDiscount(PromoCodeDiscount promoCodeDiscount, TicketCategory ticketCategory) {
        if(promoCodeDiscount.getCodeType() == PromoCodeDiscount.CodeType.DISCOUNT) {
            return promoCodeDiscount.getCategories().isEmpty() || promoCodeDiscount.getCategories().contains(ticketCategory.getId());
        }
        return ticketCategory.isAccessRestricted() && ticketCategory.getId() == promoCodeDiscount.getHiddenCategoryId();
    }

    private boolean isCaptchaInvalid(HttpServletRequest request, EventAndOrganizationId event) {
        return configurationManager.isRecaptchaForTicketSelectionEnabled(event)
            && !recaptchaService.checkRecaptcha(request);
    }

}
