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

import alfio.repository.*;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.join.UserOrganizationRepository;
import alfio.util.RequestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.List;

@Component
@Transactional
public class OrganizationDeleter {

    private static final Logger log = LoggerFactory.getLogger(OrganizationDeleter.class);

    private final UserOrganizationRepository userOrganizationRepository;
    private final OrganizationRepository organizationRepository;
    private final EventRepository eventRepository;
    private final EventDeleterRepository eventDeleterRepository;
    private final OrganizationDeleterRepository organizationDeleterRepository;

    public OrganizationDeleter(UserOrganizationRepository userOrganizationRepository,
                               OrganizationRepository organizationRepository,
                               EventRepository eventRepository,
                               EventDeleterRepository eventDeleterRepository,
                               OrganizationDeleterRepository organizationDeleterRepository) {
        this.userOrganizationRepository = userOrganizationRepository;
        this.organizationRepository = organizationRepository;
        this.eventRepository = eventRepository;
        this.eventDeleterRepository = eventDeleterRepository;
        this.organizationDeleterRepository = organizationDeleterRepository;
    }


    public boolean deleteOrganization(int organizationId, Principal principal) {
        boolean isAdmin = RequestUtils.isAdmin(principal) || RequestUtils.isSystemApiKey(principal);
        if (isAdmin) {
            var originalOrg = organizationRepository.getById(organizationId);
            log.warn("Delete organization {} ({}) initiated by user {}", organizationId, originalOrg.getName(), principal.getName());
            var disabledEventIds = eventRepository.disableEventsForOrganization(organizationId);
            if (!disabledEventIds.isEmpty()) {
                log.warn("deleting {} events linked to organization {}", disabledEventIds.size(), organizationId);
                disabledEventIds.forEach(eventDeleterRepository::deleteAllForEvent);
            }
            List<Integer> organizationIds = List.of(organizationId);
            organizationDeleterRepository.deleteFieldValues(organizationIds);
            organizationDeleterRepository.deleteFieldDescription(organizationIds);
            organizationDeleterRepository.deleteFieldConfiguration(organizationIds);
            int users = userOrganizationRepository.cleanupOrganization(organizationId);
            log.warn("removed {} user(s) from organization {}", users, organizationId);
            organizationDeleterRepository.deleteEmptyOrganizations(organizationIds);
            return true;
        }
        return false;
    }


}
