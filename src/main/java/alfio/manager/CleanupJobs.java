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
package alfio.manager;

import alfio.config.Initializer;
import alfio.config.support.PlatformProvider;
import alfio.repository.system.VacuumQuartzTables;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!"+ Initializer.PROFILE_DISABLE_JOBS)
@Log4j2
public class CleanupJobs {

    private final VacuumQuartzTables vacuumQuartzTables;
    private final PlatformProvider platform;
    private final Environment environment;

    @Autowired
    public CleanupJobs(VacuumQuartzTables vacuumQuartzTables, PlatformProvider platform, Environment environment) {
        this.vacuumQuartzTables = vacuumQuartzTables;
        this.platform = platform;
        this.environment = environment;
    }

    /* The vacuum will be scheduled for 00:00.00 GMT */
    @Scheduled(cron = "0 0 0 * * *", zone = "GMT")
    public void vacuumQuartzTables() {
        if(PlatformProvider.PGSQL.equals(platform.getDialect(environment))) {
            log.info("vacuuming quartz tables");
            vacuumQuartzTables.vacuumQuartzTables();
            log.info("end vacuuming quartz tables");
        }
    }
}
