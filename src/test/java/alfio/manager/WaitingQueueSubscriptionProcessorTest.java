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
import alfio.manager.support.TemplateGenerator;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.WaitingQueueSubscription;
import alfio.model.modification.TicketReservationWithOptionalCodeModification;
import alfio.model.system.ConfigurationKeyValuePathLevel;
import alfio.repository.TicketRepository;
import alfio.repository.WaitingQueueRepository;
import alfio.util.TemplateManager;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Stream;

import static alfio.model.system.ConfigurationKeys.ENABLE_PRE_REGISTRATION;
import static alfio.model.system.ConfigurationKeys.ENABLE_WAITING_QUEUE;
import static alfio.test.util.TestUtil.clockProvider;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class WaitingQueueSubscriptionProcessorTest {

    private EventManager eventManager;
    private TicketReservationManager ticketReservationManager;
    private ConfigurationManager configurationManager;
    private WaitingQueueManager waitingQueueManager;
    private NotificationManager notificationManager;
    private MessageSource messageSource;
    private TemplateManager templateManager;
    private WaitingQueueRepository waitingQueueRepository;
    private PlatformTransactionManager transactionManager;
    private Event event;
    private WaitingQueueSubscription subscription;
    private TicketReservationWithOptionalCodeModification reservation;
    private WaitingQueueSubscriptionProcessor processor;
    private TicketRepository ticketRepository;
    private MessageSourceManager messageSourceManager;


    @BeforeEach
    void setUp() {
        eventManager = mock(EventManager.class);
        ticketReservationManager = mock(TicketReservationManager.class);
        configurationManager = mock(ConfigurationManager.class);
        waitingQueueManager = mock(WaitingQueueManager.class);
        notificationManager = mock(NotificationManager.class);
        messageSource = mock(MessageSource.class);
        messageSourceManager = mock(MessageSourceManager.class);
        templateManager = mock(TemplateManager.class);
        waitingQueueRepository = mock(WaitingQueueRepository.class);
        transactionManager = mock(PlatformTransactionManager.class);
        event = mock(Event.class);
        subscription = mock(WaitingQueueSubscription.class);
        reservation = mock(TicketReservationWithOptionalCodeModification.class);
        ticketRepository = mock(TicketRepository.class);
        int eventId = 1;
        when(event.getId()).thenReturn(eventId);
        when(event.getZoneId()).thenReturn(ZoneId.systemDefault());
        when(eventManager.getActiveEvents()).thenReturn(Collections.singletonList(event));
        when(messageSourceManager.getMessageSourceFor(any())).thenReturn(messageSource);
        when(messageSourceManager.getRootMessageSource()).thenReturn(messageSource);
        processor = new WaitingQueueSubscriptionProcessor(eventManager,
            ticketReservationManager,
            configurationManager,
            waitingQueueManager,
            notificationManager,
            waitingQueueRepository,
            messageSourceManager,
            templateManager,
            ticketRepository,
            transactionManager,
            clockProvider());
    }

    @Test
    void filterWaitingQueueFlagIsNotActive() {
        when(configurationManager.getFor(eq(Set.of(ENABLE_WAITING_QUEUE, ENABLE_PRE_REGISTRATION)), any()))
            .thenReturn(Map.of(
                ENABLE_WAITING_QUEUE, new ConfigurationManager.MaybeConfiguration(ENABLE_WAITING_QUEUE, new ConfigurationKeyValuePathLevel( "", "false", null)),
                ENABLE_PRE_REGISTRATION, new ConfigurationManager.MaybeConfiguration(ENABLE_PRE_REGISTRATION)
            ));

        processor.handleWaitingTickets();
        verify(waitingQueueManager, never()).distributeSeats(eq(event));
    }

    @Test
    void processPendingTickets() {

        when(configurationManager.getFor(eq(Set.of(ENABLE_WAITING_QUEUE, ENABLE_PRE_REGISTRATION)), any()))
            .thenReturn(Map.of(
                ENABLE_WAITING_QUEUE, new ConfigurationManager.MaybeConfiguration(ENABLE_WAITING_QUEUE, new ConfigurationKeyValuePathLevel( "", "true", null)),
                ENABLE_PRE_REGISTRATION, new ConfigurationManager.MaybeConfiguration(ENABLE_PRE_REGISTRATION)
            ));

        when(messageSource.getMessage(anyString(), any(), eq(Locale.ENGLISH))).thenReturn("subject");
        when(subscription.getLocale()).thenReturn(Locale.ENGLISH);
        when(subscription.getEmailAddress()).thenReturn("me");
        ZonedDateTime expiration = ZonedDateTime.now(clockProvider().getClock()).plusDays(1);
        when(waitingQueueManager.distributeSeats(eq(event))).thenReturn(Stream.of(Triple.of(subscription, reservation, expiration)));
        String reservationId = "reservation-id";
        when(ticketReservationManager.createTicketReservation(eq(event), anyList(), anyList(), any(Date.class), eq(Optional.empty()), any(Locale.class), eq(true), isNull())).thenReturn(reservationId);
        processor.handleWaitingTickets();
        verify(ticketReservationManager).createTicketReservation(eq(event), eq(Collections.singletonList(reservation)), anyList(), eq(Date.from(expiration.toInstant())), eq(Optional.empty()), eq(Locale.ENGLISH), eq(true), isNull());
        verify(notificationManager).sendSimpleEmail(eq(event), eq(reservationId), eq("me"), eq("subject"), any(TemplateGenerator.class));
    }
}