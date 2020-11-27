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
package alfio.test.util;

import alfio.util.ClockProvider;
import lombok.experimental.UtilityClass;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

@UtilityClass
public class TestUtil {

    public static final ClockProvider FIXED_TIME_CLOCK;

    static {
        var todayAt10Sharp = LocalDateTime.now(Clock.systemUTC())
            .withHour(10)
            .withMinute(0)
            .toInstant(ZoneOffset.UTC);
        FIXED_TIME_CLOCK = ClockProvider.init(Clock.fixed(todayAt10Sharp, ZoneId.of("Europe/Zurich")));
    }

    public static ClockProvider clockProvider() {
        return FIXED_TIME_CLOCK;
    }
}
