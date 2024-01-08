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
import alfio.controller.form.ReservationCreate;
import alfio.manager.support.AccessDeniedException;
import alfio.model.EventAndOrganizationId;
import alfio.model.PurchaseContext;
import alfio.model.modification.AdditionalServiceReservationModification;
import alfio.model.modification.GroupModification;
import alfio.model.modification.PromoCodeDiscountModification;
import alfio.model.modification.ReservationRequest;
import alfio.model.user.Organization;
import alfio.model.user.Role;
import alfio.repository.*;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.OrganizationRepository;
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
import static alfio.manager.user.UserManager.ADMIN_USERNAME;
import static java.util.Objects.requireNonNullElse;

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
    private final PromoCodeDiscountRepository promoCodeDiscountRepository;
    private final OrganizationRepository organizationRepository;
    private final AdditionalServiceRepository additionalServiceRepository;
    private final WaitingQueueRepository waitingQueueRepository;
    private final PurchaseContextFieldRepository purchaseContextFieldRepository;

    public AccessService(UserRepository userRepository,
                         AuthorityRepository authorityRepository,
                         UserOrganizationRepository userOrganizationRepository,
                         EventRepository eventRepository,
                         SubscriptionRepository subscriptionRepository,
                         TicketReservationRepository reservationRepository,
                         TicketRepository ticketRepository,
                         BillingDocumentRepository billingDocumentRepository,
                         GroupRepository groupRepository,
                         TicketCategoryRepository ticketCategoryRepository,
                         PromoCodeDiscountRepository promoCodeDiscountRepository,
                         OrganizationRepository organizationRepository,
                         AdditionalServiceRepository additionalServiceRepository,
                         WaitingQueueRepository waitingQueueRepository,
                         PurchaseContextFieldRepository purchaseContextFieldRepository) {
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
        this.promoCodeDiscountRepository = promoCodeDiscountRepository;
        this.organizationRepository = organizationRepository;
        this.additionalServiceRepository = additionalServiceRepository;
        this.waitingQueueRepository = waitingQueueRepository;
        this.purchaseContextFieldRepository = purchaseContextFieldRepository;
    }

    public static final Set<Role> MEMBERSHIP_ROLES = Set.of(Role.ADMIN, Role.OWNER, Role.API_CONSUMER, Role.SUPERVISOR);

    public static final Set<Role> CHECKIN_ROLES = Set.of(Role.ADMIN, Role.OWNER, Role.API_CONSUMER, Role.SUPERVISOR, Role.OPERATOR);

    public void checkAccessToUser(Principal principal, Integer userId) {
        if (userId == null || principal == null) {
            throw new AccessDeniedException();
        }
        if (isAdmin(principal) || isSystemApiUser(principal)) {
            log.trace("principal {} identified as ADMIN is allowed to retrieve user details for user {}", principal.getName(), userId);
            return;
        }
        var targetUser = userRepository.findOptionalById(userId).orElseThrow(AccessDeniedException::new);
        // target user cannot be an admin because current user is NOT an admin
        if (targetUser.getUsername().equals(ADMIN_USERNAME) || checkRole(targetUser.getUsername(), EnumSet.of(Role.ADMIN))) {
            throw new AccessDeniedException();
        }
        var targetUserOrgs = organizationRepository.findAllForUser(targetUser.getUsername());
        if (targetUserOrgs.size() != 1) {
            log.warn("denied access to user {} which is member of {} organizations", targetUser.getUsername(), targetUserOrgs.size());
            throw new AccessDeniedException();
        }
        for (Organization targetUserOrg : targetUserOrgs) {
            checkOrganizationOwnership(principal, targetUserOrg.getId());
        }
    }

    public void checkOrganizationMembership(Principal principal, int organizationId, Set<Role> roles) {
        if (principal == null) {
            log.trace("No user present, we will allow it");
            return;
        }
        if (isSystemApiUser(principal)) {
            log.trace("Allowing ownership to Organization {} to System API Key", organizationId);
            return;
        }
        if (hasRole(principal, organizationId, roles)) {
            log.trace("Allowing ownership to Organization {} to user {}", organizationId, principal.getName());
            return;
        }

        log.warn("User {} is NOT an owner or supervisor of organizationId {}", principal.getName(), organizationId);
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
        log.warn("User {} is NOT an owner of organizationId {}", principal.getName(), organizationId);
        throw new AccessDeniedException(); //"User " + principal.getName() + " don't have ownership to organizationId " + organizationId
    }

    public void ensureAdmin(Principal principal) {
        if (!isAdmin(principal)) {
            throw new AccessDeniedException();
        }
    }

    public void ensureSystemApiKey(Principal principal) {
        if (!isSystemApiUser(principal)) {
            throw new AccessDeniedException();
        }
    }

    public EventAndOrganizationId checkEventOwnership(Principal principal, int eventId) {
        var eventAndOrgId = eventRepository.findEventAndOrganizationIdById(eventId);
        checkOrganizationOwnership(principal, eventAndOrgId.getOrganizationId());
        return eventAndOrgId;
    }

    public EventAndOrganizationId checkEventOwnership(Principal principal, int eventId, int organizationId) {
        var eventAndOrgId = checkEventOwnership(principal, eventId);
        if (organizationId != eventAndOrgId.getOrganizationId()) {
            throw new AccessDeniedException();
        }
        return eventAndOrgId;
    }

    public EventAndOrganizationId checkEventOwnership(Principal principal, String eventShortName) {
        var eventAndOrgId = eventRepository.findOptionalEventAndOrganizationIdByShortName(eventShortName)
            .orElseThrow(AccessDeniedException::new);
        checkOrganizationOwnership(principal, eventAndOrgId.getOrganizationId());
        return eventAndOrgId;
    }

    /**
     * Access for admin, owner and supervisor (and API_CONSUMER, SYSTEM_API_CLIENT)
     * @param principal
     * @param eventShortName
     * @return
     */
    public EventAndOrganizationId checkEventMembership(Principal principal, String eventShortName, Set<Role> roles) {
        var eventAndOrgId = eventRepository.findOptionalEventAndOrganizationIdByShortName(eventShortName)
            .orElseThrow(AccessDeniedException::new);
        checkOrganizationMembership(principal, eventAndOrgId.getOrganizationId(), roles);
        return eventAndOrgId;
    }

    public EventAndOrganizationId checkEventMembership(Principal principal, int eventId, Set<Role> roles) {
        var eventAndOrgId = eventRepository.findEventAndOrganizationIdById(eventId);
        checkOrganizationMembership(principal, eventAndOrgId.getOrganizationId(), roles);
        return eventAndOrgId;
    }

    public EventAndOrganizationId checkCategoryOwnership(Principal principal, int eventId, int categoryId) {
        return checkCategoryOwnership(principal, eventId, Set.of(categoryId));
    }

    public EventAndOrganizationId checkCategoryOwnership(Principal principal, int eventId, Set<Integer> categoryIds) {
        var eventAndOrganizationId = checkEventOwnership(principal, eventId);
        if (categoryIds.size() != ticketCategoryRepository.countCategoryForEvent(categoryIds, eventAndOrganizationId.getId())) {
            throw new AccessDeniedException();
        }
        return eventAndOrganizationId;
    }

    public EventAndOrganizationId checkCategoryOwnership(Principal principal, String eventShortName, Set<Integer> categoryIds) {
        var eventAndOrganizationId = checkEventOwnership(principal, eventShortName);
        if (categoryIds.size() != ticketCategoryRepository.countCategoryForEvent(categoryIds, eventAndOrganizationId.getId())) {
            throw new AccessDeniedException();
        }
        return eventAndOrganizationId;
    }

    public EventAndOrganizationId checkCategoryOwnership(Principal principal, String eventShortName, int categoryId) {
        return checkCategoryOwnership(principal, eventShortName, Set.of(categoryId));
    }


    public void checkEventReservationCreationRequest(Principal principal,
                                                     String eventShortName,
                                                     ReservationCreate<? extends ReservationRequest> createRequest) {
        var eventAndOrganizationId = checkEventOwnership(principal, eventShortName);
        var categoryIds = createRequest.getTickets().stream().map(ReservationRequest::getTicketCategoryId).collect(Collectors.toSet());
        int eventId = eventAndOrganizationId.getId();
        if (categoryIds.size() != ticketCategoryRepository.countCategoriesBelongingToEvent(eventId, categoryIds)) {
            throw new AccessDeniedException();
        }
        var additionalServicesIds = requireNonNullElse(createRequest.getAdditionalServices(), List.<AdditionalServiceReservationModification>of()).stream()
            .map(AdditionalServiceReservationModification::getAdditionalServiceId)
            .collect(Collectors.toSet());
        if (!additionalServicesIds.isEmpty() && additionalServicesIds.size() != additionalServiceRepository.countAdditionalServicesBelongingToEvent(eventId, additionalServicesIds)) {
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
        return checkRole(principal.getName(), expectedRoles);
    }

    private boolean checkRole(String username, Set<Role> expectedRoles) {
        var roleNames = expectedRoles.stream().map(Role::getRoleName).collect(Collectors.toSet());
        return authorityRepository.checkRole(username, roleNames);
    }

    private boolean isOwnerOfOrganization(Principal principal, int organizationId) {
        return userRepository.findIdByUserName(principal.getName())
            .filter(userId ->
                    isAdmin(principal) ||
                    (isOwner(principal) && userOrganizationRepository.userIsInOrganization(userId, organizationId)))
            .isPresent();
    }

    private boolean hasRole(Principal principal, int organizationId, Set<Role> roles) {
        return userRepository.findIdByUserName(principal.getName())
            .filter(userId -> isAdmin(principal) || (checkRole(principal, roles) && userOrganizationRepository.userIsInOrganization(userId, organizationId)))
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

    public void checkReservationMembership(Principal principal,
                                          PurchaseContext.PurchaseContextType purchaseContextType,
                                          String publicIdentifier,
                                          String reservationId) {
        if (purchaseContextType == PurchaseContext.PurchaseContextType.event) {
            checkReservationMembershipForEvent(principal, publicIdentifier, reservationId, MEMBERSHIP_ROLES);
        } else {
            var subscriptionDescriptor = subscriptionRepository.findDescriptorByReservationId(reservationId)
                .orElseThrow(AccessDeniedException::new);
            checkOrganizationMembership(principal, subscriptionDescriptor.getOrganizationId(), MEMBERSHIP_ROLES);
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

    public void checkPurchaseContextOwnership(Principal principal,
                                              int organizationId,
                                              Integer eventId,
                                              UUID subscriptionDescriptorId) {
        if (eventId != null) {
            checkEventOwnership(principal, eventId, organizationId);
        } else {
            checkOrganizationOwnership(principal, organizationId);
            int subscriptionOrg = subscriptionRepository.findOrganizationIdForDescriptor(subscriptionDescriptorId)
                .orElseThrow(AccessDeniedException::new);
            if (subscriptionOrg != organizationId) {
                throw new AccessDeniedException();
            }
        }
    }

    public EventAndOrganizationId checkDescriptorsLinkRequest(Principal principal, String eventSlug, List<UUID> descriptorsToLink) {
        var event = checkEventOwnership(principal, eventSlug);
        var count = subscriptionRepository.countDescriptorsBelongingToOrganization(descriptorsToLink, event.getOrganizationId());
        if (count == null || descriptorsToLink.size() != count) {
            throw new AccessDeniedException();
        }
        return event;
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

    private void checkReservationMembershipForEvent(Principal principal, String publicIdentifier, String reservationId, Set<Role> roles) {
        var event = eventRepository.findOptionalEventAndOrganizationIdByShortName(publicIdentifier)
            .orElseThrow(AccessDeniedException::new);
        checkEventMembership(principal, event.getId(), roles);
        var reservations = reservationRepository.getReservationIdAndEventId(List.of(reservationId));
        if (reservations.size() != 1 || reservations.get(0).getEventId() != event.getId()) {
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

    public void checkAccessToPromoCode(Principal principal, int promoCodeId, PromoCodeDiscountModification payload) {
        int organizationId = checkAccessToPromoCodeEventOrganization(principal, payload.getEventId(), payload.getOrganizationId());
        if (!Boolean.TRUE.equals(promoCodeDiscountRepository.checkPromoCodeExists(promoCodeId, organizationId, payload.getEventId()))) {
            throw new AccessDeniedException();
        }
    }

    public void checkAccessToPromoCode(Principal principal, int promoCodeId) {
        var promoCode = promoCodeDiscountRepository.findOptionalById(promoCodeId).orElseThrow(AccessDeniedException::new);
        if (promoCode.getEventId() != null) {
            checkEventOwnership(principal, promoCode.getEventId(), promoCode.getOrganizationId());
        } else {
            checkOrganizationOwnership(principal, promoCode.getOrganizationId());
        }
    }

    public int checkAccessToPromoCodeEventOrganization(Principal principal, Integer eventId, Integer organizationId) {
        if (eventId == null && organizationId == null) {
            throw new AccessDeniedException();
        }
        if (eventId != null && organizationId != null) {
            return checkEventOwnership(principal, eventId, organizationId).getOrganizationId();
        } else if (eventId != null) {
            return checkEventOwnership(principal, eventId).getOrganizationId();
        } else {
            checkOrganizationOwnership(principal, organizationId);
            return organizationId;
        }
    }

    public void checkEventLinkRequest(Principal principal, String subscriptionId, List<String> eventSlugs) {
        int organizationId = subscriptionRepository.findOrganizationIdForDescriptor(UUID.fromString(subscriptionId))
            .orElseThrow(AccessDeniedException::new);
        checkOrganizationOwnership(principal, organizationId);
        if (eventSlugs.size() > 0 && eventSlugs.size() != eventRepository.countEventsInOrganization(organizationId, eventSlugs)) {
            throw new AccessDeniedException();
        }
    }

    public EventAndOrganizationId canAccessEvent(Principal principal, String eventShortName) {
        var eventAndOrgId = eventRepository.findOptionalEventAndOrganizationIdByShortName(eventShortName)
            .orElseThrow(AccessDeniedException::new);
        var user = userRepository.getByUsername(principal.getName());
        if (!userOrganizationRepository.userIsInOrganization(user.getId(), eventAndOrgId.getOrganizationId())) {
            throw new AccessDeniedException();
        }
        return eventAndOrgId;
    }

    public void canAccessTicket(Principal principal, String eventShortName, String uuid) {
        var eventAndOrgId = canAccessEvent(principal, eventShortName);
        var ticket = ticketRepository.findByUUID(uuid);
        if (ticket.getEventId() != eventAndOrgId.getId()) {
            throw new AccessDeniedException();
        }
    }

    public EventAndOrganizationId checkWaitingQueueSubscriberInEvent(Principal principal, int subscriberId, String eventName) {
        var eventAndOrgId = checkEventOwnership(principal, eventName);
        if (!waitingQueueRepository.exists(subscriberId, eventAndOrgId.getId())) {
            log.warn("subscriberId {} does not exists in event {}", subscriberId, eventName);
            throw new AccessDeniedException();
        }
        return eventAndOrgId;
    }

    public void checkBillingDocumentsOwnership(Principal principal, Integer eventId, List<Long> documentIds) {
        checkEventOwnership(principal, eventId);
        if (!new HashSet<>(documentIds).equals(new HashSet<>(billingDocumentRepository.findByIdsAndEvent(documentIds, eventId)))) {
            log.warn("Some document ids {} are not inside eventId {}", documentIds, eventId);
            throw new AccessDeniedException();
        }
    }

    public void checkPurchaseContextOwnershipAndTicketAdditionalFieldIds(Principal principal,
                                                                         PurchaseContext.PurchaseContextType purchaseContextType,
                                                                         String publicIdentifier,
                                                                         Set<Long> additionalFieldIds) {
        checkPurchaseContextOwnership(principal, purchaseContextType, publicIdentifier);
        UUID subscriptionUuid = null;
        Integer eventId = null;
        if (purchaseContextType == PurchaseContext.PurchaseContextType.event) {
            eventId = eventRepository.findOptionalEventAndOrganizationIdByShortName(publicIdentifier).orElseThrow().getId();
        } else {
            subscriptionUuid = UUID.fromString(publicIdentifier);
        }
        if (additionalFieldIds.size() != purchaseContextFieldRepository.countMatchingAdditionalFieldsForPurchaseContext(eventId, subscriptionUuid, additionalFieldIds)) {
            log.warn("Some additional field ids {} are not inside purchaseContext {}", additionalFieldIds, publicIdentifier);
            throw new AccessDeniedException();
        }
    }

    public void checkCategoryOwnershipAndTicket(Principal principal, String eventName, int categoryId, int ticketId) {
        checkCategoryOwnership(principal, eventName, categoryId);
        if (!ticketRepository.isInCategory(ticketId, categoryId)) {
            log.warn("Ticket with id {} is not in category id {}", ticketId, categoryId);
            throw new AccessDeniedException();
        }
    }

    public void checkEventAndReservationOwnership(Principal principal, String eventName, Set<String> reservationIds) {
        var eventAndOrgId = checkEventOwnership(principal, eventName);
        if (reservationIds.size() != reservationRepository.countReservationsWithEventId(reservationIds, eventAndOrgId.getId())) {
            log.warn("Some reservation ids {} are not in the event {}", reservationIds, eventName);
            throw new AccessDeniedException();
        };
    }

    public void checkEventAndReservationAndTransactionOwnership(Principal principal, String eventName, String reservationId, int transactionId) {
        checkEventAndReservationOwnership(principal, eventName, Set.of(reservationId));
        if (!reservationRepository.hasReservationWithTransactionId(reservationId, transactionId)) {
         log.warn("Reservation id {} does not have transaction id {}", reservationId, transactionId);
            throw new AccessDeniedException();
        }
    }

    public void checkTicketMembership(Principal principal, String publicIdentifier, String reservationId, int ticketId) {
        checkReservationMembershipForEvent(principal, publicIdentifier, reservationId, AccessService.MEMBERSHIP_ROLES);
        var tickets = ticketRepository.findByIds(List.of(ticketId));
        if (tickets.size() != 1 || !tickets.get(0).getTicketsReservationId().equals(reservationId)) {
            throw new AccessDeniedException();
        }
    }

    public void checkEventTicketIdentifierMembership(Principal principal, int eventId, String ticketIdentifier, Set<Role> roles) {
        checkEventMembership(principal, eventId, roles);
        if (!ticketRepository.isTicketInEvent(eventId, ticketIdentifier)) {
            log.warn("ticket {} is not in eventId {}", ticketIdentifier, eventId);
            throw new AccessDeniedException();
        }
    }

    public EventAndOrganizationId checkEventTicketIdentifierMembership(Principal principal, String eventName, String ticketIdentifier, Set<Role> roles) {
        var eventAndOrgId = checkEventMembership(principal, eventName, roles);
        if (!ticketRepository.isTicketInEvent(eventAndOrgId.getId(), ticketIdentifier)) {
            log.warn("ticket {} is not in eventId {}", ticketIdentifier, eventAndOrgId.getId());
            throw new AccessDeniedException();
        }
        return eventAndOrgId;
    }
}
