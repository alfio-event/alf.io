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

import java.time.format.DateTimeFormatter;
import java.util.List;

@Getter
public class EventWithStatistics {

    public static final DateTimeFormatter JSON_DATE_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm");

    @Delegate
    @JsonIgnore
    private final Event event;
    private final List<TicketCategoryWithStatistic> ticketCategories;

    public EventWithStatistics(Event event,
                               List<TicketCategoryWithStatistic> ticketCategories) {
        this.event = event;
        this.ticketCategories = ticketCategories;
    }

    public boolean isWarningNeeded() {
        return containsOrphanTickets() || containsStuckReservations();
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

}
