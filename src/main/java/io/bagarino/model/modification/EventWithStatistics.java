/**
 * This file is part of bagarino.
 *
 * bagarino is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bagarino is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.bagarino.model.modification;

import io.bagarino.model.Event;
import lombok.Getter;
import lombok.experimental.Delegate;

import java.util.List;

@Getter
public class EventWithStatistics {

    @Delegate
    private final Event event;
    private final List<TicketCategoryWithStatistic> ticketCategories;

    public EventWithStatistics(Event event,
                               List<TicketCategoryWithStatistic> ticketCategories) {
        this.event = event;
        this.ticketCategories = ticketCategories;
    }

    public boolean isWarningNeeded() {
        return ticketCategories.stream()
                .anyMatch(TicketCategoryWithStatistic::isContainingOrphans);
    }

}
