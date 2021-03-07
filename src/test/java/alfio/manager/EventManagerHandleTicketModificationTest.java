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

import alfio.model.Event;
import alfio.model.Ticket;
import alfio.model.TicketCategory;
import alfio.repository.SubscriptionRepository;
import alfio.repository.TicketRepository;
import alfio.util.ClockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import static alfio.test.util.TestUtil.clockProvider;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.*;

@DisplayName("EventManager: handle Ticket modifications")
public class EventManagerHandleTicketModificationTest {
    private Event event;
    private TicketCategory original;
    private TicketCategory updated;
    private TicketRepository ticketRepository;
    private EventManager eventManager;
    private final int eventId = 10;
    private int originalCategoryId = 20;
    private int updatedCategoryId = 30;


    @BeforeEach
    void init() {
        event = mock(Event.class);
        original = mock(TicketCategory.class);
        updated = mock(TicketCategory.class);
        ticketRepository = mock(TicketRepository.class);

        when(event.getId()).thenReturn(eventId);
        when(event.now(any(ClockProvider.class))).thenReturn(ZonedDateTime.now(clockProvider().getClock().withZone(ZoneId.systemDefault())));
        eventManager = new EventManager(null, null, null, null, null, ticketRepository, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, clockProvider(), mock(SubscriptionRepository.class));
        when(original.getId()).thenReturn(originalCategoryId);
        when(updated.getId()).thenReturn(updatedCategoryId);
        when(original.getSrcPriceCts()).thenReturn(1000);
        when(updated.getSrcPriceCts()).thenReturn(1000);
        when(original.getMaxTickets()).thenReturn(10);
        when(updated.getMaxTickets()).thenReturn(11);
        when(original.isBounded()).thenReturn(true);
        when(event.getZoneId()).thenReturn(ZoneId.systemDefault());
    }

    @DisplayName("throw exception if there are tickets already sold")
    @Test
    void throwExceptionIfAlreadySold() {
        when(ticketRepository.lockTicketsToInvalidate(eventId, 30, 2)).thenReturn(singletonList(1));
        assertThrows(IllegalStateException.class, () -> eventManager.handleTicketNumberModification(event, updated, -2, false));
        verify(ticketRepository, never()).invalidateTickets(anyList());
    }

    @Test
    @DisplayName("invalidate exceeding tickets")
    void invalidateExceedingTickets() {
        final List<Integer> ids = Arrays.asList(1, 2);
        when(ticketRepository.lockTicketsToInvalidate(eventId, updatedCategoryId, 2)).thenReturn(ids);
        eventManager.handleTicketNumberModification(event, updated, -2, false);
        verify(ticketRepository, times(1)).invalidateTickets(ids);
    }

    @Test
    @DisplayName("do nothing if the difference is zero")
    void doNothingIfZero() {
        eventManager.handleTicketNumberModification(event, updated, 0, false);
        verify(ticketRepository, never()).invalidateTickets(anyList());
        verify(ticketRepository, never()).bulkTicketUpdate(any(), any());
    }

    @Test
    @DisplayName("insert a new Ticket if the difference is 1")
    void insertTicketIfDifference1() {
        when(ticketRepository.selectNotAllocatedTicketsForUpdate(eq(eventId), eq(1), eq(Arrays.asList(Ticket.TicketStatus.FREE.name(), Ticket.TicketStatus.RELEASED.name())))).thenReturn(singletonList(1));
        eventManager.handleTicketNumberModification(event, updated, 1, false);
        verify(ticketRepository, never()).invalidateTickets(anyList());
        verify(ticketRepository, times(1)).bulkTicketUpdate(any(), any());
    }

    @Test
    @DisplayName("do nothing if the price is not changed")
    void doNothingIfPriceIsNotChanged() {
        when(original.getSrcPriceCts()).thenReturn(10);
        when(updated.getSrcPriceCts()).thenReturn(10);
        eventManager.handlePriceChange(event, original, updated);
        verify(ticketRepository, never()).selectTicketInCategoryForUpdate(anyInt(), anyInt(), anyInt(), any());
        verify(ticketRepository, never()).updateTicketPrice(anyInt(), anyInt(), anyInt(), eq(0), eq(0), eq(0), anyString());
    }

    @Test
    @DisplayName("throw Exception if there are not enough tickets")
    void throwExceptionIfNotEnoughTickets() {
        when(original.getSrcPriceCts()).thenReturn(10);
        when(updated.getSrcPriceCts()).thenReturn(11);
        when(updated.getMaxTickets()).thenReturn(2);
        when(updated.getId()).thenReturn(20);
        when(ticketRepository.selectTicketInCategoryForUpdate(eq(eventId), eq(originalCategoryId), eq(2), eq(singletonList(Ticket.TicketStatus.FREE.name())))).thenReturn(singletonList(1));
        assertThrows(IllegalStateException.class, () -> eventManager.handlePriceChange(event, original, updated));
        verify(ticketRepository, never()).updateTicketPrice(anyInt(), anyInt(), anyInt(), eq(0), eq(0), eq(0), anyString());
    }

    @Test
    @DisplayName("update tickets if constraints are verified")
    void updateTicketsIfConstraintsVerified() {
        when(original.getCurrencyCode()).thenReturn("CHF");
        when(original.getSrcPriceCts()).thenReturn(10);
        when(updated.getSrcPriceCts()).thenReturn(11);
        when(updated.getMaxTickets()).thenReturn(2);
        when(updated.getId()).thenReturn(updatedCategoryId);
        when(ticketRepository.selectTicketInCategoryForUpdate(eq(eventId), eq(updatedCategoryId), eq(2), eq(singletonList(Ticket.TicketStatus.FREE.name())))).thenReturn(Arrays.asList(1, 2));
        eventManager.handlePriceChange(event, original, updated);
        verify(ticketRepository, times(1)).updateTicketPrice(updatedCategoryId, eventId, 11, 0, 0, 0, "CHF");
    }

}