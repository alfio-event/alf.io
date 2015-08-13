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
package alfio.model.modification;

import alfio.model.Event;
import alfio.model.EventDescription;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.experimental.Delegate;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.CompareToBuilder;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Getter
public class EventWithStatistics implements StatisticsContainer, Comparable<EventWithStatistics> {

    public static final DateTimeFormatter JSON_DATE_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm");
    public static final Predicate<TicketCategoryWithStatistic> IS_BOUNDED = TicketCategoryWithStatistic::isBounded;

    @Delegate
    @JsonIgnore
    private final Event event;
    private final List<TicketCategoryWithStatistic> ticketCategories;
    private final Map<String, String> description;
    private final int soldTickets;
    private final int checkedInTickets;
    private final int allocatedTickets;
    private final boolean containsUnboundedCategories;

    public EventWithStatistics(Event event,
                               List<EventDescription> eventDescriptions,
                               List<TicketCategoryWithStatistic> ticketCategories) {
        this.event = event;
        this.ticketCategories = ticketCategories;
        this.soldTickets = countSoldTickets(ticketCategories);
        this.checkedInTickets = countCheckedInTickets(ticketCategories);
        this.allocatedTickets = ticketCategories.stream().filter(IS_BOUNDED).mapToInt(TicketCategoryWithStatistic::getMaxTickets).sum();
        this.containsUnboundedCategories = ticketCategories.stream().anyMatch(IS_BOUNDED.negate());
        this.description = eventDescriptions.stream().collect(Collectors.toMap(EventDescription::getLocale, EventDescription::getDescription));
    }


    public boolean isWarningNeeded() {
        return !isExpired() && (containsOrphanTickets() || containsStuckReservations());
    }

    public String getFormattedBegin() {
        return getBegin().format(JSON_DATE_FORMATTER);
    }

    public String getFormattedEnd() {
        return getEnd().format(JSON_DATE_FORMATTER);
    }

    private boolean containsOrphanTickets() {
        return ticketCategories.stream().anyMatch(TicketCategoryWithStatistic::isContainingOrphans);
    }

    private boolean containsStuckReservations() {
        return ticketCategories.stream().anyMatch(TicketCategoryWithStatistic::isContainingStuckTickets);
    }

    public boolean isAddCategoryEnabled() {
        return ticketCategories.stream()
                .mapToInt(TicketCategoryWithStatistic::getMaxTickets)
                .sum() < getAvailableSeats();
    }

    @Override
    public int getNotAllocatedTickets() {
        return containsUnboundedCategories ? 0 : countNotAllocatedTickets();
    }

    @Override
    public int getDynamicAllocation() {
        if(containsUnboundedCategories) {
            List<TicketCategoryWithStatistic> unboundedCategories = ticketCategories.stream().filter(IS_BOUNDED.negate()).collect(Collectors.toList());
            return countNotAllocatedTickets() - countSoldTickets(unboundedCategories) - countCheckedInTickets(unboundedCategories);
        }
        return 0;
    }

    private int countNotAllocatedTickets() {
        return event.getAvailableSeats() - allocatedTickets;
    }

    @Override
    public int getNotSoldTickets() {
        if(containsUnboundedCategories) {
            List<TicketCategoryWithStatistic> boundedCategories = ticketCategories.stream().filter(IS_BOUNDED).collect(Collectors.toList());
            return allocatedTickets - countSoldTickets(boundedCategories) - countCheckedInTickets(boundedCategories);
        }
        return allocatedTickets - soldTickets - checkedInTickets;
    }

    public boolean isExpired() {
        return ZonedDateTime.now(event.getZoneId()).truncatedTo(ChronoUnit.DAYS).isAfter(event.getEnd().truncatedTo(ChronoUnit.DAYS));
    }

    @Override
    public int compareTo(EventWithStatistics o) {
        CompareToBuilder builder = new CompareToBuilder();
        return builder.append(isExpired(), o.isExpired()).append(getBegin().withZoneSameInstant(ZoneId.systemDefault()), o.getBegin().withZoneSameInstant(ZoneId.systemDefault())).build();
    }

    private int countCheckedInTickets(List<TicketCategoryWithStatistic> ticketCategories) {
        return ticketCategories.stream().mapToInt(TicketCategoryWithStatistic::getCheckedInTickets).sum();
    }

    private static int countSoldTickets(List<TicketCategoryWithStatistic> ticketCategories) {
        return ticketCategories.stream().mapToInt(TicketCategoryWithStatistic::getSoldTickets).sum();
    }
}
