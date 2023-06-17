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
import alfio.manager.support.AccessDeniedException;
import alfio.model.EventAndOrganizationId;
import alfio.model.PurchaseContext;
import alfio.model.modification.GroupModification;
import alfio.model.user.Role;
import alfio.repository.*;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.UserRepository;
import alfio.repository.user.join.UserOrganizationRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.*;
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
    private final SubscriptionRepository subscriptionRepository;
    private final TicketReservationRepository reservationRepository;
    private final TicketRepository ticketRepository;
    private final BillingDocumentRepository billingDocumentRepository;
    private final GroupRepository groupRepository;
    private final TicketCategoryRepository ticketCategoryRepository;

    public AccessService(UserRepository userRepository,
                         AuthorityRepository authorityRepository,
                         UserOrganizationRepository userOrganizationRepository,
                         EventRepository eventRepository,
                         SubscriptionRepository subscriptionRepository,
                         TicketReservationRepository reservationRepository,
                         TicketRepository ticketRepository,
                         BillingDocumentRepository billingDocumentRepository,
                         GroupRepository groupRepository,
                         TicketCategoryRepository ticketCategoryRepository) {
        this.userRepository = userRepository;
        this.authorityRepository = authorityRepository;
        this.userOrganizationRepository = userOrganizationRepository;
        this.eventRepository = eventRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.reservationRepository = reservationRepository;
        this.ticketRepository = ticketRepository;
        this.billingDocumentRepository = billingDocumentRepository;
        this.groupRepository = groupRepository;
        this.ticketCategoryRepository = ticketCategoryRepository;
    }

    public void checkUserAccess(Principal principal, int userId) {
        throw new AccessDeniedException();
    }

    public void checkOrganizationOwnership(Principal principal, int organizationId) {
        if (principal == null) {
            log.trace("No user present, we will allow it");
            return;
        }
        if (isSystemApiUser(principal)) {
            log.trace("Allowing ownership to Organization {} to System API Key", organizationId);
            return;
        }
        if (isOwnerOfOrganization(principal, organizationId)) {
            log.trace("Allowing ownership to Organization {} to user {}", organizationId, principal.getName());
            return;
        }
        log.warn("User {} don't have ownership to organizationId {}", principal.getName(), organizationId);
        throw new AccessDeniedException(); //"User " + principal.getName() + " don't have ownership to organizationId " + organizationId
    }

    public void ensureAdmin(Principal principal) {
        if (!isAdmin(principal)) {
            throw new AccessDeniedException();
        }
    }

    public EventAndOrganizationId checkEventOwnership(Principal principal, int eventId) {
        var eventAndOrgId = eventRepository.findEventAndOrganizationIdById(eventId);
        checkOrganizationOwnership(principal, eventAndOrgId.getOrganizationId());
        return eventAndOrgId;
    }

    public EventAndOrganizationId checkEventOwnership(Principal principal, String eventShortName) {
        var eventAndOrgId = eventRepository.findOptionalEventAndOrganizationIdByShortName(eventShortName)
            .orElseThrow(AccessDeniedException::new);
        checkOrganizationOwnership(principal, eventAndOrgId.getOrganizationId());
        return eventAndOrgId;
    }

    public EventAndOrganizationId checkCategoryOwnership(Principal principal, int eventId, int categoryId) {
        var eventAndOrganizationId = checkEventOwnership(principal, eventId);
        if (!Boolean.TRUE.equals(ticketCategoryRepository.checkCategoryExistsForEvent(categoryId, eventAndOrganizationId.getId()))) {
            throw new AccessDeniedException();
        }
        return eventAndOrganizationId;
    }

    public void checkCategoryOwnership(Principal principal, String eventShortName, int categoryId) {
        var eventAndOrganizationId = checkEventOwnership(principal, eventShortName);
        if (!Boolean.TRUE.equals(ticketCategoryRepository.checkCategoryExistsForEvent(categoryId, eventAndOrganizationId.getId()))) {
            throw new AccessDeniedException();
        }
    }

    public EventAndOrganizationId checkEventOwnership(Principal principal, String eventShortName, int organizationId) {
        var eventAndOrganizationId = checkEventOwnership(principal, eventShortName);
        int orgId = eventAndOrganizationId.getOrganizationId();
        if (orgId != organizationId) {
            throw new AccessDeniedException();
        }
        return eventAndOrganizationId;
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


    public void checkReservationOwnership(Principal principal,
                                          PurchaseContext.PurchaseContextType purchaseContextType,
                                          String publicIdentifier,
                                          String reservationId) {
        if (purchaseContextType == PurchaseContext.PurchaseContextType.event) {
            checkReservationOwnershipForEvent(principal, publicIdentifier, reservationId);
        } else {
            var subscriptionDescriptor = subscriptionRepository.findDescriptorByReservationId(reservationId)
                .orElseThrow(AccessDeniedException::new);
            checkOrganizationOwnership(principal, subscriptionDescriptor.getOrganizationId());
            if (!subscriptionDescriptor.getPublicIdentifier().equals(publicIdentifier)) {
                throw new AccessDeniedException();
            }
        }
    }

    public void checkPurchaseContextOwnership(Principal principal,
                                                         PurchaseContext.PurchaseContextType purchaseContextType,
                                                         String publicIdentifier) {
        if (purchaseContextType == PurchaseContext.PurchaseContextType.event) {
            checkEventOwnership(principal, publicIdentifier);
        } else {
            checkSubscriptionDescriptorOwnership(principal, publicIdentifier);
        }
    }

    public void checkSubscriptionDescriptorOwnership(Principal principal, String publicIdentifier) {
        int organizationId = subscriptionRepository.findOrganizationIdForDescriptor(UUID.fromString(publicIdentifier))
            .orElseThrow(AccessDeniedException::new);
        checkOrganizationOwnership(principal, organizationId);
    }

    private void checkReservationOwnershipForEvent(Principal principal, String publicIdentifier, String reservationId) {
        var event = eventRepository.findOptionalEventAndOrganizationIdByShortName(publicIdentifier)
            .orElseThrow(AccessDeniedException::new);
        checkOrganizationOwnership(principal, event.getOrganizationId());
        var reservations = reservationRepository.getReservationIdAndEventId(List.of(reservationId));
        if (reservations.size() != 1 || reservations.get(0).getEventId() != event.getId()) {
            throw new AccessDeniedException();
        }
    }

    public void checkTicketOwnership(Principal principal,
                                     String publicIdentifier,
                                     String reservationId,
                                     int ticketId) {
        checkReservationOwnershipForEvent(principal, publicIdentifier, reservationId);
        var tickets = ticketRepository.findByIds(List.of(ticketId));
        if (tickets.size() != 1 || !tickets.get(0).getTicketsReservationId().equals(reservationId)) {
            throw new AccessDeniedException();
        }
    }

    public void checkBillingDocumentOwnership(Principal principal,
                                              PurchaseContext.PurchaseContextType purchaseContextType,
                                              String publicIdentifier,
                                              String reservationId,
                                              long billingDocumentId) {
        checkReservationOwnership(principal, purchaseContextType, publicIdentifier, reservationId);
        if (!Boolean.TRUE.equals(billingDocumentRepository.checkBillingDocumentExistsForReservation(billingDocumentId, reservationId))) {
            throw new AccessDeniedException();
        }
    }

    public void checkGroupLinkOwnership(Principal principal, int groupLinkId, int organizationId, int eventId, Integer categoryId) {
        var eventAndOrgId = checkEventOwnership(principal, eventId);
        if (eventAndOrgId.getOrganizationId() != organizationId) {
            throw new AccessDeniedException();
        }
        if (!Boolean.TRUE.equals(groupRepository.checkGroupLinkExists(groupLinkId, organizationId, eventId, categoryId))) {
            throw new AccessDeniedException();
        }
    }
    public void checkGroupOwnership(Principal principal, int groupId, int organizationId) {
        checkOrganizationOwnership(principal, organizationId);
        if (!Boolean.TRUE.equals(groupRepository.checkGroupExists(groupId, organizationId))) {
            throw new AccessDeniedException();
        }
    }

    public void checkGroupUpdateRequest(Principal principal, int groupId, int organizationId, GroupModification groupModification) {
        if (groupModification.getOrganizationId() != organizationId || groupModification.getId() != groupId) {
            throw new AccessDeniedException();
        }
        checkGroupOwnership(principal, groupId, organizationId);
    }

    public void checkGroupCreateRequest(Principal principal, int organizationId, GroupModification groupModification) {
        if (groupModification.getOrganizationId() != organizationId) {
            throw new AccessDeniedException();
        }
        checkOrganizationOwnership(principal, organizationId);
    }
}
