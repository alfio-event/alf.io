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

import alfio.extension.ExtensionService;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.Ticket;
import alfio.model.TicketCategory;
import alfio.model.metadata.AlfioMetadata;
import alfio.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static alfio.manager.support.extension.ExtensionEvent.TICKET_ASSIGNED;
import static org.mockito.Mockito.*;

class ExtensionManagerTest {

    private ExtensionManager extensionManager;
    private EventRepository eventRepository;
    private ExtensionService extensionService;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        ticket = mock(Ticket.class);
        when(ticket.getStatus()).thenReturn(Ticket.TicketStatus.ACQUIRED);
        when(ticket.getEventId()).thenReturn(1);
        var event = mock(Event.class);
        when(event.getId()).thenReturn(1);
        when(event.getShortName()).thenReturn("blabla");
        eventRepository = mock(EventRepository.class);
        when(eventRepository.findOrganizationIdByEventId(1)).thenReturn(1);
        when(eventRepository.findById(1)).thenReturn(event);
        extensionService = mock(ExtensionService.class);
        extensionManager = new ExtensionManager(extensionService, eventRepository, null, null, mock(ConfigurationManager.class), null);
    }

    @Test
    void handleTicketAssignmentTicketConfirmed() {
        when(ticket.hasBeenSold()).thenReturn(true);
        when(eventRepository.getMetadataForEvent(eq(1))).thenReturn(AlfioMetadata.empty());
        extensionManager.handleTicketAssignment(ticket, mock(TicketCategory.class), Map.of());
        verify(eventRepository, never()).findOrganizationIdByEventId(eq(1));
        verify(eventRepository).findById(eq(1));
        verify(extensionService).executeScriptAsync(eq(TICKET_ASSIGNED.name()), anyString(), any());
    }

    @Test
    void handleTicketAssignmentTicketNotConfirmed() {
        when(ticket.hasBeenSold()).thenReturn(false);
        when(eventRepository.getMetadataForEvent(eq(1))).thenReturn(AlfioMetadata.empty());
        extensionManager.handleTicketAssignment(ticket, mock(TicketCategory.class), Map.of());
        verify(eventRepository, never()).findOrganizationIdByEventId(eq(1));
        verify(eventRepository, never()).findById(eq(1));
        verify(extensionService, never()).executeScriptAsync(eq(TICKET_ASSIGNED.name()), anyString(), any());
    }
}