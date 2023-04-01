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

import alfio.config.Initializer;
import alfio.util.ClockProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

@Component
@Profile("!" + Initializer.PROFILE_DISABLE_JOBS)
public class AdminJobManagerScheduler {

    private static final Logger log = LoggerFactory.getLogger(AdminJobManagerScheduler.class);
    private final AdminJobManager adminJobManager;
    private final ClockProvider clockProvider;

    public AdminJobManagerScheduler(AdminJobManager adminJobManager,
                                    ClockProvider clockProvider) {
        this.adminJobManager = adminJobManager;
        this.clockProvider = clockProvider;
    }

    @Scheduled(fixedDelay = 1000L)
    void processPendingExtensionRetry() {
        log.trace("Processing pending extensions retry");
        adminJobManager.processPendingExtensionRetry(ZonedDateTime.now(clockProvider.getClock()));
        log.trace("done processing pending extensions retry");
    }

    @Scheduled(fixedDelay = 1000L)
    void processPendingReservationsRetry() {
        log.trace("Processing pending reservations retry");
        adminJobManager.processPendingReservationsRetry(ZonedDateTime.now(clockProvider.getClock()));
        log.trace("done processing pending reservations retry");
    }

    @Scheduled(fixedDelay = 60 * 1000)
    void processPendingRequests() {
        log.trace("Processing pending requests");
        adminJobManager.processPendingRequests();
        log.trace("done processing pending requests");
    }

    @Scheduled(cron = "#{environment.acceptsProfiles('dev') ? '0 * * * * *' : '0 0 0 * * *'}")
    void cleanupExpiredRequests() {
        adminJobManager.cleanupExpiredRequests();
    }
}
