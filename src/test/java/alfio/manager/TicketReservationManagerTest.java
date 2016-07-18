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
import alfio.manager.plugin.PluginManager;
import alfio.manager.support.PartialTicketTextGenerator;
import alfio.manager.support.PaymentResult;
import alfio.manager.support.TextTemplateGenerator;
import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.model.Ticket.TicketStatus;
import alfio.model.TicketReservation.TicketReservationStatus;
import alfio.model.modification.TicketReservationWithOptionalCodeModification;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Organization;
import alfio.model.user.Role;
import alfio.repository.*;
import alfio.repository.user.OrganizationRepository;
import com.insightfullogic.lambdabehave.JunitSuiteRunner;
import org.junit.runner.RunWith;
import org.springframework.context.MessageSource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static alfio.model.TicketReservation.TicketReservationStatus.*;
import static alfio.model.system.ConfigurationKeys.ASSIGNMENT_REMINDER_START;
import static alfio.model.system.ConfigurationKeys.OFFLINE_PAYMENT_DAYS;
import static com.insightfullogic.lambdabehave.Suite.describe;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
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
        MessageSource messageSource = it.usesMock(MessageSource.class);
        TicketReservationRepository ticketReservationRepository = mock(TicketReservationRepository.class);
        PluginManager pluginManager = mock(PluginManager.class);
        FileUploadManager fileUploadManager = mock(FileUploadManager.class);
        TicketFieldRepository ticketFieldRepository = mock(TicketFieldRepository.class);
        TicketReservationManager trm = new TicketReservationManager(null, null, ticketRepository, ticketReservationRepository, null, null, null, null, null, null, null, notificationManager, messageSource, null, null, null, pluginManager, fileUploadManager, ticketFieldRepository, null, null, null);

        it.initializesWith(() -> {
            when(original.getUuid()).thenReturn(ticketId);
            when(original.getEmail()).thenReturn(originalEmail);
            when(original.getFullName()).thenReturn(originalName);
            when(ticketRepository.findByUUID(eq(ticketId))).thenReturn(modified);
            form.setEmail("new@email.tld");
            form.setFullName(originalName);
            form.setUserLanguage("it");
        });

        it.should("not send the warning e-mail if the current user is admin", expect -> {
            TicketReservation reservation = mock(TicketReservation.class);
            when(original.getTicketsReservationId()).thenReturn(ticketReservationId);
            when(ticketReservationRepository.findReservationById(eq(ticketReservationId))).thenReturn(reservation);
            UserDetails userDetails = new User("user", "password", singletonList(new SimpleGrantedAuthority(Role.ADMIN.getRoleName())));
            trm.updateTicketOwner(original, Locale.ENGLISH, event, form, (a) -> null,(b) -> null, (c) -> null, Optional.of(userDetails));
            verify(messageSource, never()).getMessage(eq("ticket-has-changed-owner-subject"), eq(new Object[] {"short-name"}), eq(Locale.ITALIAN));
        });

        it.should("send the warning e-mail otherwise", expect -> {
            PartialTicketTextGenerator ownerChangeTextBuilder = it.usesMock(PartialTicketTextGenerator.class);
            when(ownerChangeTextBuilder.generate(eq(modified))).thenReturn("Hello, world");
            when(original.getUserLanguage()).thenReturn("it");
            trm.updateTicketOwner(original, Locale.ENGLISH, event, form, (a) -> null, ownerChangeTextBuilder, (c) -> null, Optional.empty());
            verify(messageSource, times(1)).getMessage(eq("ticket-has-changed-owner-subject"), any(), eq(Locale.ITALIAN));
            verify(notificationManager, times(1)).sendSimpleEmail(eq(event), eq(originalEmail), anyString(), any(TextTemplateGenerator.class));
        });

        it.should("fall back to the current locale", expect -> {
            form.setUserLanguage("");
            PartialTicketTextGenerator ownerChangeTextBuilder = it.usesMock(PartialTicketTextGenerator.class);
            when(ownerChangeTextBuilder.generate(eq(modified))).thenReturn("Hello, world");
            trm.updateTicketOwner(original, Locale.ENGLISH, event, form, (a) -> null, ownerChangeTextBuilder, (c) -> null, Optional.empty());
            verify(messageSource, times(1)).getMessage(eq("ticket-has-changed-owner-subject"), any(), eq(Locale.ITALIAN));
            verify(notificationManager, times(1)).sendTicketByEmail(eq(modified), eq(event), eq(Locale.ENGLISH), any(), any());
            verify(notificationManager, times(1)).sendSimpleEmail(eq(event), eq(originalEmail), anyString(), any(TextTemplateGenerator.class));
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
        PluginManager pluginManager = mock(PluginManager.class);
        FileUploadManager fileUploadManager = mock(FileUploadManager.class);
        TicketFieldRepository ticketFieldRepository = mock(TicketFieldRepository.class);
        TicketReservationManager trm = new TicketReservationManager(eventRepository, organizationRepository, ticketRepository, ticketReservationRepository, null, null, configurationManager, null, promoCodeDiscountRepository, null, null, notificationManager, messageSource, null, transactionManager, null, pluginManager, fileUploadManager, ticketFieldRepository, null, null, null);
        it.initializesWith(() -> reset(notificationManager, eventRepository, organizationRepository, ticketRepository, ticketReservationRepository, configurationManager, promoCodeDiscountRepository, messageSource, transactionManager));
        it.should("send the reminder before event end", expect -> {
            Event event = it.usesMock(Event.class);

            when(configurationManager.getIntConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ASSIGNMENT_REMINDER_START)), anyInt())).thenReturn(10);
            when(configurationManager.getStringConfigValue(any())).thenReturn(Optional.empty());
            when(reservation.latestNotificationTimestamp(any())).thenReturn(Optional.empty());
            when(reservation.getId()).thenReturn("abcd");
            when(ticketReservationRepository.findReservationById(eq("abcd"))).thenReturn(reservation);

            when(eventRepository.findByReservationId("abcd")).thenReturn(event);
            when(event.getZoneId()).thenReturn(ZoneId.systemDefault());
            when(event.getBegin()).thenReturn(ZonedDateTime.now().plusDays(1));
            when(eventRepository.findAll()).thenReturn(singletonList(event));
            when(ticketRepository.findAllReservationsConfirmedButNotAssigned(anyInt())).thenReturn(singletonList("abcd"));
            trm.sendReminderForTicketAssignment();
            verify(notificationManager, times(1)).sendSimpleEmail(eq(event), anyString(), anyString(), any(TextTemplateGenerator.class));
        });

        it.should("not send the reminder after event end", expect -> {
            Event event = it.usesMock(Event.class);

            when(configurationManager.getIntConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ASSIGNMENT_REMINDER_START)), anyInt())).thenReturn(10);
            when(configurationManager.getStringConfigValue(any())).thenReturn(Optional.empty());
            when(reservation.latestNotificationTimestamp(any())).thenReturn(Optional.empty());
            when(reservation.getId()).thenReturn("abcd");
            when(ticketReservationRepository.findReservationById(eq("abcd"))).thenReturn(reservation);

            when(eventRepository.findByReservationId("abcd")).thenReturn(event);
            when(event.getZoneId()).thenReturn(ZoneId.systemDefault());
            when(event.getBegin()).thenReturn(ZonedDateTime.now().minusDays(1));
            when(eventRepository.findAll()).thenReturn(singletonList(event));
            when(ticketRepository.findAllReservationsConfirmedButNotAssigned(anyInt())).thenReturn(singletonList("abcd"));
            trm.sendReminderForTicketAssignment();
            verify(notificationManager, never()).sendSimpleEmail(eq(event), anyString(), anyString(), any(TextTemplateGenerator.class));
        });

        it.should("consider ZoneId while checking (valid)", expect -> {
            Event event = it.usesMock(Event.class);

            when(configurationManager.getIntConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ASSIGNMENT_REMINDER_START)), anyInt())).thenReturn(10);
            when(configurationManager.getStringConfigValue(any())).thenReturn(Optional.empty());
            when(reservation.latestNotificationTimestamp(any())).thenReturn(Optional.empty());
            when(reservation.getId()).thenReturn("abcd");
            when(ticketReservationRepository.findReservationById(eq("abcd"))).thenReturn(reservation);

            when(eventRepository.findByReservationId("abcd")).thenReturn(event);
            when(event.getZoneId()).thenReturn(ZoneId.of("GMT-4"));
            when(event.getBegin()).thenReturn(ZonedDateTime.now(ZoneId.of("GMT-4")).plusDays(1));
            when(eventRepository.findAll()).thenReturn(singletonList(event));
            when(ticketRepository.findAllReservationsConfirmedButNotAssigned(anyInt())).thenReturn(singletonList("abcd"));
            trm.sendReminderForTicketAssignment();
            verify(notificationManager, times(1)).sendSimpleEmail(eq(event), anyString(), anyString(), any(TextTemplateGenerator.class));
        });

        it.should("consider ZoneId while checking (expired)", expect -> {
            Event event = it.usesMock(Event.class);
            when(configurationManager.getIntConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ASSIGNMENT_REMINDER_START)), anyInt())).thenReturn(10);
            when(configurationManager.getStringConfigValue(any())).thenReturn(Optional.empty());
            when(reservation.latestNotificationTimestamp(any())).thenReturn(Optional.empty());
            when(reservation.getId()).thenReturn("abcd");
            when(ticketReservationRepository.findReservationById(eq("abcd"))).thenReturn(reservation);

            when(eventRepository.findByReservationId("abcd")).thenReturn(event);
            when(event.getZoneId()).thenReturn(ZoneId.of("UTC-8"));
            when(event.getBegin()).thenReturn(ZonedDateTime.now(ZoneId.of("UTC-8")));//same day
            when(eventRepository.findAll()).thenReturn(singletonList(event));
            when(ticketRepository.findAllReservationsConfirmedButNotAssigned(anyInt())).thenReturn(singletonList("abcd"));
            trm.sendReminderForTicketAssignment();
            verify(notificationManager, never()).sendSimpleEmail(eq(event), anyString(), anyString(), any(TextTemplateGenerator.class));
        });

        it.should("not send the reminder too early", expect -> {
            Event event = it.usesMock(Event.class);
            when(configurationManager.getIntConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ASSIGNMENT_REMINDER_START)), anyInt())).thenReturn(10);
            when(configurationManager.getStringConfigValue(any())).thenReturn(Optional.empty());
            when(reservation.latestNotificationTimestamp(any())).thenReturn(Optional.empty());
            when(reservation.getId()).thenReturn("abcd");
            when(ticketReservationRepository.findReservationById(eq("abcd"))).thenReturn(reservation);

            when(eventRepository.findByReservationId("abcd")).thenReturn(event);
            when(event.getZoneId()).thenReturn(ZoneId.of("UTC-8"));
            when(event.getBegin()).thenReturn(ZonedDateTime.now(ZoneId.of("UTC-8")).plusMonths(3).plusDays(1));
            when(eventRepository.findAll()).thenReturn(singletonList(event));
            when(ticketRepository.findAllReservationsConfirmedButNotAssigned(anyInt())).thenReturn(singletonList("abcd"));
            List<Event> events = trm.getNotifiableEventsStream().collect(Collectors.toList());
            expect.that(events.size()).isEqualTo(0);
            verify(notificationManager, never()).sendSimpleEmail(eq(event), anyString(), anyString(), any(TextTemplateGenerator.class));
        });
    });

    describe("offlinePaymentDeadline", it -> {
        ConfigurationManager configurationManager = mock(ConfigurationManager.class);
        Event event = mock(Event.class);
        when(configurationManager.getIntConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), OFFLINE_PAYMENT_DAYS)), anyInt())).thenReturn(2);

        when(event.getZoneId()).thenReturn(ZoneId.systemDefault());

        it.should("return the expire date as configured", expect -> {
            when(event.getBegin()).thenReturn(ZonedDateTime.now().plusDays(3));
            ZonedDateTime offlinePaymentDeadline = TicketReservationManager.getOfflinePaymentDeadline(event, configurationManager);
            expect.that(ChronoUnit.DAYS.between(LocalDate.now(), offlinePaymentDeadline.toLocalDate())).is(2L);
        });

        it.should("return the configured waiting time", expect -> {
            when(event.getBegin()).thenReturn(ZonedDateTime.now().plusDays(3));
            expect.that(TicketReservationManager.getOfflinePaymentWaitingPeriod(event, configurationManager)).is(2);
        });

        it.should("consider the event begin date when calculating the expiration date", expect -> {
            when(event.getBegin()).thenReturn(ZonedDateTime.now().plusDays(1));
            ZonedDateTime offlinePaymentDeadline = TicketReservationManager.getOfflinePaymentDeadline(event, configurationManager);
            expect.that(ChronoUnit.DAYS.between(LocalDate.now(), offlinePaymentDeadline.toLocalDate())).is(1L);
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

    describe("fixToken", it -> {
        int ticketCategoryId = 0;
        int eventId = 0;
        TicketRepository ticketRepository = it.usesMock(TicketRepository.class);
        SpecialPriceRepository specialPriceRepository = it.usesMock(SpecialPriceRepository.class);
        TicketReservationRepository ticketReservationRepository = it.usesMock(TicketReservationRepository.class);
        TicketCategoryRepository ticketCategoryRepository = mock(TicketCategoryRepository.class);
        TicketCategoryDescriptionRepository ticketCategoryDescriptionRepository = mock(TicketCategoryDescriptionRepository.class);
        TicketCategory tc = it.usesMock(TicketCategory.class);
        WaitingQueueManager waitingQueueManager = it.usesMock(WaitingQueueManager.class);
        PluginManager pluginManager = mock(PluginManager.class);
        FileUploadManager fileUploadManager = mock(FileUploadManager.class);
        TicketFieldRepository ticketFieldRepository = mock(TicketFieldRepository.class);
        AdditionalServiceItemRepository additionalServiceItemRepository = mock(AdditionalServiceItemRepository.class);
        TicketReservationManager ticketReservationManager = new TicketReservationManager(null, null, ticketRepository, ticketReservationRepository, ticketCategoryRepository, ticketCategoryDescriptionRepository,
            null, null, null, specialPriceRepository, null, null, null, null, null, waitingQueueManager, pluginManager, fileUploadManager, ticketFieldRepository, null, additionalServiceItemRepository, null);
        String specialPriceCode = "SPECIAL-PRICE";
        String specialPriceSessionId = "session-id";
        int specialPriceId = -42;
        SpecialPrice specialPrice = mock(SpecialPrice.class);
        when(specialPrice.getCode()).thenReturn(specialPriceCode);
        when(specialPrice.getId()).thenReturn(specialPriceId);
        when(ticketCategoryRepository.getById(eq(ticketCategoryId), eq(eventId))).thenReturn(tc);
        it.should("do nothing if the category is not restricted", expect -> {
            when(tc.isAccessRestricted()).thenReturn(false);
            Optional<SpecialPrice> result = ticketReservationManager.fixToken(Optional.empty(), ticketCategoryId, eventId, Optional.empty(), null);
            expect.that(result.isPresent()).is(false);
        });

        it.should("do nothing if sessionId is not present", expect -> {
            Optional<SpecialPrice> renewed = ticketReservationManager.renewSpecialPrice(Optional.of(specialPrice), Optional.empty());
            expect.that(renewed.isPresent()).is(false);
        });

        it.should("do nothing if special price status is pending and sessionId don't match", expect -> {
            when(specialPrice.getStatus()).thenReturn(SpecialPrice.Status.PENDING);
            when(specialPrice.getSessionIdentifier()).thenReturn("another-id");
            Optional<SpecialPrice> renewed = ticketReservationManager.renewSpecialPrice(Optional.of(specialPrice), Optional.of(specialPriceSessionId));
            expect.that(renewed.isPresent()).is(false);
        });

        it.should("renew special price", expect -> {
            when(tc.isAccessRestricted()).thenReturn(true);
            when(specialPrice.getStatus()).thenReturn(SpecialPrice.Status.FREE);
            when(specialPriceRepository.getByCode(eq(specialPriceCode))).thenReturn(specialPrice);
            Optional<SpecialPrice> renewed = ticketReservationManager.renewSpecialPrice(Optional.of(specialPrice), Optional.of(specialPriceSessionId));
            verify(specialPriceRepository).bindToSession(eq(specialPriceId), eq(specialPriceSessionId));
            expect.that(renewed.isPresent()).is(true);
            expect.that(renewed.get()).is(specialPrice);
        });

        it.should("cancel the pending reservation and renew the code", expect -> {
            Ticket ticket = mock(Ticket.class);
            String reservationId = "rid";
            when(ticket.getTicketsReservationId()).thenReturn(reservationId);
            when(ticketRepository.findBySpecialPriceId(eq(specialPriceId))).thenReturn(ticket);
            TicketReservation reservation = mock(TicketReservation.class);
            when(ticketReservationRepository.findReservationById(eq(reservationId))).thenReturn(reservation);
            when(reservation.getStatus()).thenReturn(TicketReservationStatus.PENDING);
            when(reservation.getId()).thenReturn(reservationId);
            when(ticketRepository.freeFromReservation(eq(singletonList(reservationId)))).thenReturn(1);
            when(ticketReservationRepository.remove(eq(singletonList(reservationId)))).thenReturn(1);
            when(specialPriceRepository.getByCode(eq(specialPriceCode))).thenReturn(specialPrice);
            when(specialPrice.getStatus()).thenReturn(SpecialPrice.Status.PENDING);
            when(specialPrice.getSessionIdentifier()).thenReturn(specialPriceSessionId);
            Optional<SpecialPrice> renewed = ticketReservationManager.renewSpecialPrice(Optional.of(specialPrice), Optional.of(specialPriceSessionId));
            verify(specialPriceRepository).updateStatusForReservation(eq(singletonList(reservationId)), eq(SpecialPrice.Status.FREE.toString()));
            verify(ticketRepository).freeFromReservation(eq(singletonList(reservationId)));
            verify(ticketReservationRepository).remove(eq(singletonList(reservationId)));
            verify(waitingQueueManager).fireReservationExpired(eq(reservationId));
            expect.that(renewed.isPresent()).is(true);
            expect.that(renewed.get()).is(specialPrice);
        });

    });

    describe("reserveTicketsForCategory", it -> {
        int ticketCategoryId = 0;
        int eventId = 0;
        TicketRepository ticketRepository = it.usesMock(TicketRepository.class);
        TicketReservationRepository ticketReservationRepository = it.usesMock(TicketReservationRepository.class);
        TicketCategoryRepository ticketCategoryRepository = mock(TicketCategoryRepository.class);
        TicketCategoryDescriptionRepository ticketCategoryDescriptionRepository = mock(TicketCategoryDescriptionRepository.class);
        TicketCategory tc = it.usesMock(TicketCategory.class);
        PluginManager pluginManager = mock(PluginManager.class);
        FileUploadManager fileUploadManager = mock(FileUploadManager.class);
        TicketFieldRepository ticketFieldRepository = mock(TicketFieldRepository.class);
        TicketReservationManager ticketReservationManager = new TicketReservationManager(null, null, ticketRepository, ticketReservationRepository, ticketCategoryRepository, ticketCategoryDescriptionRepository,
            null, null, null, null, null, null, null, null, null, null, pluginManager, fileUploadManager, ticketFieldRepository, null, null, null);
        when(ticketCategoryRepository.getById(eq(ticketCategoryId), eq(eventId))).thenReturn(tc);
        TicketReservationWithOptionalCodeModification trm = it.usesMock(TicketReservationWithOptionalCodeModification.class);

        it.should("reserve tickets for bounded categories", expect -> {
            when(tc.isBounded()).thenReturn(true);
            List<Integer> ids = singletonList(1);
            when(ticketRepository.selectTicketInCategoryForUpdate(eq(eventId), eq(ticketCategoryId), eq(1), eq(singletonList(Ticket.TicketStatus.FREE.name())))).thenReturn(ids);
            when(trm.getAmount()).thenReturn(1);
            when(trm.getTicketCategoryId()).thenReturn(ticketCategoryId);
            ticketReservationManager.reserveTicketsForCategory(eventId, Optional.empty(), "trid", trm, Locale.ENGLISH, false);
            verify(ticketRepository).reserveTickets("trid", ids, ticketCategoryId, Locale.ENGLISH.getLanguage());
        });

        it.should("reserve tickets for bounded categories (waiting queue)", expect -> {
            when(tc.isBounded()).thenReturn(true);
            List<Integer> ids = singletonList(1);
            when(ticketRepository.selectTicketInCategoryForUpdate(eq(eventId), eq(ticketCategoryId), eq(1), eq(asList(TicketStatus.RELEASED.name(), TicketStatus.PRE_RESERVED.name())))).thenReturn(ids);
            when(trm.getAmount()).thenReturn(1);
            when(trm.getTicketCategoryId()).thenReturn(ticketCategoryId);
            ticketReservationManager.reserveTicketsForCategory(eventId, Optional.empty(), "trid", trm, Locale.ENGLISH, true);
            verify(ticketRepository).reserveTickets("trid", ids, ticketCategoryId, Locale.ENGLISH.getLanguage());
        });

        it.should("reserve tickets for unbounded categories", expect -> {
            when(tc.isBounded()).thenReturn(false);
            List<Integer> ids = singletonList(1);
            when(ticketRepository.selectNotAllocatedTicketsForUpdate(eq(eventId), eq(1), eq(singletonList(Ticket.TicketStatus.FREE.name())))).thenReturn(ids);
            when(trm.getAmount()).thenReturn(1);
            when(trm.getTicketCategoryId()).thenReturn(ticketCategoryId);
            ticketReservationManager.reserveTicketsForCategory(eventId, Optional.empty(), "trid", trm, Locale.ENGLISH, false);
            verify(ticketRepository).reserveTickets("trid", ids, ticketCategoryId, Locale.ENGLISH.getLanguage());
        });

        it.should("reserve tickets for unbounded categories (waiting queue)", expect -> {
            when(tc.isBounded()).thenReturn(false);
            List<Integer> ids = singletonList(1);
            when(ticketRepository.selectNotAllocatedTicketsForUpdate(eq(eventId), eq(1), eq(asList(TicketStatus.RELEASED.name(), TicketStatus.PRE_RESERVED.name())))).thenReturn(ids);
            when(trm.getAmount()).thenReturn(1);
            when(trm.getTicketCategoryId()).thenReturn(ticketCategoryId);
            ticketReservationManager.reserveTicketsForCategory(eventId, Optional.empty(), "trid", trm, Locale.ENGLISH, true);
            verify(ticketRepository).reserveTickets("trid", ids, ticketCategoryId, Locale.ENGLISH.getLanguage());
        });

    });

    describe("cleanupExpiredReservations", it -> {
        Date now = new Date();
        List<String> reservationIds = singletonList("reservation-id");
        TicketReservationRepository ticketReservationRepository = it.usesMock(TicketReservationRepository.class);
        SpecialPriceRepository specialPriceRepository = it.usesMock(SpecialPriceRepository.class);
        TicketRepository ticketRepository = it.usesMock(TicketRepository.class);
        WaitingQueueManager waitingQueueManager = it.usesMock(WaitingQueueManager.class);
        PluginManager pluginManager = mock(PluginManager.class);
        FileUploadManager fileUploadManager = mock(FileUploadManager.class);
        TicketFieldRepository ticketFieldRepository = mock(TicketFieldRepository.class);
        TicketReservationManager ticketReservationManager = new TicketReservationManager(null, null, ticketRepository, ticketReservationRepository, null, null, null, null, null, specialPriceRepository, null, null, null, null, null, waitingQueueManager, pluginManager, fileUploadManager, ticketFieldRepository, null, null, null);
        it.should("do nothing if there are no reservations", expect -> {
            when(ticketReservationRepository.findExpiredReservation(eq(now))).thenReturn(Collections.emptyList());
            ticketReservationManager.cleanupExpiredReservations(now);
            verify(ticketReservationRepository).findExpiredReservation(eq(now));
            verifyNoMoreInteractions(ticketReservationRepository, specialPriceRepository, ticketRepository, waitingQueueManager);
        });
        it.should("cancel the expired reservations", expect -> {
            when(ticketReservationRepository.findExpiredReservation(eq(now))).thenReturn(reservationIds);
            ticketReservationManager.cleanupExpiredReservations(now);
            verify(ticketReservationRepository).findExpiredReservation(eq(now));
            verify(specialPriceRepository).updateStatusForReservation(eq(reservationIds), eq(SpecialPrice.Status.FREE.toString()));
            verify(ticketRepository).resetCategoryIdForUnboundedCategories(eq(reservationIds));
            verify(ticketRepository).freeFromReservation(eq(reservationIds));
            verify(ticketReservationRepository).remove(eq(reservationIds));
            verify(waitingQueueManager).cleanExpiredReservations(eq(reservationIds));
            verifyNoMoreInteractions(ticketReservationRepository, specialPriceRepository, ticketRepository);
        });
    });

    describe("countAvailableTickets", it -> {
        TicketRepository ticketRepository = it.usesMock(TicketRepository.class);
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(42);
        TicketCategory category = it.usesMock(TicketCategory.class);
        PluginManager pluginManager = mock(PluginManager.class);
        TicketFieldRepository ticketFieldRepository = mock(TicketFieldRepository.class);
        TicketReservationManager ticketReservationManager = new TicketReservationManager(null, null, ticketRepository, null, null, null, null, null, null, null, null, null, null, null, null, null, pluginManager, null, ticketFieldRepository, null, null, null);
        it.should("count how many tickets are yet available for a category", expect -> {
            when(category.isBounded()).thenReturn(true);
            when(category.getId()).thenReturn(24);
            ticketReservationManager.countAvailableTickets(event, category);
            verify(ticketRepository).countNotSoldTickets(eq(42), eq(24));
        });
        it.should("count how many tickets are available for unbounded categories", expect -> {
            when(category.isBounded()).thenReturn(false);
            ticketReservationManager.countAvailableTickets(event, category);
            verify(ticketRepository).countNotSoldTicketsForUnbounded(eq(42));
        });

    });

    describe("releaseTicket", it -> {
        TicketRepository ticketRepository = it.usesMock(TicketRepository.class);
        NotificationManager notificationManager = it.usesMock(NotificationManager.class);
        MessageSource messageSource = it.usesMock(MessageSource.class);
        TicketCategoryRepository ticketCategoryRepository = mock(TicketCategoryRepository.class);
        TicketCategoryDescriptionRepository ticketCategoryDescriptionRepository = mock(TicketCategoryDescriptionRepository.class);
        OrganizationRepository organizationRepository = it.usesMock(OrganizationRepository.class);
        TicketReservationRepository ticketReservationRepository = it.usesMock(TicketReservationRepository.class);
        PluginManager pluginManager = mock(PluginManager.class);
        FileUploadManager fileUploadManager = mock(FileUploadManager.class);
        TicketFieldRepository ticketFieldRepository = mock(TicketFieldRepository.class);
        TicketReservationManager ticketReservationManager = new TicketReservationManager(null, organizationRepository, ticketRepository, ticketReservationRepository, ticketCategoryRepository, ticketCategoryDescriptionRepository,
            null, null, null, null, null, notificationManager, messageSource, null, null, null, pluginManager, fileUploadManager, ticketFieldRepository, null, null, null);
        TicketReservation ticketReservation = mock(TicketReservation.class);
        Ticket ticket = mock(Ticket.class);
        Event event = mock(Event.class);
        int eventId = 42;
        int ticketId = 2048756;
        int categoryId = 657;
        int organizationId = 938249873;
        String reservationId = "what-a-reservation!!";
        when(ticket.getId()).thenReturn(ticketId);
        when(ticketCategoryDescriptionRepository.findByTicketCategoryIdAndLocale(anyInt(), anyString())).thenReturn(Optional.of("desc"));
        String reservationEmail = "me@mydomain.com";
        when(ticket.getEmail()).thenReturn(reservationEmail);
        when(ticket.getUserLanguage()).thenReturn("it");
        when(ticket.getEventId()).thenReturn(eventId);
        when(event.getId()).thenReturn(eventId);
        when(event.getOrganizationId()).thenReturn(organizationId);
        when(ticket.getCategoryId()).thenReturn(categoryId);
        when(ticketReservation.getId()).thenReturn(reservationId);
        String eventName = "VoxxedDaysTicino";
        TicketCategory ticketCategory = it.usesMock(TicketCategory.class);
        Organization organization = it.usesMock(Organization.class);
        it.isSetupWith(() -> {
            when(ticketCategoryRepository.getById(eq(categoryId), eq(eventId))).thenReturn(ticketCategory);
            when(organizationRepository.getById(eq(organizationId))).thenReturn(organization);
        });

        when(event.getShortName()).thenReturn(eventName);
        it.should("send an e-mail to the assignee on success", expect -> {
            String organizationEmail = "ciccio@test";
            when(organization.getEmail()).thenReturn(organizationEmail);
            when(ticketCategory.isAccessRestricted()).thenReturn(false);
            when(ticketRepository.releaseTicket(eq(reservationId), eq(eventId), eq(ticketId))).thenReturn(1);
            when(ticketCategory.isAccessRestricted()).thenReturn(false);
            List<String> expectedReservations = singletonList(reservationId);
            when(ticketReservationRepository.remove(eq(expectedReservations))).thenReturn(1);
            ticketReservationManager.releaseTicket(event, ticketReservation, ticket);
            verify(ticketRepository).releaseTicket(eq(reservationId), eq(eventId), eq(ticketId));
            verify(notificationManager).sendSimpleEmail(eq(event), eq(reservationEmail), any(), any(TextTemplateGenerator.class));
            verify(notificationManager).sendSimpleEmail(eq(event), eq(organizationEmail), any(), any(TextTemplateGenerator.class));
            verify(organizationRepository).getById(eq(organizationId));
            verify(ticketReservationRepository).remove(eq(expectedReservations));
        });

        it.should("not allow to release a ticket which belongs to a restricted category if there isn't any unbounded category", expect -> {
            when(ticketCategoryRepository.getById(eq(categoryId), eq(eventId))).thenReturn(ticketCategory);
            when(ticketCategoryRepository.countUnboundedCategoriesByEventId(eq(eventId))).thenReturn(0);
            when(ticketCategory.isAccessRestricted()).thenReturn(true);
            expect.exception(IllegalStateException.class, () -> ticketReservationManager.releaseTicket(event, ticketReservation, ticket));
            verify(ticketCategoryRepository).countUnboundedCategoriesByEventId(eq(eventId));
        });

        it.should("allow to release a ticket which belongs to a restricted category if there is at least one unbounded category", expect -> {
            when(ticketCategory.getId()).thenReturn(categoryId);
            when(ticketRepository.releaseTicket(eq(reservationId), eq(eventId), eq(ticketId))).thenReturn(1);
            when(ticketCategoryRepository.getById(eq(categoryId), eq(eventId))).thenReturn(ticketCategory);
            when(ticketCategory.isAccessRestricted()).thenReturn(true);
            when(ticketCategoryRepository.countUnboundedCategoriesByEventId(eq(eventId))).thenReturn(1);
            List<String> expectedReservations = singletonList(reservationId);
            when(ticketReservationRepository.remove(eq(expectedReservations))).thenReturn(1);
            ticketReservationManager.releaseTicket(event, ticketReservation, ticket);
            verify(ticketRepository).releaseTicket(eq(reservationId), eq(eventId), eq(ticketId));
            verify(ticketRepository).unbindTicketsFromCategory(eq(eventId), eq(categoryId), eq(singletonList(ticketId)));
            verify(notificationManager).sendSimpleEmail(eq(event), eq(reservationEmail), any(), any(TextTemplateGenerator.class));
            verify(organizationRepository).getById(eq(organizationId));
            verify(ticketReservationRepository).remove(eq(expectedReservations));
        });

        it.should("throw an exception in case of multiple tickets", expect -> {
            when(ticketRepository.releaseTicket(eq(reservationId), eq(eventId), eq(ticketId))).thenReturn(2);
            expect.exception(IllegalArgumentException.class, () -> ticketReservationManager.releaseTicket(event, ticketReservation, ticket));
            verify(ticketRepository).releaseTicket(eq(reservationId), eq(eventId), eq(ticketId));
            verify(notificationManager, never()).sendSimpleEmail(any(), any(), any(), any(TextTemplateGenerator.class));
        });

    });

    describe("confirm reservation", it -> {
        String gatewayToken = "token";
        String reservationId = "reservation-id";
        String transactionId = "transaction-id";
        TicketReservationRepository ticketReservationRepository = it.usesMock(TicketReservationRepository.class);
        SpecialPriceRepository specialPriceRepository = it.usesMock(SpecialPriceRepository.class);
        TicketRepository ticketRepository = it.usesMock(TicketRepository.class);
        WaitingQueueManager waitingQueueManager = it.usesMock(WaitingQueueManager.class);
        PlatformTransactionManager platformTransactionManager = mock(PlatformTransactionManager.class);
        PaymentManager paymentManager = it.usesMock(PaymentManager.class);
        ConfigurationManager configurationManager = it.usesMock(ConfigurationManager.class);
        NotificationManager notificationManager = it.usesMock(NotificationManager.class);
        MessageSource messageSource = it.usesMock(MessageSource.class);
        PluginManager pluginManager = mock(PluginManager.class);
        OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
        PromoCodeDiscountRepository promoCodeDiscountRepository = mock(PromoCodeDiscountRepository.class);
        EventRepository eventRepository = it.usesMock(EventRepository.class);
        FileUploadManager fileUploadManager = mock(FileUploadManager.class);
        TicketFieldRepository ticketFieldRepository = mock(TicketFieldRepository.class);
        AdditionalServiceItemRepository additionalServiceItemRepository = mock(AdditionalServiceItemRepository.class);
        when(additionalServiceItemRepository.findByReservationUuid(anyString())).thenReturn(emptyList());
        TicketReservationManager ticketReservationManager = new TicketReservationManager(eventRepository, organizationRepository, ticketRepository, ticketReservationRepository, null, null, configurationManager, paymentManager, promoCodeDiscountRepository, specialPriceRepository, null, notificationManager, messageSource, null, platformTransactionManager, waitingQueueManager, pluginManager, fileUploadManager, ticketFieldRepository, null, additionalServiceItemRepository, null);
        Event event = mock(Event.class);
        when(event.getZoneId()).thenReturn(ZoneId.systemDefault());
        when(event.getBegin()).thenReturn(ZonedDateTime.now().plusDays(5));


        it.should("confirm a paid reservation", expect -> {
            when(ticketReservationRepository.updateTicketReservation(eq(reservationId), eq(TicketReservationStatus.COMPLETE.toString()), anyString(), anyString(), anyString(), anyString(), any(), eq(PaymentProxy.STRIPE.toString()))).thenReturn(1);
            when(ticketRepository.updateTicketsStatusWithReservationId(eq(reservationId), eq(TicketStatus.ACQUIRED.toString()))).thenReturn(1);
            when(ticketReservationRepository.updateTicketReservation(eq(reservationId), eq(IN_PAYMENT.toString()), anyString(), anyString(), anyString(), anyString(), isNull(ZonedDateTime.class), eq(PaymentProxy.STRIPE.toString()))).thenReturn(1);
            when(paymentManager.processPayment(eq(reservationId), eq(gatewayToken), anyInt(), eq(event), anyString(), anyString(), anyString())).thenReturn(PaymentResult.successful(transactionId));
            PaymentResult result = ticketReservationManager.confirm(gatewayToken, null, event, reservationId, "", "", Locale.ENGLISH, "", new TicketReservationManager.TotalPrice(100, 0, 0, 0), Optional.empty(), Optional.of(PaymentProxy.STRIPE), false);
            expect.that(result.isSuccessful()).is(true);
            expect.that(result.getGatewayTransactionId()).is(Optional.of(transactionId));
            verify(ticketReservationRepository).updateTicketReservation(eq(reservationId), eq(TicketReservationStatus.IN_PAYMENT.toString()), anyString(), anyString(), anyString(), anyString(), any(), eq(PaymentProxy.STRIPE.toString()));
            verify(ticketReservationRepository).lockReservationForUpdate(eq(reservationId));
            verify(paymentManager).processPayment(eq(reservationId), eq(gatewayToken), anyInt(), eq(event), anyString(), anyString(), anyString());
            verify(ticketRepository).updateTicketsStatusWithReservationId(eq(reservationId), eq(TicketStatus.ACQUIRED.toString()));
            verify(specialPriceRepository).updateStatusForReservation(eq(singletonList(reservationId)), eq(SpecialPrice.Status.TAKEN.toString()));
            verify(ticketReservationRepository).updateTicketReservation(eq(reservationId), eq(TicketReservationStatus.COMPLETE.toString()), anyString(), anyString(), anyString(), anyString(), any(), eq(PaymentProxy.STRIPE.toString()));
            verify(waitingQueueManager).fireReservationConfirmed(eq(reservationId));
        });
        it.should("return failure code if payment was not successful", expect -> {
            when(ticketReservationRepository.updateTicketReservation(eq(reservationId), eq(IN_PAYMENT.toString()), anyString(), anyString(), anyString(), anyString(), isNull(ZonedDateTime.class), eq(PaymentProxy.STRIPE.toString()))).thenReturn(1);
            when(ticketReservationRepository.updateTicketStatus(eq(reservationId), eq(TicketReservationStatus.PENDING.toString()))).thenReturn(1);
            when(paymentManager.processPayment(eq(reservationId), eq(gatewayToken), anyInt(), eq(event), anyString(), anyString(), anyString())).thenReturn(PaymentResult.unsuccessful("error-code"));
            PaymentResult result = ticketReservationManager.confirm(gatewayToken, null, event, reservationId, "", "", Locale.ENGLISH, "", new TicketReservationManager.TotalPrice(100, 0, 0, 0), Optional.empty(), Optional.of(PaymentProxy.STRIPE), false);
            expect.that(result.isSuccessful()).is(false);
            expect.that(result.getGatewayTransactionId()).is(Optional.empty());
            expect.that(result.getErrorCode()).is(Optional.of("error-code"));
            verify(ticketReservationRepository).updateTicketReservation(eq(reservationId), eq(TicketReservationStatus.IN_PAYMENT.toString()), anyString(), anyString(), anyString(), anyString(), any(), eq(PaymentProxy.STRIPE.toString()));
            verify(ticketReservationRepository).lockReservationForUpdate(eq(reservationId));
            verify(paymentManager).processPayment(eq(reservationId), eq(gatewayToken), anyInt(), eq(event), anyString(), anyString(), anyString());
            verify(ticketReservationRepository).updateTicketStatus(eq(reservationId), eq(TicketReservationStatus.PENDING.toString()));
        });
        it.should("handle the ON_SITE payment method", expect -> {
            when(ticketRepository.updateTicketsStatusWithReservationId(eq(reservationId), eq(TicketStatus.TO_BE_PAID.toString()))).thenReturn(1);
            when(ticketReservationRepository.updateTicketReservation(eq(reservationId), eq(COMPLETE.toString()), anyString(), anyString(), anyString(), anyString(), any(ZonedDateTime.class), eq(PaymentProxy.ON_SITE.toString()))).thenReturn(1);
            when(paymentManager.processPayment(eq(reservationId), eq(gatewayToken), anyInt(), eq(event), anyString(), anyString(), anyString())).thenReturn(PaymentResult.unsuccessful("error-code"));
            PaymentResult result = ticketReservationManager.confirm(gatewayToken, null, event, reservationId, "", "", Locale.ENGLISH, "", new TicketReservationManager.TotalPrice(100, 0, 0, 0), Optional.empty(), Optional.of(PaymentProxy.ON_SITE), false);
            expect.that(result.isSuccessful()).is(true);
            expect.that(result.getGatewayTransactionId()).is(Optional.of(TicketReservationManager.NOT_YET_PAID_TRANSACTION_ID));
            verify(ticketReservationRepository).updateTicketReservation(eq(reservationId), eq(TicketReservationStatus.COMPLETE.toString()), anyString(), anyString(), anyString(), anyString(), any(), eq(PaymentProxy.ON_SITE.toString()));
            verify(ticketReservationRepository).lockReservationForUpdate(eq(reservationId));
            verify(ticketRepository).updateTicketsStatusWithReservationId(eq(reservationId), eq(TicketStatus.TO_BE_PAID.toString()));
            verify(specialPriceRepository).updateStatusForReservation(eq(singletonList(reservationId)), eq(SpecialPrice.Status.TAKEN.toString()));
            verify(waitingQueueManager).fireReservationConfirmed(eq(reservationId));
        });

        it.should("handle the OFFLINE payment method", expect -> {
            when(ticketReservationRepository.postponePayment(eq(reservationId), any(Date.class), anyString(), anyString(), anyString())).thenReturn(1);
            PaymentResult result = ticketReservationManager.confirm(gatewayToken, null, event, reservationId, "", "", Locale.ENGLISH, "", new TicketReservationManager.TotalPrice(100, 0, 0, 0), Optional.empty(), Optional.of(PaymentProxy.OFFLINE), false);
            expect.that(result.isSuccessful()).is(true);
            expect.that(result.getGatewayTransactionId()).is(Optional.of(TicketReservationManager.NOT_YET_PAID_TRANSACTION_ID));
            verify(waitingQueueManager, never()).fireReservationConfirmed(eq(reservationId));
        });

        it.should("confirm OFFLINE payments", expect -> {
            TicketReservation reservation = it.usesMock(TicketReservation.class);
            when(reservation.getConfirmationTimestamp()).thenReturn(ZonedDateTime.now());
            when(reservation.getId()).thenReturn(reservationId);
            when(reservation.getPaymentMethod()).thenReturn(PaymentProxy.OFFLINE);
            when(reservation.getStatus()).thenReturn(OFFLINE_PAYMENT);
            when(reservation.getUserLanguage()).thenReturn(Locale.ENGLISH.getLanguage());
            when(ticketReservationRepository.findReservationById(eq(reservationId))).thenReturn(reservation);
            when(ticketRepository.updateTicketsStatusWithReservationId(eq(reservationId), eq(TicketStatus.ACQUIRED.toString()))).thenReturn(1);
            when(ticketReservationRepository.updateTicketReservation(eq(reservationId), eq(COMPLETE.toString()), anyString(), anyString(), anyString(), anyString(), any(ZonedDateTime.class), eq(PaymentProxy.OFFLINE.toString()))).thenReturn(1);
            when(configurationManager.getStringConfigValue(any())).thenReturn(Optional.of("vatnr"));
            when(ticketRepository.findTicketsInReservation(eq(reservationId))).thenReturn(Collections.emptyList());
            when(eventRepository.findByReservationId(eq(reservationId))).thenReturn(event);

            ticketReservationManager.confirmOfflinePayment(event, reservationId);
            verify(ticketReservationRepository).lockReservationForUpdate(eq(reservationId));
            verify(ticketReservationRepository).confirmOfflinePayment(eq(reservationId), eq(COMPLETE.toString()), any(ZonedDateTime.class));
            verify(ticketRepository).updateTicketsStatusWithReservationId(eq(reservationId), eq(TicketStatus.ACQUIRED.toString()));
            verify(ticketReservationRepository).updateTicketReservation(eq(reservationId), eq(TicketReservationStatus.COMPLETE.toString()), anyString(), anyString(), anyString(), anyString(), any(), eq(PaymentProxy.OFFLINE.toString()));
            verify(waitingQueueManager).fireReservationConfirmed(eq(reservationId));
        });


        it.isConcludedWith(() -> verifyNoMoreInteractions(ticketReservationRepository, paymentManager, ticketRepository, specialPriceRepository, waitingQueueManager, configurationManager));
    });

    describe("URL Generation", it -> {
        Event event = mock(Event.class);
        String reservationId = "abcd";
        String shortName = "shortName";
        String ticketId = "ticketId";
        when(event.getShortName()).thenReturn(shortName);
        EventRepository eventRepository = mock(EventRepository.class);
        when(eventRepository.findByReservationId(reservationId)).thenReturn(event);
        TicketReservationRepository ticketReservationRepository = mock(TicketReservationRepository.class);
        TicketRepository ticketRepository = mock(TicketRepository.class);
        TicketReservation reservation = mock(TicketReservation.class);
        when(reservation.getUserLanguage()).thenReturn("en");
        when(ticketReservationRepository.findReservationById(reservationId)).thenReturn(reservation);
        Ticket ticket = mock(Ticket.class);
        when(ticketRepository.findByUUID(ticketId)).thenReturn(ticket);
        when(ticket.getUserLanguage()).thenReturn("it");
        ConfigurationManager configurationManager = mock(ConfigurationManager.class);
        String baseUrl = "http://my-website/";
        when(configurationManager.getRequiredValue(Configuration.getSystemConfiguration(ConfigurationKeys.BASE_URL))).thenReturn(baseUrl);
        FileUploadManager fileUploadManager = mock(FileUploadManager.class);
        TicketFieldRepository ticketFieldRepository = mock(TicketFieldRepository.class);
        TicketReservationManager trm = new TicketReservationManager(eventRepository, null, ticketRepository, ticketReservationRepository, null, null, configurationManager, null, null, null, null, null, null, null, null, null, null, fileUploadManager, ticketFieldRepository, null, null, null);
        it.should("generate the reservationUrl from reservationId", expect -> {
            expect.that(trm.reservationUrl(reservationId)).is(baseUrl + "event/" + shortName + "/reservation/" + reservationId + "?lang=en");
        });
        it.should("generate the reservationUrl from reservationId and event", expect -> {
            expect.that(trm.reservationUrl(reservationId, event)).is(baseUrl + "event/" + shortName + "/reservation/" + reservationId + "?lang=en");
        });
        it.should("generate the ticket URL", expect -> {
            expect.that(trm.ticketUrl(reservationId, event, ticketId)).is(baseUrl + "event/" + shortName + "/reservation/" + reservationId+"/ticketId?lang=it");
        });
        it.should("generate the ticket update URL", expect -> {
            expect.that(trm.ticketUpdateUrl(reservationId, event, "ticketId")).is(baseUrl + "event/" + shortName + "/reservation/" + reservationId + "/ticket/ticketId/update?lang=it");
        });
    });

    describe("sendReminderForOptionalInfo", it -> {
        int eventId = 1;
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
        PluginManager pluginManager = mock(PluginManager.class);
        FileUploadManager fileUploadManager = mock(FileUploadManager.class);
        TicketFieldRepository ticketFieldRepository = mock(TicketFieldRepository.class);
        TicketReservationManager trm = new TicketReservationManager(eventRepository, organizationRepository, ticketRepository, ticketReservationRepository, null, null, configurationManager, null, promoCodeDiscountRepository, null, null, notificationManager, messageSource, null, transactionManager, null, pluginManager, fileUploadManager, ticketFieldRepository, null, null, null);
        it.initializesWith(() -> reset(notificationManager, eventRepository, organizationRepository, ticketRepository, ticketReservationRepository, configurationManager, promoCodeDiscountRepository, messageSource, transactionManager));
        it.should("send the reminder if there weren't any notifications before", expect -> {
            Event event = it.usesMock(Event.class);
            when(event.getId()).thenReturn(eventId);
            when(configurationManager.getIntConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ASSIGNMENT_REMINDER_START)), anyInt())).thenReturn(10);
            when(configurationManager.getStringConfigValue(any())).thenReturn(Optional.empty());
            when(reservation.latestNotificationTimestamp(any())).thenReturn(Optional.empty());
            String reservationId = "abcd";
            when(reservation.getId()).thenReturn(reservationId);
            Ticket ticket = it.usesMock(Ticket.class);
            when(ticket.getTicketsReservationId()).thenReturn(reservationId);
            int ticketId = 2;
            when(ticket.getId()).thenReturn(ticketId);
            when(ticketRepository.findAllAssignedButNotYetNotified(eventId)).thenReturn(singletonList(ticket));
            when(ticketReservationRepository.findReservationById(eq(reservationId))).thenReturn(reservation);

            when(eventRepository.findByReservationId(reservationId)).thenReturn(event);
            when(event.getZoneId()).thenReturn(ZoneId.systemDefault());
            when(event.getBegin()).thenReturn(ZonedDateTime.now().plusDays(1));
            when(eventRepository.findAll()).thenReturn(singletonList(event));
            when(ticketRepository.findAllReservationsConfirmedButNotAssigned(anyInt())).thenReturn(singletonList(reservationId));
            when(ticketRepository.flagTicketAsReminderSent(ticketId)).thenReturn(1);
            when(ticketRepository.findByUUID(anyString())).thenReturn(ticket);
            trm.sendReminderForOptionalData();
            verify(notificationManager, times(1)).sendSimpleEmail(eq(event), anyString(), anyString(), any(TextTemplateGenerator.class));
        });

        it.should("not send the reminder if there was a notification before for the reservation", expect -> {
            Event event = it.usesMock(Event.class);
            when(event.getId()).thenReturn(eventId);
            when(configurationManager.getIntConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ASSIGNMENT_REMINDER_START)), anyInt())).thenReturn(10);
            when(configurationManager.getStringConfigValue(any())).thenReturn(Optional.empty());
            when(reservation.latestNotificationTimestamp(any())).thenReturn(Optional.of(ZonedDateTime.now().minusDays(10)));
            String reservationId = "abcd";
            when(reservation.getId()).thenReturn(reservationId);
            Ticket ticket = it.usesMock(Ticket.class);
            when(ticket.getTicketsReservationId()).thenReturn(reservationId);
            int ticketId = 2;
            when(ticket.getId()).thenReturn(ticketId);
            when(ticketRepository.findAllAssignedButNotYetNotified(eventId)).thenReturn(singletonList(ticket));
            when(ticketReservationRepository.findReservationById(eq(reservationId))).thenReturn(reservation);

            when(eventRepository.findByReservationId(reservationId)).thenReturn(event);
            when(event.getZoneId()).thenReturn(ZoneId.systemDefault());
            when(event.getBegin()).thenReturn(ZonedDateTime.now().plusDays(1));
            when(eventRepository.findAll()).thenReturn(singletonList(event));
            when(ticketRepository.flagTicketAsReminderSent(ticketId)).thenReturn(1);
            trm.sendReminderForOptionalData();
            verify(notificationManager, never()).sendSimpleEmail(eq(event), anyString(), anyString(), any(TextTemplateGenerator.class));
        });

        it.should("not send the reminder if there was a notification before", expect -> {
            Event event = it.usesMock(Event.class);
            when(event.getId()).thenReturn(eventId);
            when(configurationManager.getIntConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ASSIGNMENT_REMINDER_START)), anyInt())).thenReturn(10);
            when(configurationManager.getStringConfigValue(any())).thenReturn(Optional.empty());
            when(reservation.latestNotificationTimestamp(any())).thenReturn(Optional.empty());
            String reservationId = "abcd";
            when(reservation.getId()).thenReturn(reservationId);
            Ticket ticket = it.usesMock(Ticket.class);
            when(ticket.getTicketsReservationId()).thenReturn(reservationId);
            int ticketId = 2;
            when(ticket.getId()).thenReturn(ticketId);
            when(ticketRepository.findAllAssignedButNotYetNotified(eventId)).thenReturn(emptyList());
            when(ticketReservationRepository.findReservationById(eq(reservationId))).thenReturn(reservation);

            when(eventRepository.findByReservationId(reservationId)).thenReturn(event);
            when(event.getZoneId()).thenReturn(ZoneId.systemDefault());
            when(event.getBegin()).thenReturn(ZonedDateTime.now().plusDays(1));
            when(eventRepository.findAll()).thenReturn(singletonList(event));
            when(ticketRepository.flagTicketAsReminderSent(ticketId)).thenReturn(1);
            trm.sendReminderForOptionalData();
            verify(notificationManager, never()).sendSimpleEmail(eq(event), anyString(), anyString(), any(TextTemplateGenerator.class));
        });

        it.should("not send the reminder if the ticket was already modified", expect -> {
            Event event = it.usesMock(Event.class);
            when(event.getId()).thenReturn(eventId);
            when(configurationManager.getIntConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ASSIGNMENT_REMINDER_START)), anyInt())).thenReturn(10);
            when(configurationManager.getStringConfigValue(any())).thenReturn(Optional.empty());
            when(reservation.latestNotificationTimestamp(any())).thenReturn(Optional.empty());
            String reservationId = "abcd";
            when(reservation.getId()).thenReturn(reservationId);
            Ticket ticket = it.usesMock(Ticket.class);
            when(ticket.getTicketsReservationId()).thenReturn(reservationId);
            int ticketId = 2;
            when(ticket.getId()).thenReturn(ticketId);
            when(ticketRepository.findAllAssignedButNotYetNotified(eventId)).thenReturn(singletonList(ticket));
            when(ticketReservationRepository.findReservationById(eq(reservationId))).thenReturn(reservation);

            when(eventRepository.findByReservationId(reservationId)).thenReturn(event);
            when(event.getZoneId()).thenReturn(ZoneId.systemDefault());
            when(event.getBegin()).thenReturn(ZonedDateTime.now().plusDays(1));
            when(eventRepository.findAll()).thenReturn(singletonList(event));
            when(ticketRepository.flagTicketAsReminderSent(ticketId)).thenReturn(0);
            trm.sendReminderForOptionalData();
            verify(notificationManager, never()).sendSimpleEmail(eq(event), anyString(), anyString(), any(TextTemplateGenerator.class));
        });
    });
}}