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

import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.WaitingQueueSubscription;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.TicketRepository;
import alfio.repository.WaitingQueueRepository;
import com.insightfullogic.lambdabehave.JunitSuiteRunner;
import org.junit.runner.RunWith;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Arrays;
import java.util.List;

import static com.insightfullogic.lambdabehave.Suite.describe;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

@RunWith(JunitSuiteRunner.class)
public class WaitingQueueManagerTest {{
    describe("basicOperations", it -> {
        WaitingQueueRepository waitingQueueRepository = it.usesMock(WaitingQueueRepository.class);
        EventManager eventManager = it.usesMock(EventManager.class);
        TicketRepository ticketRepository = it.usesMock(TicketRepository.class);
        TicketCategoryRepository ticketCategoryRepository = it.usesMock(TicketCategoryRepository.class);
        ConfigurationManager configurationManager = it.usesMock(ConfigurationManager.class);
        NamedParameterJdbcTemplate jdbc = it.usesMock(NamedParameterJdbcTemplate.class);

        WaitingQueueManager manager = new WaitingQueueManager(waitingQueueRepository, ticketRepository, ticketCategoryRepository, configurationManager, eventManager, null, jdbc);
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
        it.should("doesn't do anything if there are 0 subscribers and 0 waiting tickets", expect -> {
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
        it.completesWith(() -> verifyNoMoreInteractions(waitingQueueRepository, eventManager, ticketRepository));
    });
}}