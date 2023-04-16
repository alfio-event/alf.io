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
package alfio.model.system.command;

import alfio.model.Event;
import alfio.model.Ticket;
import alfio.model.metadata.TicketMetadataContainer;

/**
 * Signals that access for the ticket must be invalidated on external systems
 */
public class InvalidateAccess {
    private final Ticket ticket;
    private final TicketMetadataContainer ticketMetadataContainer;
    private final Event event;

    public InvalidateAccess(Ticket ticket, TicketMetadataContainer ticketMetadataContainer, Event event) {
        this.ticket = ticket;
        this.ticketMetadataContainer = ticketMetadataContainer;
        this.event = event;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public TicketMetadataContainer getTicketMetadataContainer() {
        return ticketMetadataContainer;
    }

    public Event getEvent() {
        return event;
    }
}
