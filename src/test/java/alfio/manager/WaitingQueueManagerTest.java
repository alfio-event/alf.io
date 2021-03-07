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

import alfio.manager.i18n.MessageSourceManager;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.Ticket;
import alfio.model.WaitingQueueSubscription;
import alfio.model.modification.TicketReservationWithOptionalCodeModification;
import alfio.model.system.ConfigurationKeyValuePathLevel;
import alfio.repository.EventRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.TicketRepository;
import alfio.repository.WaitingQueueRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.TestUtil;
import alfio.util.ClockProvider;
import alfio.util.TemplateManager;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static alfio.model.system.ConfigurationKeys.ENABLE_PRE_REGISTRATION;
import static alfio.model.system.ConfigurationKeys.WAITING_QUEUE_RESERVATION_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("Waiting List Manager")
public class WaitingQueueManagerTest {

    private WaitingQueueRepository waitingQueueRepository;
    private EventStatisticsManager eventStatisticsManager;
    private TicketRepository ticketRepository;
    private TicketCategoryRepository ticketCategoryRepository;
    private ConfigurationManager configurationManager;
    private NotificationManager notificationManager;
    private TemplateManager templateManager;
    private MessageSource messageSource;
    private MessageSourceManager messageSourceManager;
    private OrganizationRepository organizationRepository;
    private EventRepository eventRepository;
    private ExtensionManager extensionManager;
    private Event event;
    private WaitingQueueManager manager;
    private final String reservationId = "reservation-id";
    private final int eventId = 1;


    @BeforeEach
    void setUp() {
        waitingQueueRepository = mock(WaitingQueueRepository.class);
        eventStatisticsManager = mock(EventStatisticsManager.class);
        ticketRepository = mock(TicketRepository.class);
        ticketCategoryRepository = mock(TicketCategoryRepository.class);
        configurationManager = mock(ConfigurationManager.class);
        notificationManager = mock(NotificationManager.class);
        templateManager = mock(TemplateManager.class);
        messageSource = mock(MessageSource.class);
        messageSourceManager = mock(MessageSourceManager.class);
        organizationRepository = mock(OrganizationRepository.class);
        eventRepository = mock(EventRepository.class);
        extensionManager = mock(ExtensionManager.class);
        event = mock(Event.class);
        when(event.getId()).thenReturn(eventId);
        when(event.now(any(ClockProvider.class))).thenCallRealMethod();
        manager = new WaitingQueueManager(waitingQueueRepository, ticketRepository, ticketCategoryRepository, configurationManager, eventStatisticsManager, notificationManager, templateManager, messageSourceManager, organizationRepository, eventRepository, extensionManager, TestUtil.clockProvider());
        when(messageSourceManager.getMessageSourceFor(any())).thenReturn(messageSource);
        when(messageSourceManager.getRootMessageSource()).thenReturn(messageSource);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(waitingQueueRepository, eventStatisticsManager, ticketRepository);
    }

    @Test
    @DisplayName("handle a reservation confirmation")
    void handleReservationConfirmation() {
        manager.fireReservationConfirmed(reservationId);
        verify(waitingQueueRepository).updateStatusByReservationId(eq(reservationId), eq(WaitingQueueSubscription.Status.ACQUIRED.toString()));
    }

    @Test
    @DisplayName("handle a reservation expiration")
    void handleReservationExpiration() {
        manager.fireReservationExpired(reservationId);
        verify(waitingQueueRepository).bulkUpdateExpiredReservations(eq(Collections.singletonList(reservationId)));
    }

    @Test
    @DisplayName("handle a bulk cancellation")
    void handleBulkCancellation() {
        List<String> reservationIds = Arrays.asList(reservationId, "id2");
        manager.cleanExpiredReservations(reservationIds);
        verify(waitingQueueRepository).bulkUpdateExpiredReservations(eq(reservationIds));
    }

    @Test
    @DisplayName("revert tickets to free if there isn't any subscriber")
    void revertTicketsIfNoSubscribers() {
        when(waitingQueueRepository.countWaitingPeople(eq(eventId))).thenReturn(0);
        when(ticketRepository.countWaiting(eq(eventId))).thenReturn(1);
        manager.distributeSeats(event);
        verify(waitingQueueRepository).loadAllWaitingForUpdate(eq(eventId));
        verify(ticketRepository).countWaiting(eq(eventId));
        verify(ticketRepository).revertToFree(eq(eventId));
    }

    @Test
    @DisplayName("do nothing if there are 0 subscribers and 0 waiting tickets")
    void doNothingIfNoSubscribersAndNoWaiting() {
        when(waitingQueueRepository.countWaitingPeople(eq(eventId))).thenReturn(0);
        when(ticketRepository.countWaiting(eq(eventId))).thenReturn(0);
        manager.distributeSeats(event);
        verify(waitingQueueRepository).loadAllWaitingForUpdate(eq(eventId));
        verify(ticketRepository).countWaiting(eq(eventId));
        verify(ticketRepository, never()).revertToFree(eq(eventId));
    }

    @Test
    void processPreReservations() {
        Ticket ticket = mock(Ticket.class);
        WaitingQueueSubscription subscription = mock(WaitingQueueSubscription.class);
        when(subscription.isPreSales()).thenReturn(true);
        when(eventRepository.countExistingTickets(eq(eventId))).thenReturn(10);
        when(event.getZoneId()).thenReturn(ZoneId.systemDefault());
        when(waitingQueueRepository.countWaitingPeople(eq(eventId))).thenReturn(1);
        when(ticketRepository.countWaiting(eq(eventId))).thenReturn(0);
        when(configurationManager.getFor(eq(ENABLE_PRE_REGISTRATION), any())).thenReturn(new ConfigurationManager.MaybeConfiguration(ENABLE_PRE_REGISTRATION, new ConfigurationKeyValuePathLevel(null, "true", null)));
        when(configurationManager.getFor(eq(WAITING_QUEUE_RESERVATION_TIMEOUT), any())).thenReturn(new ConfigurationManager.MaybeConfiguration(WAITING_QUEUE_RESERVATION_TIMEOUT));
        when(ticketRepository.selectWaitingTicketsForUpdate(eventId, Ticket.TicketStatus.PRE_RESERVED.name(), 1)).thenReturn(Collections.singletonList(ticket));
        when(waitingQueueRepository.loadAllWaitingForUpdate(eventId)).thenReturn(Collections.singletonList(subscription));
        when(waitingQueueRepository.loadWaiting(eventId, 1)).thenReturn(Collections.singletonList(subscription));
        Stream<Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime>> stream = manager.distributeSeats(event);
        assertEquals(1L, stream.count());
        verify(waitingQueueRepository).loadAllWaitingForUpdate(eq(eventId));
        verify(waitingQueueRepository).loadWaiting(eq(eventId), eq(1));
        verify(ticketRepository).countWaiting(eq(eventId));
        verify(ticketRepository, never()).revertToFree(eq(eventId));
        verify(ticketRepository).countPreReservedTickets(eq(eventId));
        verify(ticketRepository).preReserveTicket(anyList());
        verify(ticketRepository).selectWaitingTicketsForUpdate(eq(eventId), anyString(), anyInt());
    }
}