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

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.*;

@Getter
public class DateTimeModification {

    private final LocalDate date;
    private final LocalTime time;

    public DateTimeModification(@JsonProperty("date") LocalDate date, @JsonProperty("time") LocalTime time) {
        this.date = date;
        this.time = time;
    }

    public ZonedDateTime toZonedDateTime(ZoneId zoneId) {
        return ZonedDateTime.of(date, time, zoneId);
    }

    public boolean isBefore(DateTimeModification other) {
        return toLocalDateTime().isBefore(other.toLocalDateTime());
    }

    public boolean isAfter(DateTimeModification other) {
        return toLocalDateTime().isAfter(other.toLocalDateTime());
    }

    public LocalDateTime toLocalDateTime() {
        return LocalDateTime.of(date, time);
    }

    public static DateTimeModification fromZonedDateTime(ZonedDateTime zonedDateTime) {
        return zonedDateTime == null ? null : new DateTimeModification(zonedDateTime.toLocalDate(), zonedDateTime.toLocalTime());
    }

    public static ZonedDateTime atZone(DateTimeModification t, ZoneId zoneId) {
        return t == null ? null : t.toZonedDateTime(zoneId);
    }

}
