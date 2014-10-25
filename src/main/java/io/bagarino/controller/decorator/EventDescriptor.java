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
package io.bagarino.controller.decorator;

import io.bagarino.model.Event;
import lombok.experimental.Delegate;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class EventDescriptor {

    @Delegate
    private final Event event;

    public EventDescriptor(Event event) {
        this.event = event;
    }

    public String getFormattedEventDates() {
        final ZonedDateTime begin = event.getBegin();
        final ZonedDateTime end = event.getEnd();
        if(event.getSameDay()) {
            return String.format("%s %s - %s", begin.format(DateTimeFormatter.ISO_DATE), begin.format(DateTimeFormatter.ISO_TIME), end.format(DateTimeFormatter.ISO_TIME));
        }
        return String.format("%s %s - %s %s", begin.format(DateTimeFormatter.ISO_DATE), begin.format(DateTimeFormatter.ISO_TIME),
                end.format(DateTimeFormatter.ISO_DATE), end.format(DateTimeFormatter.ISO_TIME));
    }

    public boolean getVatIncluded() {
        return event.isVatIncluded();
    }
}
