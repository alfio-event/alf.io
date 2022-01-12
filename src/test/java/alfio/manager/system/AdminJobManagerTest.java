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
package alfio.manager.system;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import static alfio.test.util.TestUtil.clockProvider;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminJobManagerTest {

    @Test
    void getNextExecution() {
        // next execution date contains an exponential backoff
        for (int attempt = 0, backoffSecs = 2; attempt <= AdminJobManager.MAX_ATTEMPTS; attempt++, backoffSecs *= 2) {
            var expectedDate = ZonedDateTime.now(clockProvider().getClock())
                .plusSeconds(backoffSecs)
                .truncatedTo(ChronoUnit.MILLIS);
            var actualDate = AdminJobManager.getNextExecution(attempt)
                .truncatedTo(ChronoUnit.MILLIS);
            assertTrue(Duration.between(expectedDate, actualDate).isZero());
        }
    }
}