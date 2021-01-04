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

import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.Ticket;
import alfio.model.TicketCategory;
import alfio.model.TicketCategoryStatisticView;
import alfio.model.user.Organization;
import alfio.repository.*;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.TestUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("EventManager: unbind tickets from category")
public class EventManagerUnbindTicketsTest {
    private final int eventId = 0;
    private final String eventName = "myEvent";
    private final String username = "username";
    private final int categoryId = 1;
    private final int organizationId = 2;

    private TicketCategoryRepository ticketCategoryRepository;
    private TicketCategoryDescriptionRepository ticketCategoryDescriptionRepository;
    private TicketRepository ticketRepository;
    private EventRepository eventRepository;
    private EventDescriptionRepository eventDescriptionRepository;
    private UserManager userManager;
    private SpecialPriceRepository specialPriceRepository;
    private Event event;
    private TicketCategory ticketCategory;
    private Organization organization;
    private OrganizationRepository organizationRepository;
    private EventManager eventManager;

    @BeforeEach
    void setUp() {
        ticketCategoryRepository = mock(TicketCategoryRepository.class);
        ticketCategoryDescriptionRepository = mock(TicketCategoryDescriptionRepository.class);
        ticketRepository = mock(TicketRepository.class);
        eventRepository = mock(EventRepository.class);
        eventDescriptionRepository = mock(EventDescriptionRepository.class);
        userManager = mock(UserManager.class);
        specialPriceRepository = mock(SpecialPriceRepository.class);
        event = mock(Event.class);
        ticketCategory = mock(TicketCategory.class);
        organization = mock(Organization.class);
        organizationRepository = mock(OrganizationRepository.class);


        when(specialPriceRepository.findAllByCategoryId(eq(categoryId))).thenReturn(Collections.emptyList());
        when(event.getId()).thenReturn(eventId);
        when(event.getOrganizationId()).thenReturn(organizationId);
        when(organization.getId()).thenReturn(organizationId);
        when(eventRepository.findOptionalEventAndOrganizationIdByShortName(eq(eventName))).thenReturn(Optional.of(event));
        when(ticketCategory.getId()).thenReturn(categoryId);
        when(organizationRepository.findOrganizationForUser(anyString(), anyInt())).thenReturn(Optional.of(organization));
        eventManager = new EventManager(userManager, eventRepository,
            eventDescriptionRepository, ticketCategoryRepository, ticketCategoryDescriptionRepository,
            ticketRepository, specialPriceRepository, null, null, null,
            null, null, null,
            null, null, organizationRepository,
            null, null, null, null, null,
            null, TestUtil.clockProvider(), mock(SubscriptionRepository.class));
    }

    @Test
    @DisplayName("should not unbind from an event which doesn't contain unbounded categories")
    void notUnbindIfNoUnboundCategories() {
        when(ticketCategoryRepository.countUnboundedCategoriesByEventId(eq(eventId))).thenReturn(0);
        when(userManager.findUserOrganizations(eq(username))).thenReturn(singletonList(organization));
        assertThrows(IllegalArgumentException.class, () -> eventManager.unbindTickets(eventName, categoryId, username));
        verify(ticketCategoryRepository).countUnboundedCategoriesByEventId(eq(eventId));
        verify(organizationRepository).findOrganizationForUser(eq(username), eq(organizationId));
        verify(eventRepository).findOptionalEventAndOrganizationIdByShortName(eq(eventName));
        verifyNoMoreInteractions(ticketCategoryRepository, userManager, eventRepository, ticketRepository);
    }

    @Test
    @DisplayName("should not unbind from a category which is not bounded")
    void notUnbindFromAnUnboundedCategory() {
        TicketCategoryStatisticView ticketCategoryStatisticView = mock(TicketCategoryStatisticView.class);
        when(ticketCategoryRepository.countUnboundedCategoriesByEventId(eq(eventId))).thenReturn(1);
        when(userManager.findUserOrganizations(eq(username))).thenReturn(singletonList(organization));
        when(ticketCategory.isBounded()).thenReturn(false);

        when(ticketCategoryRepository.findStatisticWithId(eq(categoryId), eq(eventId))).thenReturn(ticketCategoryStatisticView);

        assertThrows(IllegalArgumentException.class, () -> eventManager.unbindTickets(eventName, categoryId, username));
        verify(ticketCategoryRepository).countUnboundedCategoriesByEventId(eq(eventId));
        verify(organizationRepository).findOrganizationForUser(eq(username), eq(organizationId));
        verify(eventRepository).findOptionalEventAndOrganizationIdByShortName(eq(eventName));
    }

    @Test
    @DisplayName("should unbind tickets from a bounded category")
    void unbindTicketsFromBoundedCategory() {
        TicketCategoryStatisticView categoryStatisticView = mock(TicketCategoryStatisticView.class);
        when(ticketCategoryRepository.countUnboundedCategoriesByEventId(eq(eventId))).thenReturn(1);
        when(userManager.findUserOrganizations(eq(username))).thenReturn(singletonList(organization));
        when(ticketCategory.isBounded()).thenReturn(true);
        int notSold = 2;
        when(ticketCategory.getMaxTickets()).thenReturn(notSold);
        List<Integer> lockedTickets = Arrays.asList(1, 2);
        when(ticketRepository.selectTicketInCategoryForUpdate(eq(eventId), eq(categoryId), eq(notSold), eq(singletonList(Ticket.TicketStatus.FREE.name())))).thenReturn(lockedTickets);
        when(ticketRepository.unbindTicketsFromCategory(eq(eventId), eq(categoryId), eq(lockedTickets))).thenReturn(notSold);

        when(categoryStatisticView.isBounded()).thenReturn(true);
        when(categoryStatisticView.getNotSoldTicketsCount()).thenReturn(2);
        when(categoryStatisticView.getId()).thenReturn(categoryId);
        when(ticketCategoryRepository.findStatisticWithId(eq(categoryId), eq(eventId))).thenReturn(categoryStatisticView);

        eventManager.unbindTickets(eventName, categoryId, username);

        verify(ticketCategoryRepository).countUnboundedCategoriesByEventId(eq(eventId));
        verify(organizationRepository).findOrganizationForUser(eq(username), eq(organizationId));
        verify(eventRepository).findOptionalEventAndOrganizationIdByShortName(eq(eventName));
        verify(ticketRepository).selectTicketInCategoryForUpdate(eq(eventId), eq(categoryId), eq(notSold), eq(singletonList(Ticket.TicketStatus.FREE.name())));
        verify(ticketRepository).unbindTicketsFromCategory(eq(eventId), eq(categoryId), eq(lockedTickets));
        verify(ticketCategoryRepository).updateSeatsAvailability(eq(categoryId), eq(0));
    }
}