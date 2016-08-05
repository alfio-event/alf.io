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

import alfio.manager.support.TextTemplateGenerator;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.WaitingQueueSubscription;
import alfio.model.modification.ASReservationWithOptionalCodeModification;
import alfio.model.modification.TicketReservationWithOptionalCodeModification;
import alfio.model.system.Configuration;
import alfio.repository.WaitingQueueRepository;
import alfio.util.TemplateManager;
import com.insightfullogic.lambdabehave.JunitSuiteRunner;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.runner.RunWith;
import org.springframework.context.MessageSource;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Stream;

import static alfio.model.system.ConfigurationKeys.ENABLE_WAITING_QUEUE;
import static com.insightfullogic.lambdabehave.Suite.describe;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

@RunWith(JunitSuiteRunner.class)
public class WaitingQueueSubscriptionProcessorTest {{
    describe("handleWaitingTickets", it -> {
        EventManager eventManager = it.usesMock(EventManager.class);
        TicketReservationManager ticketReservationManager = it.usesMock(TicketReservationManager.class);
        ConfigurationManager configurationManager = it.usesMock(ConfigurationManager.class);
        WaitingQueueManager waitingQueueManager = it.usesMock(WaitingQueueManager.class);
        NotificationManager notificationManager = it.usesMock(NotificationManager.class);
        MessageSource messageSource = it.usesMock(MessageSource.class);
        TemplateManager templateManager = it.usesMock(TemplateManager.class);
        WaitingQueueRepository waitingQueueRepository = it.usesMock(WaitingQueueRepository.class);
        WaitingQueueSubscriptionProcessor processor = new WaitingQueueSubscriptionProcessor(eventManager, ticketReservationManager, configurationManager, waitingQueueManager, notificationManager, waitingQueueRepository, messageSource, templateManager);
        final int eventId = 1;
        Event event = mock(Event.class);
        final String reservationId = "reservation-id";
        when(event.getId()).thenReturn(eventId);
        List<Event> activeEvents = Collections.singletonList(event);
        it.should("filter events whose 'waiting queue' flag is not active", expect -> {
            when(eventManager.getActiveEvents()).thenReturn(activeEvents);
            when(configurationManager.getBooleanConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_WAITING_QUEUE)), eq(false))).thenReturn(false);
            processor.handleWaitingTickets();
            verify(waitingQueueManager, never()).distributeSeats(eq(event));
        });
        it.should("process pending tickets", expect -> {
            when(eventManager.getActiveEvents()).thenReturn(activeEvents);
            when(configurationManager.getBooleanConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_WAITING_QUEUE)), eq(false))).thenReturn(true);
            when(messageSource.getMessage(anyString(), any(), eq(Locale.ENGLISH))).thenReturn("subject");
            WaitingQueueSubscription subscription = it.usesMock(WaitingQueueSubscription.class);
            when(subscription.getLocale()).thenReturn(Locale.ENGLISH);
            when(subscription.getEmailAddress()).thenReturn("me");
            TicketReservationWithOptionalCodeModification reservation = it.usesMock(TicketReservationWithOptionalCodeModification.class);
            ZonedDateTime expiration = ZonedDateTime.now().plusDays(1);
            when(waitingQueueManager.distributeSeats(eq(event))).thenReturn(Stream.of(Triple.of(subscription, reservation, expiration)));
            when(ticketReservationManager.createTicketReservation(eq(event), anyListOf(TicketReservationWithOptionalCodeModification.class), anyListOf(ASReservationWithOptionalCodeModification.class), any(Date.class), eq(Optional.empty()), eq(Optional.empty()), any(Locale.class), eq(true))).thenReturn(reservationId);
            processor.handleWaitingTickets();
            verify(configurationManager).getBooleanConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_WAITING_QUEUE)), eq(false));
            verify(ticketReservationManager).createTicketReservation(eq(event), eq(Collections.singletonList(reservation)), anyListOf(ASReservationWithOptionalCodeModification.class), eq(Date.from(expiration.toInstant())), eq(Optional.empty()), eq(Optional.empty()), eq(Locale.ENGLISH), eq(true));
            verify(notificationManager).sendSimpleEmail(eq(event), eq("me"), eq("subject"), any(TextTemplateGenerator.class));
        });
    });
}}