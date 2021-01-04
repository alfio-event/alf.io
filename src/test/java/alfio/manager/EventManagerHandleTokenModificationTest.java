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
import alfio.model.TicketCategory;
import alfio.repository.SpecialPriceRepository;
import alfio.repository.SubscriptionRepository;
import alfio.repository.TicketRepository;
import alfio.test.util.TestUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@DisplayName("EventManager: handle Token modifications")
class EventManagerHandleTokenModificationTest {

    private TicketCategory original;
    private TicketCategory updated;
    private SpecialPriceRepository specialPriceRepository;
    private EventManager eventManager;
    private final int eventId = 10;


    @BeforeEach
    void init() {
        original = mock(TicketCategory.class);
        updated = mock(TicketCategory.class);
        specialPriceRepository = mock(SpecialPriceRepository.class);
        Event event = mock(Event.class);
        TicketRepository ticketRepository = mock(TicketRepository.class);
        when(event.getId()).thenReturn(eventId);
        eventManager = new EventManager(null, null, null, null,
            null, ticketRepository, specialPriceRepository, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, TestUtil.clockProvider(), mock(SubscriptionRepository.class));
        when(original.getId()).thenReturn(20);
        when(updated.getId()).thenReturn(30);
        when(original.getSrcPriceCts()).thenReturn(1000);
        when(updated.getSrcPriceCts()).thenReturn(1000);
        when(original.getMaxTickets()).thenReturn(10);
        when(updated.getMaxTickets()).thenReturn(11);
        when(event.getZoneId()).thenReturn(ZoneId.systemDefault());
    }


    @Test
    @DisplayName("do nothing if both categories are not 'access restricted'")
    void doNothingIfCategoriesAreNotAccessRestricted() {
        when(original.isAccessRestricted()).thenReturn(false);
        when(updated.isAccessRestricted()).thenReturn(false);
        eventManager.handleTokenModification(original, updated, 50);
        verify(specialPriceRepository, never()).bulkInsert(any(TicketCategory.class), anyInt());
    }

    @Test
    @DisplayName("handle the activation of access restriction")
    void handleRestrictionActivation() {
        when(original.isAccessRestricted()).thenReturn(false);
        when(updated.isAccessRestricted()).thenReturn(true);
        when(updated.getMaxTickets()).thenReturn(50);
        eventManager.handleTokenModification(original, updated, 50);
        verify(specialPriceRepository, times(1)).bulkInsert(updated, updated.getMaxTickets());
    }

    @Test
    @DisplayName("handle the deactivation of access restriction")
    void handleRestrictionDeactivation() {
        when(original.isAccessRestricted()).thenReturn(true);
        when(updated.isAccessRestricted()).thenReturn(false);
        when(updated.getId()).thenReturn(20);
        eventManager.handleTokenModification(original, updated, 50);
        verify(specialPriceRepository, times(1)).cancelExpiredTokens(eq(20));
    }

    @Test
    @DisplayName("handle the ticket addition")
    void handleTicketAddition() {
        when(original.isAccessRestricted()).thenReturn(true);
        when(updated.isAccessRestricted()).thenReturn(true);
        eventManager.handleTokenModification(original, updated, 50);
        verify(specialPriceRepository, times(1)).bulkInsert(updated, 50);
    }

    @Test
    @DisplayName("handle the ticket removal")
    void handleTicketRemoval() {
        when(original.isAccessRestricted()).thenReturn(true);
        when(updated.isAccessRestricted()).thenReturn(true);
        when(updated.getId()).thenReturn(20);
        final List<Integer> ids = Arrays.asList(1, 2);
        when(specialPriceRepository.lockNotSentTokens(eq(20), eq(2))).thenReturn(ids);
        eventManager.handleTokenModification(original, updated, -2);
        verify(specialPriceRepository, times(1)).cancelTokens(ids);
    }

    @Test
    @DisplayName("fail if there are not enough tickets")
    void failIfNotEnoughTickets() {
        when(original.isAccessRestricted()).thenReturn(true);
        when(updated.isAccessRestricted()).thenReturn(true);
        when(updated.getId()).thenReturn(20);
        final List<Integer> ids = singletonList(1);
        when(specialPriceRepository.lockNotSentTokens(eq(20), eq(2))).thenReturn(ids);
        assertThrows(IllegalArgumentException.class, () -> eventManager.handleTokenModification(original, updated, -2));
        verify(specialPriceRepository, never()).cancelTokens(anyList());
    }
}