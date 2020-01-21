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

import alfio.model.Audit;
import alfio.model.Ticket;
import alfio.model.group.Group;
import alfio.model.group.GroupMember;
import alfio.model.group.LinkedGroup;
import alfio.model.modification.GroupMemberModification;
import alfio.model.modification.GroupModification;
import alfio.model.modification.LinkedGroupModification;
import alfio.model.result.ErrorCode;
import alfio.model.result.Result;
import alfio.repository.AuditingRepository;
import alfio.repository.GroupRepository;
import alfio.repository.TicketRepository;
import ch.digitalfondue.npjt.AffectedRowCountAndKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static alfio.model.group.LinkedGroup.MatchType.FULL;
import static alfio.model.group.LinkedGroup.Type.*;
import static java.util.Collections.singletonList;

@Component
@Log4j2
public class GroupManager {

    private final GroupRepository groupRepository;
    private final TicketRepository ticketRepository;
    private final AuditingRepository auditingRepository;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public GroupManager(GroupRepository groupRepository,
                        TicketRepository ticketRepository,
                        AuditingRepository auditingRepository,
                        PlatformTransactionManager transactionManager) {
        this.groupRepository = groupRepository;
        this.ticketRepository = ticketRepository;
        this.auditingRepository = auditingRepository;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager, new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW));
    }

    public Result<Integer> createNew(GroupModification input) {
        return requiresNewTransactionTemplate.execute(status -> {
            Group wl = createNew(input.getName(), input.getDescription(), input.getOrganizationId());
            Result<Integer> insertMembers = insertMembers(wl.getId(), input.getItems());
            if(!insertMembers.isSuccess()) {
                status.setRollbackOnly();
            }
            return insertMembers;
        });
    }

    Group createNew(String name, String description, int organizationId) {
        AffectedRowCountAndKey<Integer> insert = groupRepository.insert(name, description, organizationId);
        return groupRepository.getById(insert.getKey());
    }

    @Transactional
    public LinkedGroup createLink(int groupId,
                                  int eventId,
                                  LinkedGroupModification modification) {
        Objects.requireNonNull(groupRepository.getById(groupId), "Group not found");
        Validate.isTrue(modification.getType() != LIMITED_QUANTITY || modification.getMaxAllocation() != null, "Missing max allocation");
        AffectedRowCountAndKey<Integer> configuration = groupRepository.createConfiguration(groupId, eventId,
            modification.getTicketCategoryId(), modification.getType(), modification.getMatchType(), modification.getMaxAllocation());
        return groupRepository.getConfiguration(configuration.getKey());
    }

    @Transactional
    public LinkedGroup updateLink(int id, LinkedGroupModification modification) {
        LinkedGroup original = groupRepository.getConfigurationForUpdate(id);
        if(requiresCleanState(modification, original)) {
            Validate.isTrue(groupRepository.countWhitelistedTicketsForConfiguration(original.getId()) == 0, "Cannot update as there are already confirmed tickets.");
        }
        groupRepository.updateConfiguration(id, modification.getGroupId(), original.getEventId(), modification.getTicketCategoryId(), modification.getType(), modification.getMatchType(), modification.getMaxAllocation());
        return groupRepository.getConfiguration(id);
    }

    private boolean requiresCleanState(LinkedGroupModification modification, LinkedGroup original) {
        return (original.getType() == UNLIMITED && modification.getType() != UNLIMITED)
            || original.getGroupId() != modification.getGroupId()
            || (modification.getType() == LIMITED_QUANTITY && modification.getMaxAllocation() != null && original.getMaxAllocation() != null && modification.getMaxAllocation().compareTo(original.getMaxAllocation()) < 0);
    }

    boolean isGroupLinked(int eventId, int categoryId) {
        return CollectionUtils.isNotEmpty(findLinks(eventId, categoryId));
    }

    @Transactional(readOnly = true)
    public List<Group> getAllActiveForOrganization(int organizationId) {
        return groupRepository.getAllActiveForOrganization(organizationId);
    }

    @Transactional(readOnly = true)
    public List<Group> getAllForOrganization(int organizationId) {
        return groupRepository.getAllForOrganization(organizationId);
    }

    @Transactional
    public Optional<GroupModification> loadComplete(int id) {
        return groupRepository.getOptionalById(id)
            .map(wl -> {
                List<GroupMemberModification> items = groupRepository.getItems(wl.getId()).stream().map(i -> new GroupMemberModification(i.getId(), i.getValue(), i.getDescription())).collect(Collectors.toList());
                return new GroupModification(wl.getId(), wl.getName(), wl.getDescription(), wl.getOrganizationId(), items);
            });
    }

    @Transactional
    public Optional<Group> findById(int groupId, int organizationId) {
        return groupRepository.getOptionalById(groupId).filter(w -> w.getOrganizationId() == organizationId);
    }

    @Transactional
    public boolean isAllowed(String value, int eventId, int categoryId) {

        List<LinkedGroup> configurations = findLinks(eventId, categoryId);
        if(CollectionUtils.isEmpty(configurations)) {
            return true;
        }
        LinkedGroup configuration = configurations.get(0);
        return getMatchingMember(configuration, value).isPresent();
    }

    @Transactional
    public List<LinkedGroup> getLinksForEvent(int eventId) {
        return groupRepository.findActiveConfigurationsForEvent(eventId);
    }

    @Transactional
    public List<LinkedGroup> findLinks(int eventId, int categoryId) {
        return groupRepository.findActiveConfigurationsFor(eventId, categoryId);
    }

    Result<Integer> insertMembers(int groupId, List<GroupMemberModification> members) {

        Map<String, List<GroupMemberModification>> grouped = members.stream().collect(Collectors.groupingBy(GroupMemberModification::getValue));
        List<String> duplicates = grouped.entrySet().stream().filter(e -> e.getValue().size() > 1).map(Map.Entry::getKey).collect(Collectors.toList());

        return new Result.Builder<Integer>()
            .checkPrecondition(duplicates::isEmpty, ErrorCode.lazy(() -> ErrorCode.custom("value.duplicate", duplicates.stream().limit(10).collect(Collectors.joining(", ")))))
            .build(() -> Arrays.stream(groupRepository.insert(groupId, members)).sum());
    }

    @Transactional
    public boolean acquireMemberForTicket(Ticket ticket) {
        List<LinkedGroup> configurations = findLinks(ticket.getEventId(), ticket.getCategoryId());
        if(CollectionUtils.isEmpty(configurations)) {
            return true;
        }
        LinkedGroup configuration = configurations.get(0);
        Optional<GroupMember> optionalItem = getMatchingMember(configuration, ticket.getEmail());
        if(optionalItem.isEmpty()) {
            return false;
        }
        GroupMember item = optionalItem.get();
        boolean preventDuplication = configuration.getType() == ONCE_PER_VALUE;
        boolean limitAssignments = preventDuplication || configuration.getType() == LIMITED_QUANTITY;
        if(limitAssignments) {
            //reload and lock configuration
            configuration = groupRepository.getConfigurationForUpdate(configuration.getId());
            int existing = groupRepository.countExistingWhitelistedTickets(item.getId(), configuration.getId());
            int expected = preventDuplication ? 1 : Optional.ofNullable(configuration.getMaxAllocation()).orElse(0);
            if(existing >= expected) {
                return false;
            }
        }
        groupRepository.insertWhitelistedTicket(item.getId(), configuration.getId(), ticket.getId(), preventDuplication ? Boolean.TRUE : null);
        Map<String, Object> modifications = new HashMap<>();
        modifications.put("itemId", item.getId());
        modifications.put("configurationId", configuration.getId());
        modifications.put("ticketId", ticket.getId());
        auditingRepository.insert(ticket.getTicketsReservationId(), null, ticket.getEventId(), Audit.EventType.GROUP_MEMBER_ACQUIRED, new Date(), Audit.EntityType.TICKET, String.valueOf(ticket.getId()), singletonList(modifications));
        return true;
    }

    private Optional<GroupMember> getMatchingMember(LinkedGroup configuration, String email) {
        String trimmed = StringUtils.trimToEmpty(email);
        Optional<GroupMember> exactMatch = groupRepository.findItemByValueExactMatch(configuration.getGroupId(), trimmed);
        if(exactMatch.isPresent() || configuration.getMatchType() == FULL) {
            return exactMatch;
        }
        String partial = StringUtils.substringAfterLast(trimmed, "@");
        return partial.length() > 0 ? groupRepository.findItemEndsWith(configuration.getId(), configuration.getGroupId(), "%@"+partial) : Optional.empty();
    }

    @Transactional
    public void deleteWhitelistedTicketsForReservation(String reservationId) {
        List<Integer> tickets = ticketRepository.findTicketsInReservation(reservationId).stream().map(Ticket::getId).collect(Collectors.toList());
        if(!tickets.isEmpty()) {
            int result = groupRepository.deleteExistingWhitelistedTickets(tickets);
            log.trace("deleted {} whitelisted tickets for reservation {}", result, reservationId);
        }
    }

    @Transactional
    public void disableLink(int linkId) {
        Validate.isTrue(groupRepository.disableLink(linkId) == 1, "Error while disabling link");
    }

    @Transactional
    public Optional<GroupModification> update(int listId, GroupModification modification) {

        if(groupRepository.getOptionalById(listId).isEmpty() || CollectionUtils.isEmpty(modification.getItems())) {
            return Optional.empty();
        }

        List<String> existingValues = groupRepository.getAllValuesIncludingNotActive(listId);
        List<GroupMemberModification> notPresent = modification.getItems().stream()
            .filter(i -> i.getId() == null && !existingValues.contains(i.getValue().strip().toLowerCase()))
            .distinct()
            .collect(Collectors.toList());

        if(!notPresent.isEmpty()) {
            var insertResult = insertMembers(listId, notPresent);
            if(!insertResult.isSuccess()) {
                var error = Objects.requireNonNull(insertResult.getFirstErrorOrNull());
                throw new DuplicateGroupItemException(error.getDescription());
            }
        }
        groupRepository.update(listId, modification.getName(), modification.getDescription());
        return loadComplete(listId);
    }

    @Transactional
    public boolean deactivateMembers(List<Integer> memberIds, int groupId) {
        if(memberIds.isEmpty()) {
            return false;
        }
        groupRepository.deactivateGroupMember(memberIds, groupId);
        return true;
    }

    @Transactional
    public boolean deactivateGroup(int groupId) {
        List<Integer> members = groupRepository.getItems(groupId).stream().map(GroupMember::getId).collect(Collectors.toList());
        if(!members.isEmpty()) {
            Validate.isTrue(deactivateMembers(members, groupId), "error while disabling group members");
        }
        groupRepository.disableAllLinks(groupId);
        Validate.isTrue(groupRepository.deactivateGroup(groupId) == 1, "unexpected error while disabling group");
        return true;
    }

    @RequiredArgsConstructor
    public static class WhitelistValidator implements Predicate<WhitelistValidationItem> {

        private final int eventId;
        private final GroupManager groupManager;

        @Override
        public boolean test(WhitelistValidationItem item) {
            return groupManager.isAllowed(item.value, eventId, item.categoryId);
        }
    }

    @RequiredArgsConstructor
    public static class WhitelistValidationItem {
        private final int categoryId;
        private final String value;
    }

    public static class DuplicateGroupItemException extends RuntimeException {
        public DuplicateGroupItemException(String message) {
            super(message);
        }
    }
}
