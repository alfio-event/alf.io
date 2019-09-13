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
package alfio.util;

import lombok.experimental.UtilityClass;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAdjuster;
import java.util.*;

@UtilityClass
public class WorkingDaysAdjusters {

    private static final Set<DayOfWeek> MON_FRI = EnumSet.complementOf(EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY));
    private static final List<HoursRange> ALL_DAY = Collections.singletonList(new HoursRange(LocalTime.of(8, 0, 0), LocalTime.of(20, 0, 0)));

    public static TemporalAdjuster defaultWorkingDays() {
        return temporal -> adjust(temporal, MON_FRI, ALL_DAY);
    }

    private static Temporal adjust(Temporal in, Set<DayOfWeek> dayOfWeeks, List<HoursRange> hoursRanges) {
        DayOfWeek dayOfWeek = DayOfWeek.from(in);
        LocalTime localTime = LocalTime.from(in);
        boolean dayInRange = dayOfWeeks.contains(dayOfWeek);
        boolean hourInRange = hoursRanges.stream().anyMatch(hr -> hr.includes(localTime));
        if(dayInRange && hourInRange) {
            return in;
        }
        Temporal result = in;
        if(!dayInRange) {
            do {
                result = result.plus(1, ChronoUnit.DAYS);
            } while(!dayOfWeeks.contains(DayOfWeek.from(result)));
        }
        if(!hourInRange) {
            OptionalInt distance = hoursRanges.stream()
                    .mapToInt(hr -> hr.getDistanceInHours(localTime))
                    .sorted()
                    .findFirst();
            result = result.plus(distance.orElseThrow(IllegalStateException::new), ChronoUnit.HOURS);
        }
        return result;
    }
}
