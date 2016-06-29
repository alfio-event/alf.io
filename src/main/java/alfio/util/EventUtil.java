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
import alfio.model.modification.EventWithStatistics;
import alfio.model.modification.TicketCategoryWithStatistic;
import alfio.model.system.Configuration;
import lombok.experimental.UtilityClass;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static alfio.model.system.ConfigurationKeys.ENABLE_PRE_REGISTRATION;
import static alfio.model.system.ConfigurationKeys.ENABLE_WAITING_QUEUE;
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
        return findLastCategory(categories).map(lastCategory -> {
            ZonedDateTime now = ZonedDateTime.now(event.getZoneId());
            if(isPreSales(event, categories)) {
                return configurationManager.getBooleanConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_PRE_REGISTRATION), false);
            } else if(configurationManager.getBooleanConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_WAITING_QUEUE), false)) {
                return now.isBefore(lastCategory.getZonedExpiration()) && noTicketsAvailable.test(event);
            }
            return false;
        }).orElse(false);
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
                .map(ps -> buildTicketParams(event.getId(), creationDate, Optional.empty(), 0, ps));
    }

    public static Stream<MapSqlParameterSource> generateStreamForTicketCreation(int limit) {
        return Stream.generate(MapSqlParameterSource::new)
                .limit(limit);
    }

    public static MapSqlParameterSource buildTicketParams(int eventId,
                                              Date creation,
                                              Optional<TicketCategory> tc,
                                              int srcPriceCts,
                                              MapSqlParameterSource ps) {
        return ps.addValue("uuid", UUID.randomUUID().toString())
                .addValue("creation", creation)
                .addValue("categoryId", tc.map(TicketCategory::getId).orElse(null))
                .addValue("eventId", eventId)
                .addValue("status", Ticket.TicketStatus.FREE.name())
                .addValue("srcPriceCts", srcPriceCts);
    }

    public static int evaluatePrice(int price, boolean freeOfCharge) {
        return freeOfCharge ? 0 : price;
    }

    public static int determineAvailableSeats(TicketCategoryWithStatistic tc, EventWithStatistics e) {
        return tc.isBounded() ? tc.getNotSoldTickets() : e.getDynamicAllocation();
    }



}
