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

import java.time.Duration;
import java.time.LocalTime;

public class HoursRange {
    private final LocalTime start;
    private final LocalTime end;

    public HoursRange(LocalTime start, LocalTime end) {
        this.start = start;
        this.end = end;
    }

    public boolean includes(LocalTime localTime) {
        return Duration.between(localTime, start).isNegative() && Duration.between(end, localTime).isNegative();
    }

    public int getDistanceInHours(LocalTime localTime) {
        if(includes(localTime)) {
            return 0;
        }
        Duration distanceFromStart = Duration.between(localTime, start);
        if(distanceFromStart.isNegative()) {
            return 24 + (int) distanceFromStart.toHours();
        }
        return (int) distanceFromStart.toHours();
    }
}
