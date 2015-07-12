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
import alfio.model.Event;
import alfio.model.Ticket;
import alfio.model.TicketCategory;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import lombok.experimental.UtilityClass;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

@UtilityClass
public class EventUtil {

    private static final Predicate<List<SaleableTicketCategory>> IS_EMPTY = List::isEmpty;

    public static boolean displayWaitingQueueForm(Event event, List<SaleableTicketCategory> categories, ConfigurationManager configurationManager) {
        Optional<SaleableTicketCategory> lastCategoryOptional = findLastCategory(categories);
        if(!lastCategoryOptional.isPresent()) {
            return false;
        }
        ZonedDateTime now = ZonedDateTime.now(event.getZoneId());
        SaleableTicketCategory lastCategory = lastCategoryOptional.get();
        if(isPreSales(event, categories)) {
            return configurationManager.getBooleanConfigValue(Configuration.event(event), ConfigurationKeys.ENABLE_PRE_REGISTRATION, false);
        } else if(configurationManager.getBooleanConfigValue(Configuration.event(event), ConfigurationKeys.ENABLE_WAITING_QUEUE, false)) {
            return now.isBefore(lastCategory.getZonedExpiration()) && noTicketAvailable(categories);
        }
        return false;
    }

    private static Optional<SaleableTicketCategory> findLastCategory(List<SaleableTicketCategory> categories) {
        return Optional.ofNullable(categories).filter(IS_EMPTY.negate()).map(l -> l.get(l.size() - 1));
    }

    private static Optional<SaleableTicketCategory> findFirstCategory(List<SaleableTicketCategory> categories) {
        return Optional.ofNullable(categories).filter(IS_EMPTY.negate()).flatMap(l -> l.stream().findFirst());
    }

    private static boolean noTicketAvailable(List<SaleableTicketCategory> categories) {
        return categories.stream().noneMatch(c -> c.getAvailableTickets() > 0);
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
}
