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
import alfio.model.system.ConfigurationKeys;
import alfio.model.user.User;
import alfio.repository.EventDeleterRepository;
import alfio.repository.EventRepository;
import alfio.repository.OrganizationDeleterRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.repository.user.join.UserOrganizationRepository;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@AllArgsConstructor
@Log4j2
public class DemoModeDataManager {
    private final UserRepository userRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final OrganizationRepository organizationRepository;
    private final EventDeleterRepository eventDeleterRepository;
    private final EventRepository eventRepository;
    private final ConfigurationManager configurationManager;
    private final OrganizationDeleterRepository organizationDeleterRepository;

    public List<Integer> findExpiredUsers(Date date) {
        return userRepository.findUsersToDeleteOlderThan(date, Set.of(User.Type.DEMO.name(), User.Type.API_KEY.name()));
    }

    public void deleteAccounts(List<Integer> userIds) {
        if(!userIds.isEmpty()) {
            log.info("found {} expired users", userIds.size());
            var organizationIds = userOrganizationRepository.findOrganizationsForUsers(userIds);
            var disabledEventIds = eventRepository.disableEventsForUsers(userIds);
            log.info("found {} events to delete", disabledEventIds.size());
            disabledEventIds.forEach(eventDeleterRepository::deleteAllForEvent);
            userIds.forEach(userRepository::deleteUserAndReferences);
            organizationDeleterRepository.deleteEmptyOrganizations(organizationIds);
        }
    }

    // runs every day at 1:00 AM
    @Scheduled(cron = "0 0 1 * * *")
    @Profile("!" + Initializer.PROFILE_DISABLE_JOBS)
    @Transactional
    public void cleanupForDemoMode() {
        log.info("########## running job cleanupForDemoMode");
        try {
            int expirationDate = configurationManager.getForSystem(ConfigurationKeys.DEMO_MODE_ACCOUNT_EXPIRATION_DAYS).getValueAsIntOrDefault(20);
            List<Integer> userIds = findExpiredUsers(DateUtils.addDays(new Date(), -expirationDate));
            if (!userIds.isEmpty()) {
                deleteAccounts(userIds);
            }
        } finally {
            log.info("########## end job cleanupForDemoMode");
        }
    }

}
