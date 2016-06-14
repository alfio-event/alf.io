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
package alfio.util;

import alfio.controller.decorator.SaleableTicketCategory;
import alfio.manager.system.ConfigurationManager;
import alfio.model.AdditionalService;
import alfio.model.Event;
import alfio.model.Ticket;
import alfio.model.TicketCategory;
import alfio.model.modification.EventWithStatistics;
import alfio.model.modification.TicketCategoryWithStatistic;
import alfio.model.system.Configuration;
import lombok.experimental.UtilityClass;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static alfio.model.system.ConfigurationKeys.ENABLE_PRE_REGISTRATION;
import static alfio.model.system.ConfigurationKeys.ENABLE_WAITING_QUEUE;
import static alfio.util.MonetaryUtil.addVAT;
import static java.time.temporal.ChronoField.*;

@UtilityClass
public class EventUtil {

    private static final DateTimeFormatter JSON_TIME_FORMATTER = new DateTimeFormatterBuilder()
        .appendValue(HOUR_OF_DAY, 2)
        .appendLiteral(':')
        .appendValue(MINUTE_OF_HOUR, 2)
        .optionalStart()
        .appendLiteral(':')
        .appendValue(SECOND_OF_MINUTE, 2)
        .toFormatter(Locale.ROOT);

    public static final DateTimeFormatter JSON_DATETIME_FORMATTER = new DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .append(DateTimeFormatter.ISO_LOCAL_DATE)
        .appendLiteral('T')
        .append(JSON_TIME_FORMATTER)
        .appendLiteral('Z')
        .toFormatter(Locale.ROOT);

    public static boolean displayWaitingQueueForm(Event event, List<SaleableTicketCategory> categories, ConfigurationManager configurationManager, Predicate<Event> noTicketsAvailable) {
        Optional<SaleableTicketCategory> lastCategoryOptional = findLastCategory(categories);
        if(!lastCategoryOptional.isPresent()) {
            return false;
        }
        ZonedDateTime now = ZonedDateTime.now(event.getZoneId());
        if(isPreSales(event, categories)) {
            return configurationManager.getBooleanConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_PRE_REGISTRATION), false);
        } else if(configurationManager.getBooleanConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_WAITING_QUEUE), false)) {
            return now.isBefore(lastCategoryOptional.get().getZonedExpiration()) && noTicketsAvailable.test(event);
        }
        return false;
    }


    private static Optional<SaleableTicketCategory> findLastCategory(List<SaleableTicketCategory> categories) {
        return sortCategories(categories, (c1, c2) -> c2.getUtcExpiration().compareTo(c1.getUtcExpiration())).findFirst();
    }

    private static Optional<SaleableTicketCategory> findFirstCategory(List<SaleableTicketCategory> categories) {
        return sortCategories(categories, (c1, c2) -> c1.getUtcExpiration().compareTo(c2.getUtcExpiration())).findFirst();
    }

    private static Stream<SaleableTicketCategory> sortCategories(List<SaleableTicketCategory> categories, Comparator<SaleableTicketCategory> comparator) {
        return Optional.ofNullable(categories).orElse(Collections.emptyList()).stream().sorted(comparator);
    }



    public static boolean isPreSales(Event event, List<SaleableTicketCategory> categories) {
        ZonedDateTime now = ZonedDateTime.now(event.getZoneId());
        return findFirstCategory(categories).map(c -> now.isBefore(c.getZonedInception())).orElse(false);
    }

    public static Stream<MapSqlParameterSource> generateEmptyTickets(Event event, Date creationDate, int limit) {
        return generateStreamForTicketCreation(limit)
                .map(ps -> buildTicketParams(event.getId(), creationDate, Optional.<TicketCategory>empty(), 0, 0, ps));
    }

    public static Stream<MapSqlParameterSource> generateStreamForTicketCreation(int limit) {
        return Stream.generate(MapSqlParameterSource::new)
                .limit(limit);
    }

    public static MapSqlParameterSource buildTicketParams(int eventId,
                                              Date creation,
                                              Optional<TicketCategory> tc,
                                              int originalPrice,
                                              int paidPrice,
                                              MapSqlParameterSource ps) {
        return ps.addValue("uuid", UUID.randomUUID().toString())
                .addValue("creation", creation)
                .addValue("categoryId", tc.map(TicketCategory::getId).orElse(null))
                .addValue("eventId", eventId)
                .addValue("status", Ticket.TicketStatus.FREE.name())
                .addValue("originalPrice", originalPrice)
                .addValue("paidPrice", paidPrice);
    }

    /**
     * Calculate the price for ticket category edit page
     *
     * @param e
     * @return
     */
    public static UnaryOperator<Integer> categoryPriceCalculator(Event e) {
        return p -> {
            if(e.isFreeOfCharge()) {
                return 0;
            }
            if(e.isVatIncluded()) {
                return MonetaryUtil.addVAT(p, e.getVat());
            }
            return p;
        };
    }

    public static int evaluatePrice(int price, BigDecimal vat, boolean vatIncluded, boolean freeOfCharge) {
        if(freeOfCharge) {
            return 0;
        }
        if(!vatIncluded) {
            return price;
        }
        return MonetaryUtil.removeVAT(price, vat);
    }

    public static int determineAvailableSeats(TicketCategoryWithStatistic tc, EventWithStatistics e) {
        return tc.isBounded() ? tc.getNotSoldTickets() : e.getDynamicAllocation();
    }

    public static int getFinalPriceInCents(Event event, TicketCategory category) {
        if(event.isVatIncluded()) {
            return addVAT(category.getPriceInCents(), event.getVat());
        }
        return category.getPriceInCents();
    }

    public static int getFinalPriceInCents(Event event, AdditionalService additionalService) {
        if(!additionalService.isFixPrice()) {
            return 0;
        }
        switch(additionalService.getVatType()) {
            case NONE:
            case CUSTOM_EXCLUDED:
                return additionalService.getPriceInCents();
            case INHERITED:
                if(event.isVatIncluded()) {
                    return addVAT(additionalService.getPriceInCents(), event.getVat());
                }
                return additionalService.getPriceInCents();
            case CUSTOM_INCLUDED:
                return addVAT(additionalService.getPriceInCents(), additionalService.getVat());
            default:
                return additionalService.getPriceInCents();
        }
    }


}
