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

import alfio.manager.plugin.PluginManager;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.Ticket;
import alfio.model.WaitingQueueSubscription;
import alfio.model.modification.TicketReservationWithOptionalCodeModification;
import alfio.model.system.Configuration;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.TicketRepository;
import alfio.repository.WaitingQueueRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.TemplateManager;
import com.insightfullogic.lambdabehave.JunitSuiteRunner;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.runner.RunWith;
import org.springframework.context.MessageSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static alfio.model.system.ConfigurationKeys.ENABLE_PRE_REGISTRATION;
import static com.insightfullogic.lambdabehave.Suite.describe;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

@RunWith(JunitSuiteRunner.class)
public class WaitingQueueManagerTest {{
    describe("basicOperations", it -> {
        WaitingQueueRepository waitingQueueRepository = it.usesMock(WaitingQueueRepository.class);
        EventStatisticsManager eventStatisticsManager = it.usesMock(EventStatisticsManager.class);
        TicketRepository ticketRepository = it.usesMock(TicketRepository.class);
        TicketCategoryRepository ticketCategoryRepository = it.usesMock(TicketCategoryRepository.class);
        ConfigurationManager configurationManager = it.usesMock(ConfigurationManager.class);
        NamedParameterJdbcTemplate jdbc = it.usesMock(NamedParameterJdbcTemplate.class);
        NotificationManager notificationManager = it.usesMock(NotificationManager.class);
        TemplateManager templateManager = it.usesMock(TemplateManager.class);
        MessageSource messageSource = it.usesMock(MessageSource.class);
        OrganizationRepository organizationRepository = it.usesMock(OrganizationRepository.class);
        PluginManager pluginManager = it.usesMock(PluginManager.class);

        WaitingQueueManager manager = new WaitingQueueManager(waitingQueueRepository, ticketRepository, ticketCategoryRepository, configurationManager, eventStatisticsManager, jdbc, notificationManager, templateManager, messageSource, organizationRepository, pluginManager);
        String reservationId = "reservation-id";
        it.should("handle a reservation confirmation", expect -> {
            manager.fireReservationConfirmed(reservationId);
            verify(waitingQueueRepository).updateStatusByReservationId(eq(reservationId), eq(WaitingQueueSubscription.Status.ACQUIRED.toString()));
        });
        it.should("handle a reservation expiration", expect -> {
            manager.fireReservationExpired(reservationId);
            verify(waitingQueueRepository).updateStatusByReservationId(eq(reservationId), eq(WaitingQueueSubscription.Status.EXPIRED.toString()));
        });
        it.should("handle a bulk cancellation", expect -> {
            List<String> reservationIds = Arrays.asList(reservationId, "id2");
            manager.cleanExpiredReservations(reservationIds);
            verify(waitingQueueRepository).bulkUpdateExpiredReservations(eq(reservationIds));
        });
        it.should("revert tickets to free if there isn't any subscriber", expect -> {
            Event event = it.usesMock(Event.class);
            int eventId = 1;
            when(event.getId()).thenReturn(eventId);
            when(waitingQueueRepository.countWaitingPeople(eq(eventId))).thenReturn(0);
            when(ticketRepository.countWaiting(eq(eventId))).thenReturn(1);
            manager.distributeSeats(event);
            verify(waitingQueueRepository).countWaitingPeople(eq(eventId));
            verify(ticketRepository).countWaiting(eq(eventId));
            verify(ticketRepository).revertToFree(eq(eventId));
        });
        it.should("do nothing if there are 0 subscribers and 0 waiting tickets", expect -> {
            Event event = it.usesMock(Event.class);
            int eventId = 1;
            when(event.getId()).thenReturn(eventId);
            when(waitingQueueRepository.countWaitingPeople(eq(eventId))).thenReturn(0);
            when(ticketRepository.countWaiting(eq(eventId))).thenReturn(0);
            manager.distributeSeats(event);
            verify(waitingQueueRepository).countWaitingPeople(eq(eventId));
            verify(ticketRepository).countWaiting(eq(eventId));
            verify(ticketRepository, never()).revertToFree(eq(eventId));
        });
        it.should("process pre-reservations if there are 0 waiting tickets 1 or more subscribers", expect -> {
            Event event = it.usesMock(Event.class);
            int eventId = 1;
            when(event.getId()).thenReturn(eventId);
            when(event.getZoneId()).thenReturn(ZoneId.systemDefault());

            when(waitingQueueRepository.countWaitingPeople(eq(eventId))).thenReturn(1);
            when(ticketRepository.countWaiting(eq(eventId))).thenReturn(0);
            when(configurationManager.getBooleanConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_PRE_REGISTRATION), false)).thenReturn(true);
            Ticket ticket = it.usesMock(Ticket.class);
            when(ticketRepository.selectWaitingTicketsForUpdate(eventId, Ticket.TicketStatus.PRE_RESERVED.name(), 1)).thenReturn(Collections.singletonList(ticket));
            WaitingQueueSubscription subscription = it.usesMock(WaitingQueueSubscription.class);
            when(waitingQueueRepository.loadWaiting(eventId, 1)).thenReturn(Collections.singletonList(subscription));
            Stream<Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime>> stream = manager.distributeSeats(event);
            expect.that(stream.count()).is(1L);
            verify(waitingQueueRepository).countWaitingPeople(eq(eventId));
            verify(ticketRepository).countWaiting(eq(eventId));
            verify(ticketRepository, never()).revertToFree(eq(eventId));
        });
        it.completesWith(() -> verifyNoMoreInteractions(waitingQueueRepository, eventStatisticsManager, ticketRepository));
    });
}}