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

import com.insightfullogic.lambdabehave.JunitSuiteRunner;
import org.junit.runner.RunWith;

import java.sql.Date;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static com.insightfullogic.lambdabehave.Suite.describe;

@RunWith(JunitSuiteRunner.class)
public class DateTimeModificationTest {{
    final ZoneId myZoneId = ZoneId.of("UTC+2");
    final ZoneId serverZoneId = ZoneId.of("UTC+1");
    final ZonedDateTime localDateTime = ZonedDateTime.of(LocalDateTime.of(2014, 10, 13, 12, 0), serverZoneId);
    final ZoneId utc = ZoneId.of("UTC");
    final ZonedDateTime UTCDateTime = ZonedDateTime.of(LocalDateTime.of(2014, 10, 13, 10, 0), utc);
    final DateTimeModification modification = new DateTimeModification(localDateTime.toLocalDate(), localDateTime.toLocalTime());

    describe("DateTimeModification.toDate", it -> {
        it.should("subtract two hour from the user date", expect -> expect.that(modification.toDate(myZoneId)).is(Date.from(UTCDateTime.toInstant())));
        it.should("subtract one hour from the server date", expect -> expect.that(modification.toDate(serverZoneId)).is(Date.from(UTCDateTime.plusHours(1).toInstant())));
    });
}}