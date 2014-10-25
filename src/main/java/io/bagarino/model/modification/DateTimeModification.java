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

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.*;
import java.util.Date;

@Getter
public class DateTimeModification {
    private final LocalDate date;
    private final LocalTime time;

    public DateTimeModification(@JsonProperty("date") LocalDate date, @JsonProperty("time") LocalTime time) {
        this.date = date;
        this.time = time;
    }

    public Date toDate(ZoneId zoneId) {
        ZonedDateTime zd = ZonedDateTime.of(date, time, zoneId);
        return Date.from(zd.withZoneSameInstant(ZoneId.of(ZoneOffset.UTC.getId())).toInstant());
    }

    public ZonedDateTime toZonedDateTime(ZoneId zoneId) {
        return ZonedDateTime.of(date, time, zoneId);
    }
}
