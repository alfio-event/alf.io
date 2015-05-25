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
import alfio.model.*;
import alfio.model.modification.TicketReservationWithOptionalCodeModification;
import alfio.repository.*;
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
import java.util.*;

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

    describe("fixToken", it -> {
        int ticketCategoryId = 0;
        int eventId = 0;
        TicketRepository ticketRepository = it.usesMock(TicketRepository.class);
        SpecialPriceRepository specialPriceRepository = it.usesMock(SpecialPriceRepository.class);
        TicketReservationRepository ticketReservationRepository = it.usesMock(TicketReservationRepository.class);
        TicketCategoryRepository ticketCategoryRepository = mock(TicketCategoryRepository.class);
        TicketCategory tc = it.usesMock(TicketCategory.class);
        TicketReservationManager ticketReservationManager = new TicketReservationManager(null, null, ticketRepository, ticketReservationRepository, ticketCategoryRepository, null, null, null, specialPriceRepository, null, null, null, null, null);
        String specialPriceCode = "SPECIAL-PRICE";
        String specialPriceSessionId = "session-id";
        int specialPriceId = -42;
        SpecialPrice specialPrice = mock(SpecialPrice.class);
        when(specialPrice.getCode()).thenReturn(specialPriceCode);
        when(specialPrice.getId()).thenReturn(specialPriceId);
        when(ticketCategoryRepository.getById(eq(ticketCategoryId), eq(eventId))).thenReturn(tc);
        it.should("do nothing if the category is not restricted", expect -> {
            when(tc.isAccessRestricted()).thenReturn(false);
            Optional<SpecialPrice> result = ticketReservationManager.fixToken(Optional.<SpecialPrice>empty(), ticketCategoryId, eventId, Optional.<String>empty(), null);
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
            when(reservation.getStatus()).thenReturn(TicketReservation.TicketReservationStatus.PENDING);
            when(reservation.getId()).thenReturn(reservationId);
            when(ticketRepository.freeFromReservation(eq(Collections.singletonList(reservationId)))).thenReturn(1);
            when(ticketReservationRepository.remove(eq(Collections.singletonList(reservationId)))).thenReturn(1);
            when(specialPriceRepository.getByCode(eq(specialPriceCode))).thenReturn(specialPrice);
            when(specialPrice.getStatus()).thenReturn(SpecialPrice.Status.PENDING);
            when(specialPrice.getSessionIdentifier()).thenReturn(specialPriceSessionId);
            Optional<SpecialPrice> renewed = ticketReservationManager.renewSpecialPrice(Optional.of(specialPrice), Optional.of(specialPriceSessionId));
            verify(specialPriceRepository).updateStatusForReservation(eq(Collections.singletonList(reservationId)), eq(SpecialPrice.Status.FREE.toString()));
            verify(ticketRepository).freeFromReservation(eq(Collections.singletonList(reservationId)));
            verify(ticketReservationRepository).remove(eq(Collections.singletonList(reservationId)));
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
        TicketCategory tc = it.usesMock(TicketCategory.class);
        TicketReservationManager ticketReservationManager = new TicketReservationManager(null, null, ticketRepository, ticketReservationRepository, ticketCategoryRepository, null, null, null, null, null, null, null, null, null);
        when(ticketCategoryRepository.getById(eq(ticketCategoryId), eq(eventId))).thenReturn(tc);
        TicketReservationWithOptionalCodeModification trm = it.usesMock(TicketReservationWithOptionalCodeModification.class);

        it.should("reserve tickets for bounded categories", expect -> {
            when(tc.isBounded()).thenReturn(true);
            List<Integer> ids = Collections.singletonList(1);
            when(ticketRepository.selectTicketInCategoryForUpdate(eq(eventId), eq(ticketCategoryId), eq(1))).thenReturn(ids);
            when(trm.getAmount()).thenReturn(1);
            when(trm.getTicketCategoryId()).thenReturn(ticketCategoryId);
            ticketReservationManager.reserveTicketsForCategory(eventId, Optional.<String>empty(), "trid", trm, Locale.ENGLISH);
            verify(ticketRepository).reserveTickets("trid", ids, ticketCategoryId, Locale.ENGLISH.getLanguage());
        });

        it.should("reserve tickets for unbounded categories", expect -> {
            when(tc.isBounded()).thenReturn(false);
            List<Integer> ids = Collections.singletonList(1);
            when(ticketRepository.selectNotAllocatedTicketsForUpdate(eq(eventId), eq(1))).thenReturn(ids);
            when(trm.getAmount()).thenReturn(1);
            when(trm.getTicketCategoryId()).thenReturn(ticketCategoryId);
            ticketReservationManager.reserveTicketsForCategory(eventId, Optional.<String>empty(), "trid", trm, Locale.ENGLISH);
            verify(ticketRepository).reserveTickets("trid", ids, ticketCategoryId, Locale.ENGLISH.getLanguage());
        });

    });

    describe("cleanupExpiredReservations", it -> {
        Date now = new Date();
        List<String> reservationIds = Collections.singletonList("reservation-id");
        TicketReservationRepository ticketReservationRepository = it.usesMock(TicketReservationRepository.class);
        SpecialPriceRepository specialPriceRepository = it.usesMock(SpecialPriceRepository.class);
        TicketRepository ticketRepository = it.usesMock(TicketRepository.class);
        TicketReservationManager ticketReservationManager = new TicketReservationManager(null, null, ticketRepository, ticketReservationRepository, null, null, null, null, specialPriceRepository, null, null, null, null, null);
        it.should("do nothing if there are no reservations", expect -> {
            when(ticketReservationRepository.findExpiredReservation(eq(now))).thenReturn(Collections.emptyList());
            ticketReservationManager.cleanupExpiredReservations(now);
            verify(ticketReservationRepository).findExpiredReservation(eq(now));
            verifyNoMoreInteractions(ticketReservationRepository, specialPriceRepository, ticketRepository);
        });
        it.should("cancel the expired reservations", expect -> {
            when(ticketReservationRepository.findExpiredReservation(eq(now))).thenReturn(reservationIds);
            ticketReservationManager.cleanupExpiredReservations(now);
            verify(ticketReservationRepository).findExpiredReservation(eq(now));
            verify(specialPriceRepository).updateStatusForReservation(eq(reservationIds), eq(SpecialPrice.Status.FREE.toString()));
            verify(ticketRepository).resetCategoryIdForUnboundedCategories(eq(reservationIds));
            verify(ticketRepository).freeFromReservation(eq(reservationIds));
            verify(ticketReservationRepository).remove(eq(reservationIds));
            verifyNoMoreInteractions(ticketReservationRepository, specialPriceRepository, ticketRepository);
        });
    });

    describe("countAvailableTickets", it -> {
        TicketRepository ticketRepository = it.usesMock(TicketRepository.class);
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(42);
        TicketCategory category = it.usesMock(TicketCategory.class);
        TicketReservationManager ticketReservationManager = new TicketReservationManager(null, null, ticketRepository, null, null, null, null, null, null, null, null, null, null, null);
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
        OrganizationRepository organizationRepository = it.usesMock(OrganizationRepository.class);
        TicketReservationRepository ticketReservationRepository = it.usesMock(TicketReservationRepository.class);
        TicketReservationManager ticketReservationManager = new TicketReservationManager(null, organizationRepository, ticketRepository, ticketReservationRepository, ticketCategoryRepository, null, null, null, null, null, notificationManager, messageSource, null, null);
        TicketReservation ticketReservation = mock(TicketReservation.class);
        Ticket ticket = mock(Ticket.class);
        Event event = mock(Event.class);
        int eventId = 42;
        int ticketId = 2048756;
        int categoryId = 657;
        int organizationId = 938249873;
        String reservationId = "what-a-reservation!!";
        when(ticket.getId()).thenReturn(ticketId);
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
        it.isSetupWith(() -> {
            when(ticketCategoryRepository.getById(eq(categoryId), eq(eventId))).thenReturn(ticketCategory);
        });

        when(event.getShortName()).thenReturn(eventName);
        it.should("send an e-mail to the assignee on success", expect -> {
            when(ticketCategory.isAccessRestricted()).thenReturn(false);
            when(ticketRepository.releaseTicket(eq(reservationId), eq(eventId), eq(ticketId))).thenReturn(1);
            when(ticketCategory.isAccessRestricted()).thenReturn(false);
            List<String> expectedReservations = Collections.singletonList(reservationId);
            when(ticketReservationRepository.remove(eq(expectedReservations))).thenReturn(1);
            ticketReservationManager.releaseTicket(event, ticketReservation, ticket);
            verify(ticketRepository).releaseTicket(eq(reservationId), eq(eventId), eq(ticketId));
            verify(notificationManager).sendSimpleEmail(eq(event), eq(reservationEmail), any(), any(TextTemplateGenerator.class));
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
            List<String> expectedReservations = Collections.singletonList(reservationId);
            when(ticketReservationRepository.remove(eq(expectedReservations))).thenReturn(1);
            ticketReservationManager.releaseTicket(event, ticketReservation, ticket);
            verify(ticketRepository).releaseTicket(eq(reservationId), eq(eventId), eq(ticketId));
            verify(ticketRepository).unbindTicketsFromCategory(eq(eventId), eq(categoryId), eq(Collections.singletonList(ticketId)));
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
}}