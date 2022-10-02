/**
 * This file is part of alf.io.
 * <p>
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.manager;

import alfio.manager.system.ConfigurationManager;
import alfio.model.EventAndOrganizationId;
import alfio.model.Ticket;
import alfio.repository.EventDescriptionRepository;
import alfio.repository.EventRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.TicketRepository;
import alfio.repository.user.OrganizationRepository;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.util.Optional;

@Component
@AllArgsConstructor
@Log4j2
public class GoogleWalletManager {

    private final EventRepository eventRepository;
    private final OrganizationRepository organizationRepository;
    private final ConfigurationManager configurationManager;
    private final EventDescriptionRepository eventDescriptionRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final TicketRepository ticketRepository;
    private final HttpClient httpClient;

    public Optional<Pair<EventAndOrganizationId, Ticket>> validateTicket(String eventName, String ticketUuid) {
        var eventOptional = eventRepository.findOptionalEventAndOrganizationIdByShortName(eventName);
        if (eventOptional.isEmpty()) {
            log.trace("event {} not found", eventName);
            return Optional.empty();
        }

        var event = eventOptional.get();
        return ticketRepository.findOptionalByUUID(ticketUuid)
            .filter(t -> t.getEventId() == event.getId())
            .map(t -> Pair.of(event, t));
    }

}
