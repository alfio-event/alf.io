package alfio.manager.system;

import alfio.test.util.TestUtil;
import alfio.util.ClockProvider;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import static alfio.test.util.TestUtil.FIXED_TIME_CLOCK;
import static alfio.test.util.TestUtil.clockProvider;
import static org.junit.jupiter.api.Assertions.*;

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