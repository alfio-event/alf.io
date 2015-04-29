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

import alfio.controller.form.UpdateTicketOwnerForm;
import alfio.manager.support.PartialTicketTextGenerator;
import alfio.manager.support.TextTemplateGenerator;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.Ticket;
import alfio.model.TicketReservation;
import alfio.repository.EventRepository;
import alfio.repository.PromoCodeDiscountRepository;
import alfio.repository.TicketRepository;
import alfio.repository.TicketReservationRepository;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.OrganizationRepository;
import com.insightfullogic.lambdabehave.JunitSuiteRunner;
import org.junit.runner.RunWith;
import org.springframework.context.MessageSource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;

import static alfio.model.system.ConfigurationKeys.ASSIGNMENT_REMINDER_START;
import static alfio.model.system.ConfigurationKeys.OFFLINE_PAYMENT_DAYS;
import static com.insightfullogic.lambdabehave.Suite.describe;
import static org.mockito.Mockito.*;

@RunWith(JunitSuiteRunner.class)
public class TicketReservationManagerTest {{
    describe("update Ticket owner", it -> {
        final String ticketId = "abcde";
        final String ticketReservationId = "abcdef";
        final String originalEmail = "me@myaddress.com";
        final String originalName = "First Last";
        Ticket original = mock(Ticket.class);
        Ticket modified = mock(Ticket.class);
        TicketRepository ticketRepository = mock(TicketRepository.class);
        Event event = mock(Event.class);
        UpdateTicketOwnerForm form = new UpdateTicketOwnerForm();
        when(event.getShortName()).thenReturn("short-name");
        NotificationManager notificationManager = it.usesMock(NotificationManager.class);
        MessageSource messageSource = mock(MessageSource.class);
        TicketReservationRepository ticketReservationRepository = mock(TicketReservationRepository.class);
        TicketReservationManager trm = new TicketReservationManager(null, null, ticketRepository, ticketReservationRepository, null, null, null, null, null, null, notificationManager, messageSource, null, null);

        it.initializesWith(() -> {
            when(original.getUuid()).thenReturn(ticketId);
            when(original.getEmail()).thenReturn(originalEmail);
            when(original.getFullName()).thenReturn(originalName);
            when(ticketRepository.findByUUID(eq(ticketId))).thenReturn(modified);
            form.setEmail("new@email.tld");
            form.setFullName(originalName);
        });

        it.should("not send the warning e-mail if the current user is admin", expect -> {
            TicketReservation reservation = mock(TicketReservation.class);
            when(original.getTicketsReservationId()).thenReturn(ticketReservationId);
            when(ticketReservationRepository.findReservationById(eq(ticketReservationId))).thenReturn(reservation);
            UserDetails userDetails = new User("user", "password", Collections.singletonList(new SimpleGrantedAuthority(AuthorityRepository.ROLE_ADMIN)));
            trm.updateTicketOwner(original, Locale.ENGLISH, event, form, (a) -> null,(b) -> null, (c) -> null, Optional.of(userDetails));
            verify(messageSource, never()).getMessage(eq("ticket-has-changed-owner-subject"), eq(new Object[] {"short-name"}), eq(Locale.ENGLISH));
        });

        it.should("send the warning e-mail otherwise", expect -> {
            PartialTicketTextGenerator ownerChangeTextBuilder = it.usesMock(PartialTicketTextGenerator.class);
            when(ownerChangeTextBuilder.generate(eq(modified))).thenReturn("Hello, world");
            trm.updateTicketOwner(original, Locale.ENGLISH, event, form, (a) -> null, ownerChangeTextBuilder, (c) -> null, Optional.empty());
            verify(messageSource, times(1)).getMessage(eq("ticket-has-changed-owner-subject"), any(), eq(Locale.ENGLISH));
            verify(notificationManager, times(1)).sendSimpleEmail(eq(event), eq(originalEmail), anyString(), eq("Hello, world"));
        });
    });

    describe("sendReminderForTicketAssignment", it -> {
        ConfigurationManager configurationManager = it.usesMock(ConfigurationManager.class);
        TicketRepository ticketRepository = it.usesMock(TicketRepository.class);
        EventRepository eventRepository = it.usesMock(EventRepository.class);
        TicketReservationRepository ticketReservationRepository = it.usesMock(TicketReservationRepository.class);
        TicketReservation reservation = it.usesMock(TicketReservation.class);
        PlatformTransactionManager transactionManager = it.usesMock(PlatformTransactionManager.class);
        OrganizationRepository organizationRepository = it.usesMock(OrganizationRepository.class);
        PromoCodeDiscountRepository promoCodeDiscountRepository = it.usesMock(PromoCodeDiscountRepository.class);
        NotificationManager notificationManager = mock(NotificationManager.class);
        MessageSource messageSource = it.usesMock(MessageSource.class);
        TicketReservationManager trm = new TicketReservationManager(eventRepository, organizationRepository, ticketRepository, ticketReservationRepository, null, configurationManager, null, promoCodeDiscountRepository, null, null, notificationManager, messageSource, null, transactionManager);
        it.initializesWith(() -> reset(notificationManager, eventRepository, organizationRepository, ticketRepository, ticketReservationRepository, configurationManager, promoCodeDiscountRepository, messageSource, transactionManager));
        it.should("send the reminder before event end", expect -> {
            when(configurationManager.getIntConfigValue(eq(ASSIGNMENT_REMINDER_START), anyInt())).thenReturn(10);
            when(configurationManager.getStringConfigValue(any())).thenReturn(Optional.empty());
            when(reservation.latestNotificationTimestamp(any())).thenReturn(Optional.empty());
            when(reservation.getId()).thenReturn("abcd");
            when(ticketReservationRepository.findReservationById(eq("abcd"))).thenReturn(reservation);
            Event event = it.usesMock(Event.class);
            when(eventRepository.findByReservationId("abcd")).thenReturn(event);
            when(event.getZoneId()).thenReturn(ZoneId.systemDefault());
            when(event.getBegin()).thenReturn(ZonedDateTime.now().plusDays(1));
            when(eventRepository.findAll()).thenReturn(Collections.singletonList(event));
            when(ticketRepository.findAllReservationsConfirmedButNotAssigned(anyInt())).thenReturn(Collections.singletonList("abcd"));
            trm.sendReminderForTicketAssignment();
            verify(notificationManager, times(1)).sendSimpleEmail(eq(event), anyString(), anyString(), any(TextTemplateGenerator.class));
        });

        it.should("not send the reminder after event end", expect -> {
            when(configurationManager.getIntConfigValue(eq(ASSIGNMENT_REMINDER_START), anyInt())).thenReturn(10);
            when(configurationManager.getStringConfigValue(any())).thenReturn(Optional.empty());
            when(reservation.latestNotificationTimestamp(any())).thenReturn(Optional.empty());
            when(reservation.getId()).thenReturn("abcd");
            when(ticketReservationRepository.findReservationById(eq("abcd"))).thenReturn(reservation);
            Event event = it.usesMock(Event.class);
            when(eventRepository.findByReservationId("abcd")).thenReturn(event);
            when(event.getZoneId()).thenReturn(ZoneId.systemDefault());
            when(event.getBegin()).thenReturn(ZonedDateTime.now().minusDays(1));
            when(eventRepository.findAll()).thenReturn(Collections.singletonList(event));
            when(ticketRepository.findAllReservationsConfirmedButNotAssigned(anyInt())).thenReturn(Collections.singletonList("abcd"));
            trm.sendReminderForTicketAssignment();
            verify(notificationManager, never()).sendSimpleEmail(eq(event), anyString(), anyString(), any(TextTemplateGenerator.class));
        });

        it.should("consider ZoneId while checking (valid)", expect -> {
            when(configurationManager.getIntConfigValue(eq(ASSIGNMENT_REMINDER_START), anyInt())).thenReturn(10);
            when(configurationManager.getStringConfigValue(any())).thenReturn(Optional.empty());
            when(reservation.latestNotificationTimestamp(any())).thenReturn(Optional.empty());
            when(reservation.getId()).thenReturn("abcd");
            when(ticketReservationRepository.findReservationById(eq("abcd"))).thenReturn(reservation);
            Event event = it.usesMock(Event.class);
            when(eventRepository.findByReservationId("abcd")).thenReturn(event);
            when(event.getZoneId()).thenReturn(ZoneId.of("GMT-4"));
            when(event.getBegin()).thenReturn(ZonedDateTime.now(ZoneId.of("GMT-4")).plusDays(1));
            when(eventRepository.findAll()).thenReturn(Collections.singletonList(event));
            when(ticketRepository.findAllReservationsConfirmedButNotAssigned(anyInt())).thenReturn(Collections.singletonList("abcd"));
            trm.sendReminderForTicketAssignment();
            verify(notificationManager, times(1)).sendSimpleEmail(eq(event), anyString(), anyString(), any(TextTemplateGenerator.class));
        });

        it.should("consider ZoneId while checking (expired)", expect -> {
            when(configurationManager.getIntConfigValue(eq(ASSIGNMENT_REMINDER_START), anyInt())).thenReturn(10);
            when(configurationManager.getStringConfigValue(any())).thenReturn(Optional.empty());
            when(reservation.latestNotificationTimestamp(any())).thenReturn(Optional.empty());
            when(reservation.getId()).thenReturn("abcd");
            when(ticketReservationRepository.findReservationById(eq("abcd"))).thenReturn(reservation);
            Event event = it.usesMock(Event.class);
            when(eventRepository.findByReservationId("abcd")).thenReturn(event);
            when(event.getZoneId()).thenReturn(ZoneId.of("UTC-8"));
            when(event.getBegin()).thenReturn(ZonedDateTime.now(ZoneId.of("UTC-8")));//same day
            when(eventRepository.findAll()).thenReturn(Collections.singletonList(event));
            when(ticketRepository.findAllReservationsConfirmedButNotAssigned(anyInt())).thenReturn(Collections.singletonList("abcd"));
            trm.sendReminderForTicketAssignment();
            verify(notificationManager, never()).sendSimpleEmail(eq(event), anyString(), anyString(), any(TextTemplateGenerator.class));
        });
    });

    describe("offlinePaymentDeadline", it -> {
        ConfigurationManager configurationManager = mock(ConfigurationManager.class);
        when(configurationManager.getIntConfigValue(eq(OFFLINE_PAYMENT_DAYS), anyInt())).thenReturn(2);
        Event event = mock(Event.class);
        when(event.getZoneId()).thenReturn(ZoneId.systemDefault());

        it.should("return the expire date as configured", expect -> {
            when(event.getBegin()).thenReturn(ZonedDateTime.now().plusDays(3));
            ZonedDateTime offlinePaymentDeadline = TicketReservationManager.getOfflinePaymentDeadline(event, configurationManager);
            expect.that(Period.between(LocalDate.now(), offlinePaymentDeadline.toLocalDate()).getDays()).is(2);
        });

        it.should("return the configured waiting time", expect -> {
            when(event.getBegin()).thenReturn(ZonedDateTime.now().plusDays(3));
            expect.that(TicketReservationManager.getOfflinePaymentWaitingPeriod(event, configurationManager)).is(2);
        });

        it.should("consider the event begin date when calculating the expiration date", expect -> {
            when(event.getBegin()).thenReturn(ZonedDateTime.now().plusDays(1));
            ZonedDateTime offlinePaymentDeadline = TicketReservationManager.getOfflinePaymentDeadline(event, configurationManager);
            expect.that(Period.between(LocalDate.now(), offlinePaymentDeadline.toLocalDate()).getDays()).is(1);
        });

        it.should("return the configured waiting time considering event start date", expect -> {
            when(event.getBegin()).thenReturn(ZonedDateTime.now().plusDays(1));
            expect.that(TicketReservationManager.getOfflinePaymentWaitingPeriod(event, configurationManager)).is(1);
        });

        it.should("never return a date in the past", expect -> {
            when(event.getBegin()).thenReturn(ZonedDateTime.now());
            ZonedDateTime offlinePaymentDeadline = TicketReservationManager.getOfflinePaymentDeadline(event, configurationManager);
            expect.that(offlinePaymentDeadline.isAfter(ZonedDateTime.now())).is(true);
        });

        it.should("throw an exception after event start", expect -> {
            when(event.getBegin()).thenReturn(ZonedDateTime.now().minusDays(1));
            expect.exception(IllegalArgumentException.class, () -> TicketReservationManager.getOfflinePaymentDeadline(event, configurationManager));
        });
    });
}}