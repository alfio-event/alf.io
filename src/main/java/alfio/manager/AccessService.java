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

import alfio.config.authentication.support.APITokenAuthentication;
import alfio.model.user.Role;
import alfio.repository.EventRepository;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.UserRepository;
import alfio.repository.user.join.UserOrganizationRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static alfio.config.authentication.support.AuthenticationConstants.SYSTEM_API_CLIENT;

/**
 * Centralized service for checking if a given Principal can
 *  - read a given resource with a specific id (example, get data of a user with a specific id)
 *  - update/delete a given resource with a specific id (example: update/delete an event)
 *  - do some specific action which affect a resource with a specific id (example: add a new event in a given organization)
 */
@Service
@Transactional(readOnly = true)
public class AccessService {

    private static final Logger log = LogManager.getLogger(AccessService.class);

    private final UserRepository userRepository;

    private final EventRepository eventRepository;

    private final AuthorityRepository authorityRepository;
    private final UserOrganizationRepository userOrganizationRepository;

    public AccessService(UserRepository userRepository,
                         AuthorityRepository authorityRepository,
                         UserOrganizationRepository userOrganizationRepository,
                         EventRepository eventRepository) {
        this.userRepository = userRepository;
        this.authorityRepository = authorityRepository;
        this.userOrganizationRepository = userOrganizationRepository;
        this.eventRepository = eventRepository;
    }

    public void checkUserAccess(Principal principal, int userId) {
        throw new IllegalStateException("FIXME");
    }

    public void checkOrganizationAccess(Principal principal, int organizationId) {
        if (principal == null) {
            log.trace("No user present, we will allow it");
            return;
        }
        if (isSystemApiUser(principal)) {
            log.trace("Allowing access to Organization {} to System API Key", organizationId);
            return;
        }
        if (isOwnerOfOrganization(principal, organizationId)) {
            log.trace("Allowing access to Organization {} to user {}", organizationId, principal.getName());
            return;
        }
        log.warn("User {} don't have access to organizationId {}", principal.getName(), organizationId);
        throw new IllegalArgumentException("User " + principal.getName() + " don't have access to organizationId " + organizationId);
    }

    public void checkEventAccess(Principal principal, int eventId) {
        var orgId = eventRepository.findOrganizationIdByEventId(eventId);
        checkOrganizationAccess(principal, orgId);
    }

    public void checkEventAccess(Principal principal, String eventShortName) {
        var orgId = eventRepository.findOrganizationIdByShortName(eventShortName);
        checkOrganizationAccess(principal, orgId);
    }

    private static boolean isSystemApiUser(Principal principal) {
        return principal instanceof APITokenAuthentication
            && ((APITokenAuthentication)principal).getAuthorities().stream()
            .allMatch(authority -> authority.getAuthority().equals("ROLE_" + SYSTEM_API_CLIENT));
    }

    private boolean isAdmin(Principal user) {
        return checkRole(user, Collections.singleton(Role.ADMIN));
    }

    private boolean isOwner(Principal principal) {
        return checkRole(principal, EnumSet.of(Role.ADMIN, Role.OWNER, Role.API_CONSUMER));
    }
    private boolean checkRole(Principal principal, Set<Role> expectedRoles) {
        var roleNames = expectedRoles.stream().map(Role::getRoleName).collect(Collectors.toSet());
        return authorityRepository.checkRole(principal.getName(), roleNames);
    }

    private boolean isOwnerOfOrganization(Principal principal, int organizationId) {
        return userRepository.findIdByUserName(principal.getName())
            .filter(userId ->
                    isAdmin(principal) ||
                    (isOwner(principal) && userOrganizationRepository.userIsInOrganization(userId, organizationId)))
            .isPresent();
    }


}