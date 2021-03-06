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
package alfio.controller.api.v2.model;

import alfio.model.Event;
import lombok.Data;

import java.time.ZonedDateTime;

import static java.time.temporal.ChronoField.OFFSET_SECONDS;

@Data
public class DatesWithTimeZoneOffset {
    private final long startDateTime;
    private final int startTimeZoneOffset;
    private final long endDateTime;
    private final int endTimeZoneOffset;

    public static DatesWithTimeZoneOffset fromEvent(Event event) {
        return new DatesWithTimeZoneOffset(toEpochMilli(event.getBegin()),
            event.getBeginTimeZoneOffset(), toEpochMilli(event.getEnd()), event.getEndTimeZoneOffset());
    }

    public static DatesWithTimeZoneOffset fromDates(ZonedDateTime start, ZonedDateTime end) {
        return new DatesWithTimeZoneOffset(toEpochMilli(start), getOffset(start), toEpochMilli(end), getOffset(end));
    }

    private static long toEpochMilli(ZonedDateTime in) {
        if(in != null) {
            return in.toInstant().toEpochMilli();
        }
        return 0;
    }

    private static int getOffset(ZonedDateTime in) {
        if(in != null) {
            return in.getOffset().get(OFFSET_SECONDS);
        }
        return 0;
    }
}

