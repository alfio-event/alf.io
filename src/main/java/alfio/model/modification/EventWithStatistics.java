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
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.experimental.Delegate;
import org.apache.commons.lang3.builder.CompareToBuilder;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Getter
public class EventWithStatistics implements StatisticsContainer, Comparable<EventWithStatistics> {

    public static final DateTimeFormatter JSON_DATE_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm");

    @Delegate
    @JsonIgnore
    private final Event event;
    private final List<TicketCategoryWithStatistic> ticketCategories;
    private final int soldTickets;
    private final int checkedInTickets;
    private final int allocatedTickets;

    public EventWithStatistics(Event event,
                               List<TicketCategoryWithStatistic> ticketCategories) {
        this.event = event;
        this.ticketCategories = ticketCategories;
        this.soldTickets = ticketCategories.stream().mapToInt(TicketCategoryWithStatistic::getSoldTickets).sum();
        this.checkedInTickets = ticketCategories.stream().mapToInt(TicketCategoryWithStatistic::getCheckedInTickets).sum();
        this.allocatedTickets = ticketCategories.stream().filter(TicketCategoryWithStatistic::isBounded).mapToInt(TicketCategoryWithStatistic::getMaxTickets).sum();
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
        return event.getAvailableSeats() - allocatedTickets;
    }

    @Override
    public int getNotSoldTickets() {
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
}
