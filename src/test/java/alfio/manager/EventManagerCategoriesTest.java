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
import alfio.repository.EventRepository;
import alfio.repository.SubscriptionRepository;
import alfio.repository.TicketCategoryDescriptionRepository;
import alfio.repository.TicketCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static alfio.manager.testSupport.TicketCategoryGenerator.generateCategoryStream;
import static alfio.test.util.TestUtil.clockProvider;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("EventManager: handle categories")
public class EventManagerCategoriesTest {

    private TicketCategoryRepository ticketCategoryRepository;
    private Event event;
    private EventManager eventManager;
    private final int eventId = 0;
    private final int availableSeats = 20;


    @BeforeEach
    void init() {
        ticketCategoryRepository = mock(TicketCategoryRepository.class);
        TicketCategoryDescriptionRepository ticketCategoryDescriptionRepository = mock(TicketCategoryDescriptionRepository.class);
        EventRepository eventRepository = mock(EventRepository.class);
        event = mock(Event.class);
        when(event.getId()).thenReturn(eventId);
        eventManager = new EventManager(null, eventRepository, null, ticketCategoryRepository, ticketCategoryDescriptionRepository, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, clockProvider(), mock(SubscriptionRepository.class));
        when(eventRepository.countExistingTickets(0)).thenReturn(availableSeats);
        when(event.getZoneId()).thenReturn(ZoneId.systemDefault());
    }


    @Test
    @DisplayName("create tickets for the unbounded category")
    void createTicketsForUnboundedCategory() {
        List<TicketCategory> categories = generateCategoryStream().limit(3).collect(Collectors.toList());
        when(ticketCategoryRepository.findAllTicketCategories(eq(eventId))).thenReturn(categories);
        MapSqlParameterSource[] parameterSources = eventManager.prepareTicketsBulkInsertParameters(ZonedDateTime.now(clockProvider().getClock()), event, availableSeats, Ticket.TicketStatus.FREE);
        assertNotNull(parameterSources);
        assertEquals(availableSeats, parameterSources.length);
        assertTrue(Arrays.stream(parameterSources).allMatch(ps -> Ticket.TicketStatus.FREE.name().equals(ps.getValue("status"))));
    }

    @Test
    @DisplayName("create tickets for the unbounded categories")
    void createTicketsForUnboundedCategories() {
        List<TicketCategory> categories = generateCategoryStream().limit(6).collect(Collectors.toList());
        when(ticketCategoryRepository.findAllTicketCategories(eq(eventId))).thenReturn(categories);
        MapSqlParameterSource[] parameterSources = eventManager.prepareTicketsBulkInsertParameters(ZonedDateTime.now(clockProvider().getClock()), event, availableSeats, Ticket.TicketStatus.FREE);
        assertNotNull(parameterSources);
        assertEquals(availableSeats, parameterSources.length);
        assertTrue(Arrays.stream(parameterSources).allMatch(ps -> Ticket.TicketStatus.FREE.name().equals(ps.getValue("status"))));
    }

    @Test
    @DisplayName("create tickets only for the bounded categories")
    void createTicketsOnlyForBounded() {
        List<TicketCategory> categories = generateCategoryStream().limit(2).collect(Collectors.toList());
        when(ticketCategoryRepository.findAllTicketCategories(eq(eventId))).thenReturn(categories);
        MapSqlParameterSource[] parameterSources = eventManager.prepareTicketsBulkInsertParameters(ZonedDateTime.now(clockProvider().getClock()), event, availableSeats, Ticket.TicketStatus.FREE);
        assertNotNull(parameterSources);
        assertEquals(availableSeats, parameterSources.length);
        assertEquals(4L, Arrays.stream(parameterSources).filter(p -> p.getValue("categoryId") != null).count());
        assertTrue(Arrays.stream(parameterSources).allMatch(ps -> Ticket.TicketStatus.FREE.name().equals(ps.getValue("status"))));
    }

}
