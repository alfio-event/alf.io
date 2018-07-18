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
import alfio.model.modification.WhitelistConfigurationModification;
import alfio.model.modification.WhitelistItemModification;
import alfio.model.modification.WhitelistModification;
import alfio.model.whitelist.Whitelist;
import alfio.model.whitelist.WhitelistConfiguration;
import alfio.model.whitelist.WhitelistItem;
import alfio.repository.AuditingRepository;
import alfio.repository.TicketRepository;
import alfio.repository.WhitelistRepository;
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

import static alfio.model.whitelist.WhitelistConfiguration.MatchType.FULL;
import static alfio.model.whitelist.WhitelistConfiguration.Type.*;
import static java.util.Collections.singletonList;

@AllArgsConstructor
@Transactional
@Component
@Log4j2
public class WhitelistManager {

    private final WhitelistRepository whitelistRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TicketRepository ticketRepository;
    private final AuditingRepository auditingRepository;

    public int createNew(WhitelistModification input) {
        Whitelist wl = createNew(input.getName(), input.getDescription(), input.getOrganizationId());
        insertItems(wl.getId(), input.getItems());
        return wl.getId();
    }

    public Whitelist createNew(String name, String description, int organizationId) {
        AffectedRowCountAndKey<Integer> insert = whitelistRepository.insert(name, description, organizationId);
        return whitelistRepository.getById(insert.getKey());
    }

    public WhitelistConfiguration createConfiguration(int whitelistId,
                                                      int eventId,
                                                      WhitelistConfigurationModification modification) {
        Objects.requireNonNull(whitelistRepository.getById(whitelistId), "Whitelist not found");
        Validate.isTrue(modification.getType() != LIMITED_QUANTITY || modification.getMaxAllocation() != null, "Missing max allocation");
        AffectedRowCountAndKey<Integer> configuration = whitelistRepository.createConfiguration(whitelistId, eventId,
            modification.getTicketCategoryId(), modification.getType(), modification.getMatchType(), modification.getMaxAllocation());
        return whitelistRepository.getConfiguration(configuration.getKey());
    }

    public WhitelistConfiguration updateConfiguration(int id, WhitelistConfigurationModification modification) {
        WhitelistConfiguration original = whitelistRepository.getConfigurationForUpdate(id);
        if(requiresCleanState(modification, original)) {
            Validate.isTrue(whitelistRepository.countWhitelistedTicketsForConfiguration(original.getId()) == 0, "Cannot update as there are already confirmed tickets.");
        }
        whitelistRepository.updateConfiguration(id, modification.getWhitelistId(), original.getEventId(), modification.getTicketCategoryId(), modification.getType(), modification.getMatchType(), modification.getMaxAllocation());
        return whitelistRepository.getConfiguration(id);
    }

    private boolean requiresCleanState(WhitelistConfigurationModification modification, WhitelistConfiguration original) {
        return (original.getType() == UNLIMITED && modification.getType() != UNLIMITED)
            || original.getWhitelistId() != modification.getWhitelistId()
            || (modification.getType() == LIMITED_QUANTITY && modification.getMaxAllocation() != null && original.getMaxAllocation() != null && modification.getMaxAllocation().compareTo(original.getMaxAllocation()) < 0);
    }

    public boolean isWhitelistConfiguredFor(int eventId, int categoryId) {
        return CollectionUtils.isNotEmpty(findConfigurations(eventId, categoryId));
    }

    public List<Whitelist> getAllForOrganization(int organizationId) {
        return whitelistRepository.getAllForOrganization(organizationId);
    }

    public Optional<WhitelistModification> loadComplete(int id) {
        return whitelistRepository.getOptionalById(id)
            .map(wl -> {
                List<WhitelistItemModification> items = whitelistRepository.getItems(wl.getId()).stream().map(i -> new WhitelistItemModification(i.getId(), i.getValue(), i.getDescription())).collect(Collectors.toList());
                return new WhitelistModification(wl.getId(), wl.getName(), wl.getDescription(), wl.getOrganizationId(), items);
            });
    }

    public boolean isAllowed(String value, int eventId, int categoryId) {

        List<WhitelistConfiguration> configurations = findConfigurations(eventId, categoryId);
        if(CollectionUtils.isEmpty(configurations)) {
            return true;
        }
        WhitelistConfiguration configuration = configurations.get(0);
        return getMatchingItem(configuration, value).isPresent();
    }

    public List<WhitelistConfiguration> getConfigurationsForEvent(int eventId) {
        return whitelistRepository.findActiveConfigurationsForEvent(eventId);
    }

    public List<WhitelistConfiguration> findConfigurations(int eventId, int categoryId) {
        return whitelistRepository.findActiveConfigurationsFor(eventId, categoryId);
    }

    public int insertItems(int whitelistId, List<WhitelistItemModification> items) {
        MapSqlParameterSource[] params = items.stream()
            .map(i -> new MapSqlParameterSource("whitelistId", whitelistId).addValue("value", i.getValue()).addValue("description", i.getDescription()))
            .toArray(MapSqlParameterSource[]::new);
        return Arrays.stream(jdbcTemplate.batchUpdate(whitelistRepository.insertItemTemplate(), params)).sum();
    }

    boolean acquireItemForTicket(Ticket ticket, String email) {
        List<WhitelistConfiguration> configurations = findConfigurations(ticket.getEventId(), ticket.getCategoryId());
        if(CollectionUtils.isEmpty(configurations)) {
            return true;
        }
        WhitelistConfiguration configuration = configurations.get(0);
        Optional<WhitelistItem> optionalItem = getMatchingItem(configuration, StringUtils.defaultString(StringUtils.trimToNull(ticket.getEmail()), email));
        if(!optionalItem.isPresent()) {
            return false;
        }
        WhitelistItem item = optionalItem.get();
        boolean preventDuplication = configuration.getType() == ONCE_PER_VALUE;
        boolean limitAssignments = preventDuplication || configuration.getType() == LIMITED_QUANTITY;
        if(limitAssignments) {
            //reload and lock configuration
            configuration = whitelistRepository.getConfigurationForUpdate(configuration.getId());
            int existing = whitelistRepository.countExistingWhitelistedTickets(item.getId(), configuration.getId());
            int expected = preventDuplication ? 1 : Optional.ofNullable(configuration.getMaxAllocation()).orElse(0);
            if(existing >= expected) {
                return false;
            }
        }
        whitelistRepository.insertWhitelistedTicket(item.getId(), configuration.getId(), ticket.getId(), preventDuplication ? true : null);
        Map<String, Object> modifications = new HashMap<>();
        modifications.put("itemId", item.getId());
        modifications.put("configurationId", configuration.getId());
        modifications.put("ticketId", ticket.getId());
        auditingRepository.insert(ticket.getTicketsReservationId(), null, ticket.getEventId(), Audit.EventType.WHITELIST_ITEM_ACQUIRED, new Date(), Audit.EntityType.TICKET, ticket.getUuid(), singletonList(modifications));
        return true;
    }

    private Optional<WhitelistItem> getMatchingItem(WhitelistConfiguration configuration, String email) {
        String trimmed = StringUtils.trimToEmpty(email);
        Optional<WhitelistItem> exactMatch = whitelistRepository.findItemByValueExactMatch(configuration.getWhitelistId(), trimmed);
        if(exactMatch.isPresent() || configuration.getMatchType() == FULL) {
            return exactMatch;
        }
        String partial = StringUtils.substringAfterLast(trimmed, "@");
        return partial.length() > 0 ? whitelistRepository.findItemEndsWith(configuration.getId(), configuration.getWhitelistId(), "%@"+partial) : Optional.empty();
    }

    public void deleteWhitelistedTicketsForReservation(String reservationId) {
        List<Integer> tickets = ticketRepository.findTicketsInReservation(reservationId).stream().map(Ticket::getId).collect(Collectors.toList());
        if(!tickets.isEmpty()) {
            int result = whitelistRepository.deleteExistingWhitelistedTickets(tickets);
            log.trace("deleted {} whitelisted tickets for reservation {}", result, reservationId);
        }
    }

    @RequiredArgsConstructor
    public static class WhitelistValidator implements Predicate<WhitelistValidationItem> {

        private final int eventId;
        private final WhitelistManager whitelistManager;

        @Override
        public boolean test(WhitelistValidationItem item) {
            return whitelistManager.isAllowed(item.value, eventId, item.categoryId);
        }
    }

    @RequiredArgsConstructor
    public static class WhitelistValidationItem {
        private final int categoryId;
        private final String value;
    }
}
