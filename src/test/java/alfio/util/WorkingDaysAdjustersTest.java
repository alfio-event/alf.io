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

import com.insightfullogic.lambdabehave.JunitSuiteRunner;
import org.junit.runner.RunWith;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.Month;

import static com.insightfullogic.lambdabehave.Suite.describe;

@RunWith(JunitSuiteRunner.class)
public class WorkingDaysAdjustersTest {{
    describe("Adjust to next week day", it -> {

        it.should("adjust date to next monday, same hour", expect -> {
            LocalDateTime localDateTime = LocalDateTime.of(2015, Month.JUNE, 20, 10, 0);
            LocalDateTime adjusted = localDateTime.with(WorkingDaysAdjusters.defaultWorkingDays());
            expect.that(adjusted).isNotNull();
            expect.that(adjusted.getDayOfWeek()).is(DayOfWeek.MONDAY);
            expect.that(adjusted.getDayOfMonth()).is(22);
            expect.that(adjusted.getHour()).is(10);
            expect.that(adjusted.getMinute()).is(0);
        });

        it.should("adjust date to next monday and time to next hour", expect -> {
            LocalDateTime localDateTime = LocalDateTime.of(2015, Month.JUNE, 20, 7, 0);
            LocalDateTime adjusted = localDateTime.with(WorkingDaysAdjusters.defaultWorkingDays());
            expect.that(adjusted).isNotNull();
            expect.that(adjusted.getDayOfWeek()).is(DayOfWeek.MONDAY);
            expect.that(adjusted.getDayOfMonth()).is(22);
            expect.that(adjusted.getHour()).is(8);
            expect.that(adjusted.getMinute()).is(0);
        });

        it.should("adjust date to to next hour", expect -> {
            LocalDateTime localDateTime = LocalDateTime.of(2015, Month.JUNE, 17, 7, 0);
            LocalDateTime adjusted = localDateTime.with(WorkingDaysAdjusters.defaultWorkingDays());
            expect.that(adjusted).isNotNull();
            expect.that(adjusted.getDayOfWeek()).is(DayOfWeek.WEDNESDAY);
            expect.that(adjusted.getDayOfMonth()).is(17);
            expect.that(adjusted.getHour()).is(8);
            expect.that(adjusted.getMinute()).is(0);
        });

        it.should("do nothing if the date is within range (start)", expect -> {
            LocalDateTime localDateTime = LocalDateTime.of(2015, Month.JUNE, 17, 8, 0);
            LocalDateTime adjusted = localDateTime.with(WorkingDaysAdjusters.defaultWorkingDays());
            expect.that(adjusted).isNotNull();
            expect.that(adjusted.getDayOfWeek()).is(DayOfWeek.WEDNESDAY);
            expect.that(adjusted.getDayOfMonth()).is(17);
            expect.that(adjusted.getHour()).is(8);
            expect.that(adjusted.getMinute()).is(0);
        });

        it.should("do nothing if the date is within range (middle)", expect -> {
            LocalDateTime localDateTime = LocalDateTime.of(2015, Month.JUNE, 17, 13, 0);
            LocalDateTime adjusted = localDateTime.with(WorkingDaysAdjusters.defaultWorkingDays());
            expect.that(adjusted).isNotNull();
            expect.that(adjusted.getDayOfWeek()).is(DayOfWeek.WEDNESDAY);
            expect.that(adjusted.getDayOfMonth()).is(17);
            expect.that(adjusted.getHour()).is(13);
            expect.that(adjusted.getMinute()).is(0);
        });

        it.should("do nothing if the date is within range (end)", expect -> {
            LocalDateTime localDateTime = LocalDateTime.of(2015, Month.JUNE, 17, 19, 59);
            LocalDateTime adjusted = localDateTime.with(WorkingDaysAdjusters.defaultWorkingDays());
            expect.that(adjusted).isNotNull();
            expect.that(adjusted.getDayOfWeek()).is(DayOfWeek.WEDNESDAY);
            expect.that(adjusted.getDayOfMonth()).is(17);
            expect.that(adjusted.getHour()).is(19);
            expect.that(adjusted.getMinute()).is(59);
        });

    });
}}