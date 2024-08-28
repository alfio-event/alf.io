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
package alfio.manager;

import alfio.controller.api.v2.model.AdditionalService;
import alfio.controller.api.v2.model.ItemsByCategory;
import alfio.controller.api.v2.model.TicketCategory;
import alfio.controller.decorator.SaleableAdditionalService;
import alfio.controller.decorator.SaleableTicketCategory;
import alfio.controller.support.Formatters;
import alfio.manager.i18n.MessageSourceManager;
import alfio.manager.support.response.ValidatedResponse;
import alfio.manager.system.ConfigurationManager;
import alfio.model.AdditionalServiceText;
import alfio.model.Event;
import alfio.model.PromoCodeDiscount;
import alfio.model.SpecialPrice;
import alfio.model.system.ConfigurationKeys;
import alfio.repository.EventRepository;
import alfio.repository.PromoCodeDiscountRepository;
import alfio.repository.TicketCategoryDescriptionRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.util.ClockProvider;
import alfio.util.EventUtil;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static alfio.model.system.ConfigurationKeys.*;
import static alfio.model.system.ConfigurationKeys.DISPLAY_EXPIRED_CATEGORIES;
import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Collectors.toList;

@Component
@Transactional(readOnly = true)
public class TicketCategoryAvailabilityManager {

    private final TicketCategoryRepository ticketCategoryRepository;
    private final EventRepository eventRepository;
    private final ConfigurationManager configurationManager;
    private final MessageSourceManager messageSourceManager;
    private final PromoCodeRequestManager promoCodeRequestManager;
    private final ClockProvider clockProvider;
    private final PromoCodeDiscountRepository promoCodeRepository;
    private final TicketReservationManager ticketReservationManager;
    private final AdditionalServiceManager additionalServiceManager;
    private final TicketCategoryDescriptionRepository ticketCategoryDescriptionRepository;
    private final EventStatisticsManager eventStatisticsManager;

    public TicketCategoryAvailabilityManager(TicketCategoryRepository ticketCategoryRepository, EventRepository eventRepository, ConfigurationManager configurationManager, MessageSourceManager messageSourceManager, PromoCodeRequestManager promoCodeRequestManager, ClockProvider clockProvider, PromoCodeDiscountRepository promoCodeRepository, TicketReservationManager ticketReservationManager, AdditionalServiceManager additionalServiceManager, TicketCategoryDescriptionRepository ticketCategoryDescriptionRepository, EventStatisticsManager eventStatisticsManager) {
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.eventRepository = eventRepository;
        this.configurationManager = configurationManager;
        this.messageSourceManager = messageSourceManager;
        this.promoCodeRequestManager = promoCodeRequestManager;
        this.clockProvider = clockProvider;
        this.promoCodeRepository = promoCodeRepository;
        this.ticketReservationManager = ticketReservationManager;
        this.additionalServiceManager = additionalServiceManager;
        this.ticketCategoryDescriptionRepository = ticketCategoryDescriptionRepository;
        this.eventStatisticsManager = eventStatisticsManager;
    }

    public Optional<ItemsByCategory> getTicketCategories(String eventName, String code) {
        return eventRepository.findOptionalByShortName(eventName).filter(e -> e.getStatus() != Event.Status.DISABLED).map(event -> {
            var configurations = configurationManager.getFor(List.of(
                DISPLAY_TICKETS_LEFT_INDICATOR, MAX_AMOUNT_OF_TICKETS_BY_RESERVATION, DISPLAY_EXPIRED_CATEGORIES,
                STOP_WAITING_QUEUE_SUBSCRIPTIONS, ENABLE_PRE_REGISTRATION, ENABLE_WAITING_QUEUE // used by EventUtil
                ), event.getConfigurationLevel());
            var ticketCategoryLevelConfiguration = configurationManager.getAllCategoriesAndValueWith(event, MAX_AMOUNT_OF_TICKETS_BY_RESERVATION);
            var messageSource = messageSourceManager.getMessageSourceFor(event);
            var appliedPromoCode = promoCodeRequestManager.checkCode(event, code);


            Optional<SpecialPrice> specialCode = appliedPromoCode.getValue().getLeft();
            Optional<PromoCodeDiscount> promoCodeDiscount = appliedPromoCode.getValue().getRight();

            final ZonedDateTime now = event.now(clockProvider);
            //hide access restricted ticket categories
            var ticketCategories = ticketCategoryRepository.findAllTicketCategories(event.getId());

            List<SaleableTicketCategory> saleableTicketCategories = ticketCategories.stream()
                .filter((c) -> !c.isAccessRestricted() || shouldDisplayRestrictedCategory(specialCode, c, promoCodeDiscount))
                .map((category) -> {
                    int maxTickets = getMaxAmountOfTicketsPerReservation(configurations, ticketCategoryLevelConfiguration, category.getId());
                    PromoCodeDiscount filteredPromoCode = promoCodeDiscount.filter(promoCode -> shouldApplyDiscount(promoCode, category)).orElse(null);
                    if (specialCode.isPresent()) {
                        maxTickets = Math.min(1, maxTickets);
                    } else if (filteredPromoCode != null && filteredPromoCode.getMaxUsage() != null) {
                        maxTickets = filteredPromoCode.getMaxUsage() - promoCodeRepository.countConfirmedPromoCode(filteredPromoCode.getId());
                    }
                    return new SaleableTicketCategory(category,
                        now, event, ticketReservationManager.countAvailableTickets(event, category), maxTickets,
                        filteredPromoCode);
                })
                .collect(Collectors.toList());


            var valid = saleableTicketCategories.stream().filter(tc -> !tc.getExpired()).collect(Collectors.toList());

            //

            var ticketCategoryIds = valid.stream().map(SaleableTicketCategory::getId).collect(Collectors.toList());
            var ticketCategoryDescriptions = ticketCategoryDescriptionRepository.descriptionsByTicketCategory(ticketCategoryIds);
            var categoriesNoTax = configurationManager.getCategoriesWithNoTaxes(ticketCategoryIds);

            boolean displayTicketsLeft = configurations.get(DISPLAY_TICKETS_LEFT_INDICATOR).getValueAsBooleanOrDefault();
            var categoriesByExpiredFlag = saleableTicketCategories.stream()
                .map(stc -> {
                    var description = Formatters.applyCommonMark(ticketCategoryDescriptions.getOrDefault(stc.getId(), Collections.emptyMap()), messageSource);
                    var expiration = Formatters.getFormattedDate(event, stc.getZonedExpiration(), "common.ticket-category.date-format", messageSource);
                    var inception = Formatters.getFormattedDate(event, stc.getZonedInception(), "common.ticket-category.date-format", messageSource);
                    return new TicketCategory(stc, description, inception, expiration, displayTicketsLeft && !stc.isAccessRestricted(), !categoriesNoTax.contains(stc.getId()));
                })
                .sorted(Comparator.comparingInt(TicketCategory::getOrdinal))
                .collect(partitioningBy(TicketCategory::isExpired));


            var promoCode = Optional.of(appliedPromoCode).filter(ValidatedResponse::isSuccess)
                .map(ValidatedResponse::getValue)
                .flatMap(Pair::getRight);

            //
            var saleableAdditionalServices = additionalServiceManager.loadAllForEvent(event.getId())
                .stream()
                .map(as -> new SaleableAdditionalService(event, as, promoCode.orElse(null)))
                .filter(Predicate.not(SaleableAdditionalService::isExpired))
                .collect(Collectors.toList());

            // will be used for fetching descriptions and titles for all the languages
            var saleableAdditionalServicesIds = saleableAdditionalServices.stream().map(SaleableAdditionalService::id).collect(Collectors.toList());

            var additionalServiceTexts = additionalServiceManager.getDescriptionsByAdditionalServiceIds(saleableAdditionalServicesIds);

            var additionalServicesRes = saleableAdditionalServices.stream().map(as -> {
                var expiration = Formatters.getFormattedDate(event, as.getZonedExpiration(), "common.ticket-category.date-format", messageSource);
                var inception = Formatters.getFormattedDate(event, as.getZonedInception(), "common.ticket-category.date-format", messageSource);
                var title = additionalServiceTexts.getOrDefault(as.id(), Collections.emptyMap()).getOrDefault(AdditionalServiceText.TextType.TITLE, Collections.emptyMap());
                var description = Formatters.applyCommonMark(additionalServiceTexts.getOrDefault(as.id(), Collections.emptyMap()).getOrDefault(AdditionalServiceText.TextType.DESCRIPTION, Collections.emptyMap()), messageSource);
                return new AdditionalService(as.id(), as.type(), as.supplementPolicy(),
                    as.fixPrice(), as.availableItems(), as.maxQtyPerOrder(),
                    as.getFree(), as.getFormattedFinalPrice(), as.getSupportsDiscount(), as.getDiscountedPrice(), as.getVatApplies(), as.getVatIncluded(), as.getVatPercentage().toString(),
                    as.isExpired(), as.getSaleInFuture(),
                    inception, expiration, title, description);
            }).collect(Collectors.toList());
            //

            // waiting queue parameters
            boolean displayWaitingQueueForm = EventUtil.displayWaitingQueueForm(event, saleableTicketCategories, configurations, eventStatisticsManager.noSeatsAvailable());
            boolean preSales = EventUtil.isPreSales(event, saleableTicketCategories);
            Predicate<SaleableTicketCategory> waitingQueueTargetCategory = tc -> !tc.getExpired() && !tc.isBounded();
            List<SaleableTicketCategory> unboundedCategories = saleableTicketCategories.stream().filter(waitingQueueTargetCategory).collect(Collectors.toList());
            var tcForWaitingList = unboundedCategories.stream().map(stc -> new ItemsByCategory.TicketCategoryForWaitingList(stc.getId(), stc.getName())).collect(toList());
            //
            var activeCategories = categoriesByExpiredFlag.get(false);
            var expiredCategories = configurations.get(DISPLAY_EXPIRED_CATEGORIES).getValueAsBooleanOrDefault() ? categoriesByExpiredFlag.get(true) : List.<TicketCategory>of();

            return new ItemsByCategory(activeCategories, expiredCategories, additionalServicesRes, displayWaitingQueueForm, preSales, tcForWaitingList);
        });
    }

    private static boolean shouldDisplayRestrictedCategory(Optional<SpecialPrice> specialCode, alfio.model.TicketCategory c, Optional<PromoCodeDiscount> optionalPromoCode) {
        if(optionalPromoCode.isPresent()) {
            var promoCode = optionalPromoCode.get();
            if(promoCode.getCodeType() == PromoCodeDiscount.CodeType.ACCESS && c.getId() == promoCode.getHiddenCategoryId()) {
                return true;
            }
        }
        return specialCode.filter(sc -> sc.getTicketCategoryId() == c.getId()).isPresent();
    }

    private static int getMaxAmountOfTicketsPerReservation(Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> eventLevelConf,
                                                           Map<Integer, String> ticketCategoryLevelConf,
                                                           int ticketCategory) {

        if (ticketCategoryLevelConf.containsKey(ticketCategory)) {
            return Integer.parseInt(ticketCategoryLevelConf.get(ticketCategory));
        }
        return eventLevelConf.get(MAX_AMOUNT_OF_TICKETS_BY_RESERVATION).getValueAsIntOrDefault(5);
    }

    private static boolean shouldApplyDiscount(PromoCodeDiscount promoCodeDiscount, alfio.model.TicketCategory ticketCategory) {
        if(promoCodeDiscount.getCodeType() == PromoCodeDiscount.CodeType.DISCOUNT) {
            return promoCodeDiscount.getCategories().isEmpty() || promoCodeDiscount.getCategories().contains(ticketCategory.getId());
        }
        return ticketCategory.isAccessRestricted() && ticketCategory.getId() == promoCodeDiscount.getHiddenCategoryId();
    }
}
