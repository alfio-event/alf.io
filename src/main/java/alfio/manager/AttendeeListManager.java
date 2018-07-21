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
import alfio.model.attendeelist.AttendeeList;
import alfio.model.attendeelist.AttendeeListConfiguration;
import alfio.model.attendeelist.AttendeeListItem;
import alfio.model.modification.AttendeeListConfigurationModification;
import alfio.model.modification.AttendeeListItemModification;
import alfio.model.modification.AttendeeListModification;
import alfio.repository.AttendeeListRepository;
import alfio.repository.AuditingRepository;
import alfio.repository.TicketRepository;
import ch.digitalfondue.npjt.AffectedRowCountAndKey;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static alfio.model.attendeelist.AttendeeListConfiguration.MatchType.FULL;
import static alfio.model.attendeelist.AttendeeListConfiguration.Type.*;
import static java.util.Collections.singletonList;

@AllArgsConstructor
@Transactional
@Component
@Log4j2
public class AttendeeListManager {

    private final AttendeeListRepository attendeeListRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TicketRepository ticketRepository;
    private final AuditingRepository auditingRepository;

    public int createNew(AttendeeListModification input) {
        AttendeeList wl = createNew(input.getName(), input.getDescription(), input.getOrganizationId());
        insertItems(wl.getId(), input.getItems());
        return wl.getId();
    }

    public AttendeeList createNew(String name, String description, int organizationId) {
        AffectedRowCountAndKey<Integer> insert = attendeeListRepository.insert(name, description, organizationId);
        return attendeeListRepository.getById(insert.getKey());
    }

    public AttendeeListConfiguration createConfiguration(int attendeeListId,
                                                         int eventId,
                                                         AttendeeListConfigurationModification modification) {
        Objects.requireNonNull(attendeeListRepository.getById(attendeeListId), "Attendee list not found");
        Validate.isTrue(modification.getType() != LIMITED_QUANTITY || modification.getMaxAllocation() != null, "Missing max allocation");
        AffectedRowCountAndKey<Integer> configuration = attendeeListRepository.createConfiguration(attendeeListId, eventId,
            modification.getTicketCategoryId(), modification.getType(), modification.getMatchType(), modification.getMaxAllocation());
        return attendeeListRepository.getConfiguration(configuration.getKey());
    }

    public AttendeeListConfiguration updateConfiguration(int id, AttendeeListConfigurationModification modification) {
        AttendeeListConfiguration original = attendeeListRepository.getConfigurationForUpdate(id);
        if(requiresCleanState(modification, original)) {
            Validate.isTrue(attendeeListRepository.countWhitelistedTicketsForConfiguration(original.getId()) == 0, "Cannot update as there are already confirmed tickets.");
        }
        attendeeListRepository.updateConfiguration(id, modification.getAttendeeListId(), original.getEventId(), modification.getTicketCategoryId(), modification.getType(), modification.getMatchType(), modification.getMaxAllocation());
        return attendeeListRepository.getConfiguration(id);
    }

    private boolean requiresCleanState(AttendeeListConfigurationModification modification, AttendeeListConfiguration original) {
        return (original.getType() == UNLIMITED && modification.getType() != UNLIMITED)
            || original.getAttendeeListId() != modification.getAttendeeListId()
            || (modification.getType() == LIMITED_QUANTITY && modification.getMaxAllocation() != null && original.getMaxAllocation() != null && modification.getMaxAllocation().compareTo(original.getMaxAllocation()) < 0);
    }

    public boolean isAttendeeListConfiguredFor(int eventId, int categoryId) {
        return CollectionUtils.isNotEmpty(findConfigurations(eventId, categoryId));
    }

    public List<AttendeeList> getAllForOrganization(int organizationId) {
        return attendeeListRepository.getAllForOrganization(organizationId);
    }

    public Optional<AttendeeListModification> loadComplete(int id) {
        return attendeeListRepository.getOptionalById(id)
            .map(wl -> {
                List<AttendeeListItemModification> items = attendeeListRepository.getItems(wl.getId()).stream().map(i -> new AttendeeListItemModification(i.getId(), i.getValue(), i.getDescription())).collect(Collectors.toList());
                return new AttendeeListModification(wl.getId(), wl.getName(), wl.getDescription(), wl.getOrganizationId(), items);
            });
    }

    public Optional<AttendeeList> findById(int attendeeListId, int organizationId) {
        return attendeeListRepository.getOptionalById(attendeeListId).filter(w -> w.getOrganizationId() == organizationId);
    }

    public boolean isAllowed(String value, int eventId, int categoryId) {

        List<AttendeeListConfiguration> configurations = findConfigurations(eventId, categoryId);
        if(CollectionUtils.isEmpty(configurations)) {
            return true;
        }
        AttendeeListConfiguration configuration = configurations.get(0);
        return getMatchingItem(configuration, value).isPresent();
    }

    public List<AttendeeListConfiguration> getConfigurationsForEvent(int eventId) {
        return attendeeListRepository.findActiveConfigurationsForEvent(eventId);
    }

    public List<AttendeeListConfiguration> findConfigurations(int eventId, int categoryId) {
        return attendeeListRepository.findActiveConfigurationsFor(eventId, categoryId);
    }

    public int insertItems(int attendeeListId, List<AttendeeListItemModification> items) {
        MapSqlParameterSource[] params = items.stream()
            .map(i -> new MapSqlParameterSource("attendeeListId", attendeeListId).addValue("value", i.getValue()).addValue("description", i.getDescription()))
            .toArray(MapSqlParameterSource[]::new);
        return Arrays.stream(jdbcTemplate.batchUpdate(attendeeListRepository.insertItemTemplate(), params)).sum();
    }

    boolean acquireItemForTicket(Ticket ticket, String email) {
        List<AttendeeListConfiguration> configurations = findConfigurations(ticket.getEventId(), ticket.getCategoryId());
        if(CollectionUtils.isEmpty(configurations)) {
            return true;
        }
        AttendeeListConfiguration configuration = configurations.get(0);
        Optional<AttendeeListItem> optionalItem = getMatchingItem(configuration, StringUtils.defaultString(StringUtils.trimToNull(ticket.getEmail()), email));
        if(!optionalItem.isPresent()) {
            return false;
        }
        AttendeeListItem item = optionalItem.get();
        boolean preventDuplication = configuration.getType() == ONCE_PER_VALUE;
        boolean limitAssignments = preventDuplication || configuration.getType() == LIMITED_QUANTITY;
        if(limitAssignments) {
            //reload and lock configuration
            configuration = attendeeListRepository.getConfigurationForUpdate(configuration.getId());
            int existing = attendeeListRepository.countExistingWhitelistedTickets(item.getId(), configuration.getId());
            int expected = preventDuplication ? 1 : Optional.ofNullable(configuration.getMaxAllocation()).orElse(0);
            if(existing >= expected) {
                return false;
            }
        }
        attendeeListRepository.insertWhitelistedTicket(item.getId(), configuration.getId(), ticket.getId(), preventDuplication ? true : null);
        Map<String, Object> modifications = new HashMap<>();
        modifications.put("itemId", item.getId());
        modifications.put("configurationId", configuration.getId());
        modifications.put("ticketId", ticket.getId());
        auditingRepository.insert(ticket.getTicketsReservationId(), null, ticket.getEventId(), Audit.EventType.ATTENDEE_LIST_ITEM_ACQUIRED, new Date(), Audit.EntityType.TICKET, ticket.getUuid(), singletonList(modifications));
        return true;
    }

    private Optional<AttendeeListItem> getMatchingItem(AttendeeListConfiguration configuration, String email) {
        String trimmed = StringUtils.trimToEmpty(email);
        Optional<AttendeeListItem> exactMatch = attendeeListRepository.findItemByValueExactMatch(configuration.getAttendeeListId(), trimmed);
        if(exactMatch.isPresent() || configuration.getMatchType() == FULL) {
            return exactMatch;
        }
        String partial = StringUtils.substringAfterLast(trimmed, "@");
        return partial.length() > 0 ? attendeeListRepository.findItemEndsWith(configuration.getId(), configuration.getAttendeeListId(), "%@"+partial) : Optional.empty();
    }

    public void deleteWhitelistedTicketsForReservation(String reservationId) {
        List<Integer> tickets = ticketRepository.findTicketsInReservation(reservationId).stream().map(Ticket::getId).collect(Collectors.toList());
        if(!tickets.isEmpty()) {
            int result = attendeeListRepository.deleteExistingWhitelistedTickets(tickets);
            log.trace("deleted {} whitelisted tickets for reservation {}", result, reservationId);
        }
    }

    public void disableConfiguration(int configurationId) {
        Validate.isTrue(attendeeListRepository.disableConfiguration(configurationId) == 1, "Error while deleting configuration");
    }

    @RequiredArgsConstructor
    public static class WhitelistValidator implements Predicate<WhitelistValidationItem> {

        private final int eventId;
        private final AttendeeListManager attendeeListManager;

        @Override
        public boolean test(WhitelistValidationItem item) {
            return attendeeListManager.isAllowed(item.value, eventId, item.categoryId);
        }
    }

    @RequiredArgsConstructor
    public static class WhitelistValidationItem {
        private final int categoryId;
        private final String value;
    }
}
