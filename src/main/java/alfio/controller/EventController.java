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
import alfio.manager.TicketReservationManager;
import alfio.manager.i18n.I18nManager;
import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.model.modification.support.LocationDescriptor;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.model.user.Organization;
import alfio.repository.*;
import alfio.repository.user.OrganizationRepository;
import alfio.util.ErrorsCode;
import alfio.util.EventUtil;
import alfio.util.ValidationResult;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static alfio.controller.support.SessionUtil.addToFlash;
import static alfio.util.OptionalWrapper.optionally;

@Controller
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

    @Autowired
    public EventController(ConfigurationManager configurationManager,
                           EventRepository eventRepository,
                           EventDescriptionRepository eventDescriptionRepository,
                           I18nManager i18nManager,
                           OrganizationRepository organizationRepository,
                           TicketCategoryRepository ticketCategoryRepository,
                           TicketCategoryDescriptionRepository ticketCategoryDescriptionRepository,
                           SpecialPriceRepository specialPriceRepository,
                           PromoCodeDiscountRepository promoCodeRepository,
                           EventManager eventManager,
                           TicketReservationManager ticketReservationManager,
                           EventStatisticsManager eventStatisticsManager,
                           AdditionalServiceRepository additionalServiceRepository,
                           AdditionalServiceTextRepository additionalServiceTextRepository) {
        this.configurationManager = configurationManager;
        this.eventRepository = eventRepository;
        this.eventDescriptionRepository = eventDescriptionRepository;
        this.i18nManager = i18nManager;
        this.organizationRepository = organizationRepository;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.ticketCategoryDescriptionRepository = ticketCategoryDescriptionRepository;
        this.specialPriceRepository = specialPriceRepository;
        this.promoCodeRepository = promoCodeRepository;
        this.eventManager = eventManager;
        this.ticketReservationManager = ticketReservationManager;
        this.eventStatisticsManager = eventStatisticsManager;
        this.additionalServiceRepository = additionalServiceRepository;
        this.additionalServiceTextRepository = additionalServiceTextRepository;
    }

    @RequestMapping(value = "/", method = RequestMethod.HEAD)
    public ResponseEntity<String> replyToProxy() {
        return ResponseEntity.ok("Up and running!");
    }

    @RequestMapping(value = {"/"}, method = RequestMethod.GET)
    public String listEvents(Model model, Locale locale) {
        List<Event> events = eventManager.getActiveEvents();
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
        
        SessionUtil.removeSpecialPriceData(request);

        Optional<Event> optional = eventRepository.findOptionalByShortName(eventName);
        if(!optional.isPresent()) {
            return ValidationResult.failed(new ValidationResult.ValidationError("event", ""));
        }
        Event event = optional.get();
        ZonedDateTime now = ZonedDateTime.now(event.getZoneId());
        Optional<String> maybeSpecialCode = Optional.ofNullable(StringUtils.trimToNull(promoCode));
        Optional<SpecialPrice> specialCode = maybeSpecialCode.flatMap((trimmedCode) -> optionally(() -> specialPriceRepository.getByCode(trimmedCode)));
        Optional<PromoCodeDiscount> promotionCodeDiscount = maybeSpecialCode.flatMap((trimmedCode) -> optionally(() -> promoCodeRepository.findPromoCodeInEvent(event.getId(), trimmedCode)));
        
        if(specialCode.isPresent()) {
            if (!optionally(() -> eventManager.getTicketCategoryById(specialCode.get().getTicketCategoryId(), event.getId())).isPresent()) {
                return ValidationResult.failed(new ValidationResult.ValidationError("promoCode", ""));
            }
            
            if (specialCode.get().getStatus() != SpecialPrice.Status.FREE) {
                return ValidationResult.failed(new ValidationResult.ValidationError("promoCode", ""));
            }
            
        } else if (promotionCodeDiscount.isPresent() && !promotionCodeDiscount.get().isCurrentlyValid(event.getZoneId(), now)) {
            return ValidationResult.failed(new ValidationResult.ValidationError("promoCode", ""));
        } else if(!specialCode.isPresent() && !promotionCodeDiscount.isPresent()) {
            return ValidationResult.failed(new ValidationResult.ValidationError("promoCode", ""));
        }

        if(maybeSpecialCode.isPresent() && !model.asMap().containsKey("hasErrors")) {
            if(specialCode.isPresent()) {
                SessionUtil.saveSpecialPriceCode(maybeSpecialCode.get(), request);
            } else if (promotionCodeDiscount.isPresent()) {
                SessionUtil.savePromotionCodeDiscount(maybeSpecialCode.get(), request);
            }
            return ValidationResult.success();
        }
        return ValidationResult.failed(new ValidationResult.ValidationError("promoCode", ""));
    }

    @RequestMapping(value = "/event/{eventName}", method = {RequestMethod.GET, RequestMethod.HEAD})
    public String showEvent(@PathVariable("eventName") String eventName,
                            Model model, HttpServletRequest request, Locale locale) {

        return eventRepository.findOptionalByShortName(eventName).map(event -> {
            Optional<String> maybeSpecialCode = SessionUtil.retrieveSpecialPriceCode(request);
            Optional<SpecialPrice> specialCode = maybeSpecialCode.flatMap((trimmedCode) -> optionally(() -> specialPriceRepository.getByCode(trimmedCode)));

            Optional<PromoCodeDiscount> promoCodeDiscount = SessionUtil.retrievePromotionCodeDiscount(request)
                .flatMap((code) -> optionally(() -> promoCodeRepository.findPromoCodeInEvent(event.getId(), code)));

            final ZonedDateTime now = ZonedDateTime.now(event.getZoneId());
            //hide access restricted ticket categories
            List<SaleableTicketCategory> ticketCategories = ticketCategoryRepository.findAllTicketCategories(event.getId()).stream()
                .filter((c) -> !c.isAccessRestricted() || (specialCode.filter(sc -> sc.getTicketCategoryId() == c.getId()).isPresent()))
                .map((m) -> new SaleableTicketCategory(m, ticketCategoryDescriptionRepository.findByTicketCategoryIdAndLocale(m.getId(), locale.getLanguage()).orElse(""),
                    now, event, ticketReservationManager.countAvailableTickets(event, m), configurationManager.getIntConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), m.getId(), ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION), 5),
                    promoCodeDiscount.filter(promoCode -> shouldApplyDiscount(promoCode, m)).orElse(null)))
                .collect(Collectors.toList());
            //

            LocationDescriptor ld = LocationDescriptor.fromGeoData(event.getLatLong(), TimeZone.getTimeZone(event.getTimeZone()),
                configurationManager.getStringConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), ConfigurationKeys.MAPS_CLIENT_API_KEY)));

            final boolean hasAccessPromotions = ticketCategoryRepository.countAccessRestrictedRepositoryByEventId(event.getId()) > 0 ||
                promoCodeRepository.countByEventId(event.getId()) > 0;

            String eventDescription = eventDescriptionRepository.findDescriptionByEventIdTypeAndLocale(event.getId(), EventDescription.EventDescriptionType.DESCRIPTION, locale.getLanguage()).orElse("");

            final EventDescriptor eventDescriptor = new EventDescriptor(event, eventDescription);
            List<SaleableTicketCategory> expiredCategories = ticketCategories.stream().filter(SaleableTicketCategory::getExpired).collect(Collectors.toList());
            List<SaleableTicketCategory> validCategories = ticketCategories.stream().filter(tc -> !tc.getExpired()).collect(Collectors.toList());
            List<SaleableAdditionalService> additionalServices = additionalServiceRepository.loadAllForEvent(event.getId()).stream().map((as) -> getSaleableAdditionalService(event, locale, as, promoCodeDiscount.orElse(null))).collect(Collectors.toList());
            Predicate<SaleableTicketCategory> waitingQueueTargetCategory = tc -> !tc.getExpired() && !tc.isBounded();
            model.addAttribute("event", eventDescriptor)//
                .addAttribute("organization", organizationRepository.getById(event.getOrganizationId()))
                .addAttribute("ticketCategories", validCategories)//
                .addAttribute("expiredCategories", expiredCategories)//
                .addAttribute("containsExpiredCategories", !expiredCategories.isEmpty())//
                .addAttribute("showNoCategoriesWarning", validCategories.isEmpty())
                .addAttribute("hasAccessPromotions", hasAccessPromotions)
                .addAttribute("promoCode", specialCode.map(SpecialPrice::getCode).orElse(null))
                .addAttribute("locationDescriptor", ld)
                .addAttribute("pageTitle", "show-event.header.title")
                .addAttribute("hasPromoCodeDiscount", promoCodeDiscount.isPresent())
                .addAttribute("promoCodeDiscount", promoCodeDiscount.orElse(null))
                .addAttribute("displayWaitingQueueForm", EventUtil.displayWaitingQueueForm(event, ticketCategories, configurationManager, eventStatisticsManager.noSeatsAvailable()))
                .addAttribute("displayCategorySelectionForWaitingQueue", ticketCategories.stream().filter(waitingQueueTargetCategory).count() > 1)
                .addAttribute("unboundedCategories", ticketCategories.stream().filter(waitingQueueTargetCategory).collect(Collectors.toList()))
                .addAttribute("preSales", EventUtil.isPreSales(event, ticketCategories))
                .addAttribute("userLanguage", locale.getLanguage())
                .addAttribute("showAdditionalServices", !additionalServices.isEmpty())
                .addAttribute("enabledAdditionalServices", additionalServices.stream().filter(SaleableAdditionalService::isNotExpired).collect(Collectors.toList()))
                .addAttribute("disabledAdditionalServices", additionalServices.stream().filter(SaleableAdditionalService::isExpired).collect(Collectors.toList()))
                .addAttribute("forwardButtonDisabled", ticketCategories.stream().noneMatch(SaleableTicketCategory::getSaleable));
            model.asMap().putIfAbsent("hasErrors", false);//
            return "/event/show-event";
        }).orElse(REDIRECT + "/");
    }

    @RequestMapping(value = "/event/{eventName}/calendar/locale/{locale}", method = {RequestMethod.GET, RequestMethod.HEAD})
    public void calendar(@PathVariable("eventName") String eventName, @PathVariable("locale") String locale, @RequestParam(value = "type", required = false) String calendarType, HttpServletResponse response) throws IOException {
        Optional<Event> event = eventRepository.findOptionalByShortName(eventName);
        if (!event.isPresent()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        //meh
        Event ev = event.get();

        String description = eventDescriptionRepository.findDescriptionByEventIdTypeAndLocale(ev.getId(), EventDescription.EventDescriptionType.DESCRIPTION, locale).orElse("");

        if("google".equals(calendarType)) {
            response.sendRedirect(ev.getGoogleCalendarUrl(description));
        } else {
            Organization organization = organizationRepository.getById(ev.getOrganizationId());
            Optional<byte[]> ical = ev.getIcal(description, organization.getName(), organization.getEmail());
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

            final String redirectToEvent = "redirect:/event/" + eventName + "/";

            if (request.getHttpMethod() == HttpMethod.GET) {
                return redirectToEvent;
            }

            return reservation.validate(bindingResult, ticketReservationManager, ticketCategoryDescriptionRepository, additionalServiceRepository, eventManager, event, locale)
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
                        SessionUtil.removeSpecialPriceData(request.getRequest());
                        return redirectToEvent;
                    }
                }).orElseGet(() -> {
                    addToFlash(bindingResult, redirectAttributes);
                    return redirectToEvent;
                });

        }).orElse("redirect:/");

    }

    private SaleableAdditionalService getSaleableAdditionalService(Event event, Locale locale, AdditionalService as, PromoCodeDiscount promoCodeDiscount) {
        return new SaleableAdditionalService(event, as, additionalServiceTextRepository.findByLocaleAndType(as.getId(), locale.getLanguage(), AdditionalServiceText.TextType.TITLE).getValue(), additionalServiceTextRepository.findByLocaleAndType(as.getId(), locale.getLanguage(), AdditionalServiceText.TextType.DESCRIPTION).getValue(), promoCodeDiscount);
    }

    private static boolean shouldApplyDiscount(PromoCodeDiscount promoCodeDiscount, TicketCategory ticketCategory) {
        return promoCodeDiscount.getCategories().isEmpty() || promoCodeDiscount.getCategories().contains(ticketCategory.getId());
    }

}
