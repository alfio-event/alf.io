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
package alfio.model;

import alfio.model.metadata.TicketMetadataContainer;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class TicketWithMetadataAttributes {

    private final Ticket ticket;
    private final TicketMetadataContainer ticketMetadataContainer;

    public TicketWithMetadataAttributes(Ticket ticket, TicketMetadataContainer ticketMetadataContainer) {
        this.ticket = ticket;
        this.ticketMetadataContainer = Objects.requireNonNullElseGet(ticketMetadataContainer, TicketMetadataContainer::empty);
    }

    public Map<String, String> getAttributes() {
        return ticketMetadataContainer
            .getMetadataForKey(TicketMetadataContainer.GENERAL)
            .flatMap(tm -> Optional.ofNullable(tm.getAttributes()))
            .orElse(Map.of());
    }

    public Ticket getTicket() {
        return ticket;
    }
}
