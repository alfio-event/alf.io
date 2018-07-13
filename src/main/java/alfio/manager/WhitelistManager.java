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
import alfio.model.modification.WhitelistItemModification;
import alfio.model.whitelist.Whitelist;
import alfio.model.whitelist.WhitelistConfiguration;
import alfio.model.whitelist.WhitelistItem;
import alfio.model.whitelist.WhitelistedTicket;
import alfio.repository.AuditingRepository;
import alfio.repository.TicketRepository;
import alfio.repository.WhitelistRepository;
import ch.digitalfondue.npjt.AffectedRowCountAndKey;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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

    public Whitelist createNew(String name, String description, int organizationId) {
        AffectedRowCountAndKey<Integer> insert = whitelistRepository.insert(name, description, organizationId);
        return whitelistRepository.getById(insert.getKey());
    }

    public WhitelistConfiguration createConfiguration(int whitelistId,
                                                      int eventId,
                                                      Integer categoryId,
                                                      WhitelistConfiguration.Type type,
                                                      WhitelistConfiguration.MatchType matchType) {
        Objects.requireNonNull(whitelistRepository.getById(whitelistId), "Whitelist not found");
        AffectedRowCountAndKey<Integer> configuration = whitelistRepository.createConfiguration(whitelistId, eventId, categoryId, type, matchType);
        return whitelistRepository.getConfiguration(configuration.getKey());
    }

    public boolean isWhitelistConfiguredFor(int eventId, int categoryId) {
        return CollectionUtils.isNotEmpty(findConfigurations(eventId, categoryId));
    }

    public boolean isAllowed(String value, int eventId, int categoryId) {

        List<WhitelistConfiguration> configurations = findConfigurations(eventId, categoryId);
        if(CollectionUtils.isEmpty(configurations)) {
            return true;
        }
        WhitelistConfiguration configuration = configurations.get(0);
        return whitelistRepository.findItemByValueExactMatch(configuration.getId(), configuration.getWhitelistId(), StringUtils.trim(value)).isPresent();
    }

    public List<WhitelistConfiguration> getConfigurationsForEvent(int eventId) {
        return whitelistRepository.findActiveConfigurationsForEvent(eventId);
    }

    private List<WhitelistConfiguration> findConfigurations(int eventId, int categoryId) {
        return whitelistRepository.findActiveConfigurationsFor(eventId, categoryId);
    }

    public int insertItems(int whitelistId, List<WhitelistItemModification> items) {
        MapSqlParameterSource[] params = items.stream()
            .map(i -> new MapSqlParameterSource("whitelistId", whitelistId).addValue("value", i.getValue()).addValue("description", i.getDescription()))
            .toArray(MapSqlParameterSource[]::new);
        return Arrays.stream(jdbcTemplate.batchUpdate(whitelistRepository.insertItemTemplate(), params)).sum();
    }

    public boolean acquireItemForTicket(Ticket ticket) {
        List<WhitelistConfiguration> configurations = findConfigurations(ticket.getEventId(), ticket.getCategoryId());
        if(CollectionUtils.isEmpty(configurations)) {
            return true;
        }
        WhitelistConfiguration configuration = configurations.get(0);
        Optional<WhitelistItem> optionalItem = whitelistRepository.findItemByValueExactMatch(configuration.getId(), configuration.getWhitelistId(), StringUtils.trim(ticket.getEmail()));
        if(!optionalItem.isPresent()) {
            return false;
        }
        WhitelistItem item = optionalItem.get();
        boolean preventDuplication = configuration.getType() == WhitelistConfiguration.Type.ONCE_PER_VALUE;
        if(preventDuplication) {
            //reload and lock configuration
            configuration = whitelistRepository.getConfigurationForUpdate(configuration.getId());
            Optional<WhitelistedTicket> existing = whitelistRepository.findExistingWhitelistedTicket(item.getId(), configuration.getId());
            if(existing.isPresent()) {
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
