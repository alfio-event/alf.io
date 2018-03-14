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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.Month;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("Adjust to next week day")
public class WorkingDaysAdjustersTest {

    @Test
    @DisplayName("adjust date to next monday, same hour")
    void adjustToNextMondaySameHour() {
        LocalDateTime localDateTime = LocalDateTime.of(2015, Month.JUNE, 20, 10, 0);
        LocalDateTime adjusted = localDateTime.with(WorkingDaysAdjusters.defaultWorkingDays());
        assertNotNull(adjusted);
        assertEquals(DayOfWeek.MONDAY, adjusted.getDayOfWeek());
        assertEquals(22, adjusted.getDayOfMonth());
        assertEquals(10, adjusted.getHour());
        assertEquals(0, adjusted.getMinute());
    }

    @Test
    @DisplayName("adjust date to next monday and time to next hour")
    void adjustToNextMondayNextHour() {
        LocalDateTime localDateTime = LocalDateTime.of(2015, Month.JUNE, 20, 7, 0);
        LocalDateTime adjusted = localDateTime.with(WorkingDaysAdjusters.defaultWorkingDays());
        assertNotNull(adjusted);
        assertEquals(DayOfWeek.MONDAY, adjusted.getDayOfWeek());
        assertEquals(22, adjusted.getDayOfMonth());
        assertEquals(8, adjusted.getHour());
        assertEquals(0, adjusted.getMinute());
    }

    @Test
    @DisplayName("adjust date to to next hour")
    void adjustToNextHour() {
        LocalDateTime localDateTime = LocalDateTime.of(2015, Month.JUNE, 17, 7, 0);
        LocalDateTime adjusted = localDateTime.with(WorkingDaysAdjusters.defaultWorkingDays());
        assertNotNull(adjusted);
        assertEquals(DayOfWeek.WEDNESDAY, adjusted.getDayOfWeek());
        assertEquals(17, adjusted.getDayOfMonth());
        assertEquals(8, adjusted.getHour());
        assertEquals(0, adjusted.getMinute());
    }

    @Test
    @DisplayName("do nothing if the date is within range (start)")
    void doNothingIfDateWithinRange() {
        LocalDateTime localDateTime = LocalDateTime.of(2015, Month.JUNE, 17, 8, 0);
        LocalDateTime adjusted = localDateTime.with(WorkingDaysAdjusters.defaultWorkingDays());
        assertNotNull(adjusted);
        assertEquals(DayOfWeek.WEDNESDAY, adjusted.getDayOfWeek());
        assertEquals(17, adjusted.getDayOfMonth());
        assertEquals(8, adjusted.getHour());
        assertEquals(0, adjusted.getMinute());
    }

    @Test
    @DisplayName("do nothing if the date is within range (middle)")
    void doNothingIfDateWithinRangeMiddle() {
        LocalDateTime localDateTime = LocalDateTime.of(2015, Month.JUNE, 17, 13, 0);
        LocalDateTime adjusted = localDateTime.with(WorkingDaysAdjusters.defaultWorkingDays());
        assertNotNull(adjusted);
        assertEquals(DayOfWeek.WEDNESDAY, adjusted.getDayOfWeek());
        assertEquals(17, adjusted.getDayOfMonth());
        assertEquals(13, adjusted.getHour());
        assertEquals(0, adjusted.getMinute());
    }

    @Test
    @DisplayName("do nothing if the date is within range (end)")
    void doNothingIfDateWithinRangeEnd() {
        LocalDateTime localDateTime = LocalDateTime.of(2015, Month.JUNE, 17, 19, 59);
        LocalDateTime adjusted = localDateTime.with(WorkingDaysAdjusters.defaultWorkingDays());
        assertNotNull(adjusted);
        assertEquals(DayOfWeek.WEDNESDAY, adjusted.getDayOfWeek());
        assertEquals(17, adjusted.getDayOfMonth());
        assertEquals(19, adjusted.getHour());
        assertEquals(59, adjusted.getMinute());
    }
}