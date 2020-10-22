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

import org.apache.commons.lang3.Validate;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Provides an application-wide {@link Clock}, which will return the current Instant in the
 * Event's timezone (or UTC for system-wide operations) when invoked in the application
 * or a fixed time when invoked in the tests (today @ 10:00 AM, Zurich time)
 */
public class ClockProvider {

    private static ClockHolder HOLDER;

    private ClockProvider(Clock clock) {
        HOLDER = new ClockHolder(clock);
    }

    public static ClockProvider init(Clock clock) {
        Validate.isTrue(HOLDER == null, "Clock has already been set");
        return new ClockProvider(clock);
    }

    public static Clock clock() {
        return Objects.requireNonNull(HOLDER).clock;
    }

    public Clock getClock() {
        return HOLDER.clock;
    }

    public Clock withZone(ZoneId zoneId) {
        return HOLDER.clock.withZone(zoneId);
    }

    private static class ClockHolder {
        private final Clock clock;

        private ClockHolder(Clock clock) {
            this.clock = clock;
        }
    }
}
