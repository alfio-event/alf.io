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
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.model.user.User;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
@DependsOn("migrator")
@Profile("!"+ Initializer.PROFILE_DISABLE_JOBS)
@AllArgsConstructor
@Log4j2
public class SpringScheduledJobs {

    private static final int ONE_MINUTE = 1000 * 60;

    private final ConfigurationManager configurationManager;
    private final Environment environment;
    private final EventManager eventManager;
    private final FileUploadManager fileUploadManager;
    private final UserManager userManager;

    //cron each minute: "0 0/1 * 1/1 * ? *"

    @Scheduled(fixedRate = ONE_MINUTE * 60)
    public void cleanupUnreferencedBlobFiles() {
        log.trace("running job cleanupUnreferencedBlobFiles");
        fileUploadManager.cleanupUnreferencedBlobFiles();
    }


    //run each hour
    @Scheduled(cron = "0 0 0/1 1/1 * ? *")
    public void cleanupForDemoMode() {
        if(environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_DEMO))) {
            int expirationDate = configurationManager.getIntConfigValue(Configuration.getSystemConfiguration(ConfigurationKeys.DEMO_MODE_ACCOUNT_EXPIRATION_DAYS), 20);
            List<Integer> userIds = userManager.disableAccountsOlderThan(DateUtils.addDays(new Date(), -expirationDate), User.Type.DEMO);
            if(!userIds.isEmpty()) {
                eventManager.disableEventsFromUsers(userIds);
            }
            log.trace("running job cleanupForDemoMode");
        }
    }
}
