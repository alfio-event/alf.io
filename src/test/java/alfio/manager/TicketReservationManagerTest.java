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
import alfio.repository.user.UserRepository;
import alfio.util.TemplateManager;
import alfio.util.WorkingDaysAdjusters;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.context.MessageSource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static alfio.model.TicketReservation.TicketReservationStatus.*;
import static alfio.model.system.ConfigurationKeys.ASSIGNMENT_REMINDER_START;
import static alfio.model.system.ConfigurationKeys.OFFLINE_PAYMENT_DAYS;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class TicketReservationManagerTest {

    private static final int EVENT_ID = 42;
    private static final int TICKET_CATEGORY_ID = 24;
    private static final String SPECIAL_PRICE_CODE = "SPECIAL-PRICE";
    private static final String SPECIAL_PRICE_SESSION_ID = "session-id";
    private static final int SPECIAL_PRICE_ID = -42;
    private static final String USER_LANGUAGE = "it";
    private static final int TICKET_ID = 2048756;
    private static final int ORGANIZATION_ID = 938249873;
    private static final String EVENT_NAME = "VoxxedDaysTicino";
    private static final String RESERVATION_ID = "what-a-reservation";
    private static final String RESERVATION_EMAIL = "me@mydomain.com";
    private static final String TRANSACTION_ID = "transaction-id";
    private static final String GATEWAY_TOKEN = "token";
    private static final String BASE_URL = "http://my-website/";
    private static final String ORG_EMAIL = "org@org.org";


    private TicketReservationManager trm;

    @Mock
    private NotificationManager notificationManager;
    @Mock
    private MessageSource messageSource;
    @Mock
    private TicketReservationRepository ticketReservationRepository;
    @Mock
    private TicketFieldRepository ticketFieldRepository;
    @Mock
    private ConfigurationManager configurationManager;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private TicketCategoryRepository ticketCategoryRepository;
    @Mock
    private TicketCategoryDescriptionRepository ticketCategoryDescriptionRepository;
    @Mock
    private PaymentManager paymentManager;
    @Mock
    private PromoCodeDiscountRepository promoCodeDiscountRepository;
    @Mock
    private SpecialPriceRepository specialPriceRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private TemplateManager templateManager;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private WaitingQueueManager waitingQueueManager;
    @Mock
    private AdditionalServiceRepository additionalServiceRepository;
    @Mock
    private AdditionalServiceTextRepository additionalServiceTextRepository;
    @Mock
    private AdditionalServiceItemRepository additionalServiceItemRepository;
    @Mock
    private InvoiceSequencesRepository invoiceSequencesRepository;
    @Mock
    private AuditingRepository auditingRepository;
    @Mock
    private TicketSearchRepository ticketSearchRepository;

    @Mock
    private Event event;
    @Mock
    private SpecialPrice specialPrice;
    @Mock
    private TicketCategory ticketCategory;
    @Mock
    private Ticket ticket;
    @Mock
    private TicketReservationWithOptionalCodeModification reservationModification;
    @Mock
    private TicketReservation ticketReservation;
    @Mock
    private Organization organization;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ExtensionManager extensionManager;

    @Before
    public void init() {
        trm = new TicketReservationManager(eventRepository,
            organizationRepository,
            ticketRepository,
            ticketReservationRepository,
            ticketCategoryRepository,
            ticketCategoryDescriptionRepository,
            configurationManager,
            paymentManager,
            promoCodeDiscountRepository,
            specialPriceRepository,
            transactionRepository,
            notificationManager,
            messageSource,
            templateManager,
            transactionManager,
            waitingQueueManager,
            ticketFieldRepository,
            additionalServiceRepository,
            additionalServiceItemRepository,
            additionalServiceTextRepository,
            invoiceSequencesRepository,
            auditingRepository,
            userRepository,
            extensionManager, ticketSearchRepository);

        when(event.getId()).thenReturn(EVENT_ID);
        when(event.getOrganizationId()).thenReturn(ORGANIZATION_ID);
        when(event.mustUseFirstAndLastName()).thenReturn(false);
        when(ticketCategoryRepository.getByIdAndActive(eq(TICKET_CATEGORY_ID), eq(EVENT_ID))).thenReturn(ticketCategory);
        when(specialPrice.getCode()).thenReturn(SPECIAL_PRICE_CODE);
        when(specialPrice.getId()).thenReturn(SPECIAL_PRICE_ID);
        when(eventRepository.findByReservationId(eq(RESERVATION_ID))).thenReturn(event);
        when(eventRepository.findAll()).thenReturn(Collections.singletonList(event));
        when(configurationManager.getRequiredValue(Configuration.from(event.getOrganizationId(), event.getId(), ConfigurationKeys.BASE_URL))).thenReturn(BASE_URL);
        when(configurationManager.hasAllConfigurationsForInvoice(eq(event))).thenReturn(false);
        when(ticketReservationRepository.findReservationById(RESERVATION_ID)).thenReturn(ticketReservation);
        when(ticket.getId()).thenReturn(TICKET_ID);
        when(ticket.getSrcPriceCts()).thenReturn(10);
        when(ticketCategory.getId()).thenReturn(TICKET_CATEGORY_ID);
        when(organizationRepository.getById(eq(ORGANIZATION_ID))).thenReturn(organization);
        when(event.getZoneId()).thenReturn(ZoneId.systemDefault());
        when(event.getBegin()).thenReturn(ZonedDateTime.now().plusDays(1));
        when(event.getVatStatus()).thenReturn(PriceContainer.VatStatus.NOT_INCLUDED);
        when(userRepository.findIdByUserName(anyString())).thenReturn(Optional.empty());
        when(extensionManager.handleInvoiceGeneration(any(), anyString(),
            anyString(), any(), any(), anyString(), anyString(), any(), anyBoolean(), anyString(),
            anyString(), any())).thenReturn(Optional.empty());
    }

    private void initUpdateTicketOwner(Ticket original, Ticket modified, String ticketId, String originalEmail, String originalName, UpdateTicketOwnerForm form) {
        when(original.getUuid()).thenReturn(ticketId);
        when(original.getEmail()).thenReturn(originalEmail);
        when(original.getFullName()).thenReturn(originalName);
        when(ticketRepository.findByUUID(eq(ticketId))).thenReturn(modified);
        form.setEmail("new@email.tld");
        form.setFullName(originalName);
        form.setUserLanguage(USER_LANGUAGE);
        when(organization.getEmail()).thenReturn(ORG_EMAIL);
    }

    @Test
    public void doNotSendWarningEmailIfAdmin() {
        final String ticketId = "abcde";
        final String ticketReservationId = "abcdef";
        final String originalEmail = "me@myaddress.com";
        final String originalName = "First Last";
        Ticket original = mock(Ticket.class);
        Ticket modified = mock(Ticket.class);
        UpdateTicketOwnerForm form = new UpdateTicketOwnerForm();
        when(event.getShortName()).thenReturn("short-name");
        initUpdateTicketOwner(original, modified, ticketId, originalEmail, originalName, form);
        TicketReservation reservation = mock(TicketReservation.class);
        when(original.getTicketsReservationId()).thenReturn(ticketReservationId);
        when(ticketReservationRepository.findOptionalReservationById(eq(ticketReservationId))).thenReturn(Optional.of(reservation));
        UserDetails userDetails = new User("user", "password", singletonList(new SimpleGrantedAuthority(Role.ADMIN.getRoleName())));
        trm.updateTicketOwner(original, Locale.ENGLISH, event, form, (a) -> null,(b) -> null, Optional.of(userDetails));
        verify(messageSource, never()).getMessage(eq("ticket-has-changed-owner-subject"), eq(new Object[] {"short-name"}), eq(Locale.ITALIAN));
    }

    @Test
    public void sendWarningEmailIfNotAdmin() {
        final String ticketId = "abcde";
        final String originalEmail = "me@myaddress.com";
        final String originalName = "First Last";
        Ticket original = mock(Ticket.class);
        when(original.getStatus()).thenReturn(TicketStatus.ACQUIRED);
        Ticket modified = mock(Ticket.class);
        UpdateTicketOwnerForm form = new UpdateTicketOwnerForm();
        when(event.getShortName()).thenReturn("short-name");
        initUpdateTicketOwner(original, modified, ticketId, originalEmail, originalName, form);
        PartialTicketTextGenerator ownerChangeTextBuilder = mock(PartialTicketTextGenerator.class);
        when(ownerChangeTextBuilder.generate(eq(modified))).thenReturn("Hello, world");
        when(original.getUserLanguage()).thenReturn(USER_LANGUAGE);
        trm.updateTicketOwner(original, Locale.ENGLISH, event, form, (a) -> null, ownerChangeTextBuilder, Optional.empty());
        verify(messageSource, times(1)).getMessage(eq("ticket-has-changed-owner-subject"), any(), eq(Locale.ITALIAN));
        verify(notificationManager, times(1)).sendSimpleEmail(eq(event), eq(originalEmail), anyString(), any(TextTemplateGenerator.class));
    }

    @Test
    public void sendWarningEmailToOrganizer() {
        final String ticketId = "abcde";
        final String originalEmail = "me@myaddress.com";
        final String originalName = "First Last";
        Ticket original = mock(Ticket.class);
        when(original.getStatus()).thenReturn(TicketStatus.ACQUIRED);
        Ticket modified = mock(Ticket.class);
        UpdateTicketOwnerForm form = new UpdateTicketOwnerForm();
        when(event.getShortName()).thenReturn("short-name");
        initUpdateTicketOwner(original, modified, ticketId, originalEmail, originalName, form);
        PartialTicketTextGenerator ownerChangeTextBuilder = mock(PartialTicketTextGenerator.class);
        when(ownerChangeTextBuilder.generate(eq(modified))).thenReturn("Hello, world");
        when(original.getUserLanguage()).thenReturn(USER_LANGUAGE);
        when(event.getBegin()).thenReturn(ZonedDateTime.now().minusSeconds(1));
        trm.updateTicketOwner(original, Locale.ENGLISH, event, form, (a) -> null, ownerChangeTextBuilder, Optional.empty());
        verify(messageSource, times(1)).getMessage(eq("ticket-has-changed-owner-subject"), any(), eq(Locale.ITALIAN));
        verify(notificationManager, times(1)).sendSimpleEmail(eq(event), eq(originalEmail), anyString(), any(TextTemplateGenerator.class));
        verify(notificationManager, times(1)).sendSimpleEmail(eq(event), eq(ORG_EMAIL), anyString(), any(TextTemplateGenerator.class));
    }

    // check we don't send the ticket-has-changed-owner email if the originalEmail and name are present and the status is not ACQUIRED
    @Test
    public void dontSendWarningEmailIfNotAcquiredStatus() {
        final String ticketId = "abcde";
        final String originalEmail = "me@myaddress.com";
        final String originalName = "First Last";
        Ticket original = mock(Ticket.class);
        when(original.getStatus()).thenReturn(TicketStatus.FREE);
        Ticket modified = mock(Ticket.class);
        UpdateTicketOwnerForm form = new UpdateTicketOwnerForm();
        when(event.getShortName()).thenReturn("short-name");
        initUpdateTicketOwner(original, modified, ticketId, originalEmail, originalName, form);
        PartialTicketTextGenerator ownerChangeTextBuilder = mock(PartialTicketTextGenerator.class);
        when(ownerChangeTextBuilder.generate(eq(modified))).thenReturn("Hello, world");
        when(original.getUserLanguage()).thenReturn(USER_LANGUAGE);
        trm.updateTicketOwner(original, Locale.ENGLISH, event, form, (a) -> null, ownerChangeTextBuilder, Optional.empty());
        verifyZeroInteractions(messageSource);
    }

    @Test
    public void fallbackToCurrentLocale() throws IOException {
        final String ticketId = "abcde";
        final String originalEmail = "me@myaddress.com";
        final String originalName = "First Last";
        Ticket original = mock(Ticket.class);
        when(original.getStatus()).thenReturn(TicketStatus.ACQUIRED);
        Ticket modified = mock(Ticket.class);
        when(modified.getStatus()).thenReturn(TicketStatus.ACQUIRED);
        UpdateTicketOwnerForm form = new UpdateTicketOwnerForm();
        when(event.getShortName()).thenReturn("short-name");
        initUpdateTicketOwner(original, modified, ticketId, originalEmail, originalName, form);
        form.setUserLanguage("");
        PartialTicketTextGenerator ownerChangeTextBuilder = mock(PartialTicketTextGenerator.class);
        when(ownerChangeTextBuilder.generate(eq(modified))).thenReturn("Hello, world");
        when(original.getUserLanguage()).thenReturn(USER_LANGUAGE);
        trm.updateTicketOwner(original, Locale.ENGLISH, event, form, (a) -> null, ownerChangeTextBuilder, Optional.empty());
        verify(messageSource, times(1)).getMessage(eq("ticket-has-changed-owner-subject"), any(), eq(Locale.ITALIAN));
        verify(notificationManager, times(1)).sendTicketByEmail(eq(modified), eq(event), eq(Locale.ENGLISH), any(), any(), any());
        verify(notificationManager, times(1)).sendSimpleEmail(eq(event), eq(originalEmail), anyString(), any(TextTemplateGenerator.class));
    }

    @Test
    public void sendAssignmentReminderBeforeEventEnd() {
        TicketReservation reservation = mock(TicketReservation.class);
        when(configurationManager.getIntConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ASSIGNMENT_REMINDER_START)), anyInt())).thenReturn(10);
        when(configurationManager.getStringConfigValue(any())).thenReturn(Optional.empty());
        when(reservation.latestNotificationTimestamp(any())).thenReturn(Optional.empty());
        when(reservation.getId()).thenReturn("abcd");
        when(reservation.getUserLanguage()).thenReturn("en");
        when(reservation.getValidity()).thenReturn(new Date());
        when(ticketReservationRepository.findReservationById(eq("abcd"))).thenReturn(reservation);
        when(ticketReservationRepository.findOptionalReservationById(eq("abcd"))).thenReturn(Optional.of(reservation));

        when(eventRepository.findByReservationId("abcd")).thenReturn(event);

        when(eventRepository.findAll()).thenReturn(singletonList(event));
        when(ticketRepository.findAllReservationsConfirmedButNotAssigned(anyInt())).thenReturn(singletonList("abcd"));
        trm.sendReminderForTicketAssignment();
        verify(notificationManager, times(1)).sendSimpleEmail(eq(event), anyString(), anyString(), any(TextTemplateGenerator.class));
    }

    @Test
    public void doNotSendAssignmentReminderAfterEventEnd() {
        TicketReservation reservation = mock(TicketReservation.class);
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
    }

    @Test
    public void considerZoneIdWhileChecking() {
        TicketReservation reservation = mock(TicketReservation.class);
        when(configurationManager.getIntConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ASSIGNMENT_REMINDER_START)), anyInt())).thenReturn(10);
        when(configurationManager.getStringConfigValue(any())).thenReturn(Optional.empty());
        when(reservation.latestNotificationTimestamp(any())).thenReturn(Optional.empty());
        when(reservation.getId()).thenReturn("abcd");
        when(reservation.getUserLanguage()).thenReturn("en");
        when(reservation.getValidity()).thenReturn(new Date());
        when(ticketReservationRepository.findReservationById(eq("abcd"))).thenReturn(reservation);
        when(ticketReservationRepository.findOptionalReservationById(eq("abcd"))).thenReturn(Optional.of(reservation));

        when(eventRepository.findByReservationId("abcd")).thenReturn(event);
        when(event.getZoneId()).thenReturn(ZoneId.of("GMT-4"));
        when(event.getBegin()).thenReturn(ZonedDateTime.now(ZoneId.of("GMT-4")).plusDays(1));
        when(eventRepository.findAll()).thenReturn(singletonList(event));
        when(ticketRepository.findAllReservationsConfirmedButNotAssigned(anyInt())).thenReturn(singletonList("abcd"));
        trm.sendReminderForTicketAssignment();
        verify(notificationManager, times(1)).sendSimpleEmail(eq(event), anyString(), anyString(), any(TextTemplateGenerator.class));
    }

    @Test
    public void considerZoneIdWhileCheckingExpired() {
        TicketReservation reservation = mock(TicketReservation.class);
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
    }

    @Test
    public void doNotSendReminderTooEarly() {
        TicketReservation reservation = mock(TicketReservation.class);
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
        assertEquals(0, events.size());
        verify(notificationManager, never()).sendSimpleEmail(eq(event), anyString(), anyString(), any(TextTemplateGenerator.class));
    }

    private void initOfflinePaymentTest() {
        when(configurationManager.getIntConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), OFFLINE_PAYMENT_DAYS)), anyInt())).thenReturn(2);
        when(event.getZoneId()).thenReturn(ZoneId.systemDefault());
    }

    @Test
    public void returnTheExpiredDateAsConfigured() {
        initOfflinePaymentTest();
        when(event.getBegin()).thenReturn(ZonedDateTime.now().plusDays(3));
        ZonedDateTime offlinePaymentDeadline = TicketReservationManager.getOfflinePaymentDeadline(event, configurationManager);
        assertEquals(LocalDateTime.now().plusDays(2).with(WorkingDaysAdjusters.workingDaysAtNoon()).truncatedTo(ChronoUnit.HOURS).toLocalDate(), offlinePaymentDeadline.toLocalDate());
    }

    @Test
    public void returnTheConfiguredWaitingTime() {
        initOfflinePaymentTest();
        when(event.getBegin()).thenReturn(ZonedDateTime.now().plusDays(3));
        assertEquals(2, TicketReservationManager.getOfflinePaymentWaitingPeriod(event, configurationManager));
    }

    @Test
    public void considerEventBeginDateWhileCalculatingExpDate() {
        initOfflinePaymentTest();
        when(event.getBegin()).thenReturn(ZonedDateTime.now().plusDays(1));
        ZonedDateTime offlinePaymentDeadline = TicketReservationManager.getOfflinePaymentDeadline(event, configurationManager);
        assertEquals(LocalDateTime.now().plusDays(1).with(WorkingDaysAdjusters.workingDaysAtNoon()).truncatedTo(ChronoUnit.HOURS).toLocalDate(), offlinePaymentDeadline.toLocalDate());
    }

    @Test
    public void returnConfiguredWaitingTimeConsideringEventStart() {
        initOfflinePaymentTest();
        when(event.getBegin()).thenReturn(ZonedDateTime.now().plusDays(1));
        assertEquals(1, TicketReservationManager.getOfflinePaymentWaitingPeriod(event, configurationManager));
    }

    @Test
    public void neverReturnADateInThePast() {
        initOfflinePaymentTest();
        when(event.getBegin()).thenReturn(ZonedDateTime.now());
        ZonedDateTime offlinePaymentDeadline = TicketReservationManager.getOfflinePaymentDeadline(event, configurationManager);
        assertTrue(offlinePaymentDeadline.isAfter(ZonedDateTime.now()));
    }

    @Test(expected = TicketReservationManager.OfflinePaymentException.class)
    public void throwExceptionAfterEventStart() {
        initOfflinePaymentTest();
        when(event.getBegin()).thenReturn(ZonedDateTime.now().minusDays(1));
        TicketReservationManager.getOfflinePaymentDeadline(event, configurationManager);
        fail();
    }

    //fix token
    @Test
    public void doNothingIfPrerequisitesAreNotSatisfied() {
        //do nothing if the category is not restricted
        assertFalse(trm.fixToken(Optional.empty(), TICKET_CATEGORY_ID, EVENT_ID, Optional.empty(), mock(TicketReservationWithOptionalCodeModification.class)).isPresent());
        //do nothing if special price status is pending and sessionId don't match
        assertFalse(trm.renewSpecialPrice(Optional.of(specialPrice), Optional.empty()).isPresent());
        //do nothing if special price status is pending and sessionId don't match
        when(specialPrice.getStatus()).thenReturn(SpecialPrice.Status.PENDING);
        when(specialPrice.getSessionIdentifier()).thenReturn("another-id");
        assertFalse(trm.renewSpecialPrice(Optional.of(specialPrice), Optional.of(SPECIAL_PRICE_SESSION_ID)).isPresent());
    }

    @Test
    public void renewSpecialPrice() {
        when(ticketCategory.isAccessRestricted()).thenReturn(true);
        when(specialPrice.getStatus()).thenReturn(SpecialPrice.Status.FREE);
        when(specialPriceRepository.getByCode(eq(SPECIAL_PRICE_CODE))).thenReturn(Optional.of(specialPrice));
        Optional<SpecialPrice> renewed = trm.renewSpecialPrice(Optional.of(specialPrice), Optional.of(SPECIAL_PRICE_SESSION_ID));
        verify(specialPriceRepository).bindToSession(eq(SPECIAL_PRICE_ID), eq(SPECIAL_PRICE_SESSION_ID));
        assertTrue(renewed.isPresent());
        assertSame(specialPrice, renewed.get());
    }

    @Test
    public void cancelPendingReservationAndRenewCode() {
        String RESERVATION_ID = "rid";
        when(ticketRepository.releaseExpiredTicket(eq(RESERVATION_ID), anyInt(), anyInt())).thenReturn(1);
        when(eventRepository.findByReservationId(eq(RESERVATION_ID))).thenReturn(event);
        when(ticketRepository.findTicketsInReservation(eq(RESERVATION_ID))).thenReturn(Collections.singletonList(ticket));
        when(ticket.getTicketsReservationId()).thenReturn(RESERVATION_ID);
        when(ticketRepository.findBySpecialPriceId(eq(SPECIAL_PRICE_ID))).thenReturn(Optional.of(ticket));
        TicketReservation reservation = mock(TicketReservation.class);
        when(ticketReservationRepository.findReservationById(eq(RESERVATION_ID))).thenReturn(reservation);
        when(reservation.getStatus()).thenReturn(TicketReservationStatus.PENDING);
        when(reservation.getId()).thenReturn(RESERVATION_ID);
        when(ticketRepository.freeFromReservation(eq(singletonList(RESERVATION_ID)))).thenReturn(1);
        when(ticketReservationRepository.remove(eq(singletonList(RESERVATION_ID)))).thenReturn(1);
        when(specialPriceRepository.getByCode(eq(SPECIAL_PRICE_CODE))).thenReturn(Optional.of(specialPrice));
        when(specialPrice.getStatus()).thenReturn(SpecialPrice.Status.PENDING);
        when(specialPrice.getSessionIdentifier()).thenReturn(SPECIAL_PRICE_SESSION_ID);
        Optional<SpecialPrice> renewed = trm.renewSpecialPrice(Optional.of(specialPrice), Optional.of(SPECIAL_PRICE_SESSION_ID));
        verify(specialPriceRepository).resetToFreeAndCleanupForReservation(eq(singletonList(RESERVATION_ID)));
        verify(ticketRepository).resetCategoryIdForUnboundedCategories(eq(singletonList(RESERVATION_ID)));
        verify(ticketRepository).releaseExpiredTicket(RESERVATION_ID, EVENT_ID, TICKET_ID);
        verify(ticketReservationRepository).remove(eq(singletonList(RESERVATION_ID)));
        verify(waitingQueueManager).fireReservationExpired(eq(RESERVATION_ID));
        assertTrue(renewed.isPresent());
        assertSame(specialPrice, renewed.get());
    }

    //reserve tickets for category


    @Test
    public void reserveTicketsForBoundedCategories() {
        when(ticketCategory.isBounded()).thenReturn(true);
        List<Integer> ids = singletonList(1);
        when(ticketRepository.selectTicketInCategoryForUpdate(eq(EVENT_ID), eq(TICKET_CATEGORY_ID), eq(1), eq(singletonList(Ticket.TicketStatus.FREE.name())))).thenReturn(ids);
        when(reservationModification.getAmount()).thenReturn(1);
        when(reservationModification.getTicketCategoryId()).thenReturn(TICKET_CATEGORY_ID);
        when(ticketRepository.findById(1, TICKET_CATEGORY_ID)).thenReturn(ticket);
        trm.reserveTicketsForCategory(event, Optional.empty(), "trid", reservationModification, Locale.ENGLISH, false, null);
        verify(ticketRepository).reserveTickets("trid", ids, TICKET_CATEGORY_ID, Locale.ENGLISH.getLanguage(), 0);
    }

    @Test
    public void reserveTicketsForBoundedCategoriesWaitingQueue() {
        when(ticketCategory.isBounded()).thenReturn(true);
        List<Integer> ids = singletonList(1);
        when(ticketRepository.selectTicketInCategoryForUpdate(eq(EVENT_ID), eq(TICKET_CATEGORY_ID), eq(1), eq(asList(TicketStatus.RELEASED.name(), TicketStatus.PRE_RESERVED.name())))).thenReturn(ids);
        when(reservationModification.getAmount()).thenReturn(1);
        when(reservationModification.getTicketCategoryId()).thenReturn(TICKET_CATEGORY_ID);
        when(ticketRepository.findById(1, TICKET_CATEGORY_ID)).thenReturn(ticket);
        trm.reserveTicketsForCategory(event, Optional.empty(), "trid", reservationModification, Locale.ENGLISH, true, null);
        verify(ticketRepository).reserveTickets("trid", ids, TICKET_CATEGORY_ID, Locale.ENGLISH.getLanguage(), 0);
    }

    @Test
    public void reserveTicketsForUnboundedCategories() {
        when(ticketCategory.isBounded()).thenReturn(false);
        List<Integer> ids = singletonList(1);
        when(ticketRepository.selectNotAllocatedTicketsForUpdate(eq(EVENT_ID), eq(1), eq(singletonList(Ticket.TicketStatus.FREE.name())))).thenReturn(ids);
        when(reservationModification.getAmount()).thenReturn(1);
        when(reservationModification.getTicketCategoryId()).thenReturn(TICKET_CATEGORY_ID);
        when(ticketRepository.findById(1, TICKET_CATEGORY_ID)).thenReturn(ticket);
        trm.reserveTicketsForCategory(event, Optional.empty(), "trid", reservationModification, Locale.ENGLISH, false, null);
        verify(ticketRepository).reserveTickets("trid", ids, TICKET_CATEGORY_ID, Locale.ENGLISH.getLanguage(), 0);
    }

    @Test
    public void reserveTicketsForUnboundedCategoriesWaitingQueue() {
        when(ticketCategory.isBounded()).thenReturn(false);
        List<Integer> ids = singletonList(1);
        when(ticketRepository.selectNotAllocatedTicketsForUpdate(eq(EVENT_ID), eq(1), eq(asList(TicketStatus.RELEASED.name(), TicketStatus.PRE_RESERVED.name())))).thenReturn(ids);
        when(reservationModification.getAmount()).thenReturn(1);
        when(reservationModification.getTicketCategoryId()).thenReturn(TICKET_CATEGORY_ID);
        when(ticketRepository.findById(1, TICKET_CATEGORY_ID)).thenReturn(ticket);
        trm.reserveTicketsForCategory(event, Optional.empty(), "trid", reservationModification, Locale.ENGLISH, true, null);
        verify(ticketRepository).reserveTickets("trid", ids, TICKET_CATEGORY_ID, Locale.ENGLISH.getLanguage(), 0);
    }

    //cleanup expired reservations

    @Test
    public void doNothingIfNoReservations() {
        Date now = new Date();
        when(ticketReservationRepository.findExpiredReservation(eq(now))).thenReturn(Collections.emptyList());
        trm.cleanupExpiredReservations(now);
        verify(ticketReservationRepository).findExpiredReservation(eq(now));
        verifyNoMoreInteractions(ticketReservationRepository, specialPriceRepository, ticketRepository, waitingQueueManager);
    }

    @Test
    public void cancelExpiredReservations() {
        Date now = new Date();
        List<String> reservationIds = singletonList("reservation-id");
        when(ticketReservationRepository.findExpiredReservation(eq(now))).thenReturn(reservationIds);
        trm.cleanupExpiredReservations(now);
        verify(ticketReservationRepository).findExpiredReservation(eq(now));
        verify(specialPriceRepository).resetToFreeAndCleanupForReservation(eq(reservationIds));
        verify(ticketRepository).resetCategoryIdForUnboundedCategories(eq(reservationIds));
        verify(ticketRepository).freeFromReservation(eq(reservationIds));
        verify(ticketReservationRepository).remove(eq(reservationIds));
        verify(waitingQueueManager).cleanExpiredReservations(eq(reservationIds));
        verify(ticketReservationRepository).getReservationIdAndEventId(eq(reservationIds));
        verifyNoMoreInteractions(ticketReservationRepository, specialPriceRepository, ticketRepository);
    }

    @Test
    public void countAvailableTickets() {
        //count how many tickets yet available for a category
        when(ticketCategory.isBounded()).thenReturn(true);
        trm.countAvailableTickets(event, ticketCategory);
        verify(ticketRepository).countFreeTickets(eq(EVENT_ID), eq(TICKET_CATEGORY_ID));
        //count how many tickets are available for unbounded categories
        when(ticketCategory.isBounded()).thenReturn(false);
        trm.countAvailableTickets(event, ticketCategory);
        verify(ticketRepository).countFreeTicketsForUnbounded(eq(EVENT_ID));
    }

    private void initReleaseTicket() {
        when(ticket.getId()).thenReturn(TICKET_ID);
        when(ticketCategoryDescriptionRepository.findByTicketCategoryIdAndLocale(anyInt(), anyString())).thenReturn(Optional.of("desc"));
        when(ticket.getEmail()).thenReturn(RESERVATION_EMAIL);
        when(ticket.getUserLanguage()).thenReturn(USER_LANGUAGE);
        when(ticket.getEventId()).thenReturn(EVENT_ID);
        when(ticket.getStatus()).thenReturn(Ticket.TicketStatus.ACQUIRED);
        when(event.getId()).thenReturn(EVENT_ID);
        when(event.getOrganizationId()).thenReturn(ORGANIZATION_ID);
        when(ticket.getCategoryId()).thenReturn(TICKET_CATEGORY_ID);
        when(ticketReservation.getId()).thenReturn(RESERVATION_ID);
        when(ticketCategoryRepository.getByIdAndActive(eq(TICKET_CATEGORY_ID), eq(EVENT_ID))).thenReturn(ticketCategory);
        when(organizationRepository.getById(eq(ORGANIZATION_ID))).thenReturn(organization);
        when(event.getShortName()).thenReturn(EVENT_NAME);
    }

    @Test
    public void sendEmailToAssigneeOnSuccess() {
        initReleaseTicket();
        String organizationEmail = "ciccio@test";
        when(organization.getEmail()).thenReturn(organizationEmail);
        when(ticketCategory.isAccessRestricted()).thenReturn(false);
        when(ticketRepository.releaseTicket(eq(RESERVATION_ID), anyString(), eq(EVENT_ID), eq(TICKET_ID))).thenReturn(1);
        when(ticketCategory.isAccessRestricted()).thenReturn(false);
        List<String> expectedReservations = singletonList(RESERVATION_ID);
        when(ticketReservationRepository.remove(eq(expectedReservations))).thenReturn(1);
        when(transactionRepository.loadOptionalByReservationId(anyString())).thenReturn(Optional.empty());
        trm.releaseTicket(event, ticketReservation, ticket);
        verify(ticketRepository).releaseTicket(eq(RESERVATION_ID), anyString(), eq(EVENT_ID), eq(TICKET_ID));
        verify(notificationManager).sendSimpleEmail(eq(event), eq(RESERVATION_EMAIL), any(), any(TextTemplateGenerator.class));
        verify(notificationManager).sendSimpleEmail(eq(event), eq(organizationEmail), any(), any(TextTemplateGenerator.class));
        verify(organizationRepository).getById(eq(ORGANIZATION_ID));
        verify(ticketReservationRepository).remove(eq(expectedReservations));
    }

    @Test(expected = IllegalStateException.class)
    public void cannotReleaseRestrictedTicketIfNoUnboundedCategory() {
        initReleaseTicket();
        when(ticketCategoryRepository.getByIdAndActive(eq(TICKET_CATEGORY_ID), eq(EVENT_ID))).thenReturn(ticketCategory);
        when(ticketCategoryRepository.countUnboundedCategoriesByEventId(eq(EVENT_ID))).thenReturn(0);
        when(ticketCategory.isAccessRestricted()).thenReturn(true);
        trm.releaseTicket(event, ticketReservation, ticket);
        verify(ticketCategoryRepository).countUnboundedCategoriesByEventId(eq(EVENT_ID));
    }

    @Test
    public void releaseRestrictedTicketIfUnboundedCategoryPresent() {
        initReleaseTicket();
        when(ticketCategory.getId()).thenReturn(TICKET_CATEGORY_ID);
        when(ticketRepository.releaseTicket(eq(RESERVATION_ID), anyString(), eq(EVENT_ID), eq(TICKET_ID))).thenReturn(1);
        when(ticketCategoryRepository.getByIdAndActive(eq(TICKET_CATEGORY_ID), eq(EVENT_ID))).thenReturn(ticketCategory);
        when(ticketCategory.isAccessRestricted()).thenReturn(true);
        when(ticketCategoryRepository.countUnboundedCategoriesByEventId(eq(EVENT_ID))).thenReturn(1);
        List<String> expectedReservations = singletonList(RESERVATION_ID);
        when(ticketReservationRepository.remove(eq(expectedReservations))).thenReturn(1);
        when(transactionRepository.loadOptionalByReservationId(anyString())).thenReturn(Optional.empty());
        trm.releaseTicket(event, ticketReservation, ticket);
        verify(ticketRepository).releaseTicket(eq(RESERVATION_ID), anyString(), eq(EVENT_ID), eq(TICKET_ID));
        verify(ticketRepository).unbindTicketsFromCategory(eq(EVENT_ID), eq(TICKET_CATEGORY_ID), eq(singletonList(TICKET_ID)));
        verify(notificationManager).sendSimpleEmail(eq(event), eq(RESERVATION_EMAIL), any(), any(TextTemplateGenerator.class));
        verify(organizationRepository).getById(eq(ORGANIZATION_ID));
        verify(ticketReservationRepository).remove(eq(expectedReservations));
    }

    @Test
    public void throwExceptionIfMultipleTickets() {
        initReleaseTicket();
        when(ticketRepository.releaseTicket(eq(RESERVATION_ID), anyString(), eq(EVENT_ID), eq(TICKET_ID))).thenReturn(2);
        try {
            trm.releaseTicket(event, ticketReservation, ticket);
            fail();
        } catch (IllegalArgumentException e) {
            verify(ticketRepository).releaseTicket(eq(RESERVATION_ID), anyString(), eq(EVENT_ID), eq(TICKET_ID));
            verify(notificationManager, never()).sendSimpleEmail(any(), any(), any(), any(TextTemplateGenerator.class));
        }
    }

    //confirm reservation

    private void initConfirmReservation() {
        when(event.getZoneId()).thenReturn(ZoneId.systemDefault());
        when(event.getBegin()).thenReturn(ZonedDateTime.now().plusDays(5));
    }

    @Test
    public void confirmPaidReservation() {
        initConfirmReservation();
        when(ticketReservationRepository.updateTicketReservation(eq(RESERVATION_ID), eq(TicketReservationStatus.COMPLETE.toString()), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any(), eq(PaymentProxy.STRIPE.toString()), anyString())).thenReturn(1);
        when(ticketRepository.updateTicketsStatusWithReservationId(eq(RESERVATION_ID), eq(TicketStatus.ACQUIRED.toString()))).thenReturn(1);
        when(ticketReservationRepository.updateTicketReservation(eq(RESERVATION_ID), eq(IN_PAYMENT.toString()), anyString(), anyString(), anyString(), anyString(),anyString(), anyString(), isNull(ZonedDateTime.class), eq(PaymentProxy.STRIPE.toString()), anyString())).thenReturn(1);
        when(paymentManager.processStripePayment(eq(RESERVATION_ID), eq(GATEWAY_TOKEN), anyInt(), eq(event), anyString(), any(CustomerName.class),  anyString())).thenReturn(PaymentResult.successful(TRANSACTION_ID));
        PaymentResult result = trm.confirm(GATEWAY_TOKEN, null, event, RESERVATION_ID, "", new CustomerName("Full Name", null, null, event), Locale.ENGLISH, "", "", new TotalPrice(100, 0, 0, 0), Optional.empty(), Optional.of(PaymentProxy.STRIPE), true, null, null, null, false, false);
        assertTrue(result.isSuccessful());
        assertEquals(Optional.of(TRANSACTION_ID), result.getGatewayTransactionId());
        verify(ticketReservationRepository).updateTicketReservation(eq(RESERVATION_ID), eq(TicketReservationStatus.IN_PAYMENT.toString()), anyString(), anyString(), anyString(),anyString(), anyString(), anyString(), any(), eq(PaymentProxy.STRIPE.toString()), anyString());
        verify(ticketReservationRepository).lockReservationForUpdate(eq(RESERVATION_ID));
        verify(paymentManager).processStripePayment(eq(RESERVATION_ID), eq(GATEWAY_TOKEN), anyInt(), eq(event), anyString(), any(CustomerName.class), anyString());
        verify(ticketRepository).updateTicketsStatusWithReservationId(eq(RESERVATION_ID), eq(TicketStatus.ACQUIRED.toString()));
        verify(specialPriceRepository).updateStatusForReservation(eq(singletonList(RESERVATION_ID)), eq(SpecialPrice.Status.TAKEN.toString()));
        verify(ticketReservationRepository).updateTicketReservation(eq(RESERVATION_ID), eq(TicketReservationStatus.COMPLETE.toString()), anyString(), anyString(),anyString(), anyString(), anyString(), anyString(), any(), eq(PaymentProxy.STRIPE.toString()), anyString());
        verify(waitingQueueManager).fireReservationConfirmed(eq(RESERVATION_ID));
        verify(ticketReservationRepository).findReservationById(RESERVATION_ID);
        verify(configurationManager).hasAllConfigurationsForInvoice(eq(event));
        verify(ticketReservationRepository).updateBillingData(any(), anyString(), anyString(), anyBoolean(), anyString());
        verify(ticketRepository, atLeastOnce()).findTicketsInReservation(anyString());
        verifyNoMoreInteractions(ticketReservationRepository, paymentManager, ticketRepository, specialPriceRepository, waitingQueueManager, configurationManager);
    }

    @Test
    public void returnFailureCodeIfPaymentNotSuccesful() {
        initConfirmReservation();
        when(ticketReservationRepository.updateTicketReservation(eq(RESERVATION_ID), eq(IN_PAYMENT.toString()), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), isNull(ZonedDateTime.class), eq(PaymentProxy.STRIPE.toString()), anyString())).thenReturn(1);
        when(ticketReservationRepository.updateReservationStatus(eq(RESERVATION_ID), eq(TicketReservationStatus.PENDING.toString()))).thenReturn(1);
        when(paymentManager.processStripePayment(eq(RESERVATION_ID), eq(GATEWAY_TOKEN), anyInt(), eq(event), anyString(), any(CustomerName.class), anyString())).thenReturn(PaymentResult.unsuccessful("error-code"));
        PaymentResult result = trm.confirm(GATEWAY_TOKEN, null, event, RESERVATION_ID, "", new CustomerName("Full Name", null, null, event), Locale.ENGLISH, "", "", new TotalPrice(100, 0, 0, 0), Optional.empty(), Optional.of(PaymentProxy.STRIPE), true, null, null, null, false, false);
        assertFalse(result.isSuccessful());
        assertFalse(result.getGatewayTransactionId().isPresent());
        assertEquals(Optional.of("error-code"), result.getErrorCode());
        verify(ticketReservationRepository).updateTicketReservation(eq(RESERVATION_ID), eq(TicketReservationStatus.IN_PAYMENT.toString()), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any(), eq(PaymentProxy.STRIPE.toString()), anyString());
        verify(ticketReservationRepository).lockReservationForUpdate(eq(RESERVATION_ID));
        verify(paymentManager).processStripePayment(eq(RESERVATION_ID), eq(GATEWAY_TOKEN), anyInt(), eq(event), anyString(), any(CustomerName.class), anyString());
        verify(ticketReservationRepository).updateReservationStatus(eq(RESERVATION_ID), eq(TicketReservationStatus.PENDING.toString()));
        verify(configurationManager).hasAllConfigurationsForInvoice(eq(event));
        verify(ticketReservationRepository).updateBillingData(any(), anyString(), anyString(), anyBoolean(), anyString());
        verifyNoMoreInteractions(ticketReservationRepository, paymentManager, ticketRepository, specialPriceRepository, waitingQueueManager, configurationManager);
    }

    @Test
    public void handleOnSitePaymentMethod() {
        initConfirmReservation();
        when(ticketRepository.updateTicketsStatusWithReservationId(eq(RESERVATION_ID), eq(TicketStatus.TO_BE_PAID.toString()))).thenReturn(1);
        when(ticketReservationRepository.updateTicketReservation(eq(RESERVATION_ID), eq(COMPLETE.toString()), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any(ZonedDateTime.class), eq(PaymentProxy.ON_SITE.toString()), anyString())).thenReturn(1);
        when(paymentManager.processStripePayment(eq(RESERVATION_ID), eq(GATEWAY_TOKEN), anyInt(), eq(event), anyString(), any(CustomerName.class), anyString())).thenReturn(PaymentResult.unsuccessful("error-code"));
        PaymentResult result = trm.confirm(GATEWAY_TOKEN, null, event, RESERVATION_ID, "", new CustomerName("Full Name", null, null, event), Locale.ENGLISH, "", "", new TotalPrice(100, 0, 0, 0), Optional.empty(), Optional.of(PaymentProxy.ON_SITE), true, null, null, null, false, false);
        assertTrue(result.isSuccessful());
        assertEquals(Optional.of(TicketReservationManager.NOT_YET_PAID_TRANSACTION_ID), result.getGatewayTransactionId());
        verify(ticketReservationRepository).updateTicketReservation(eq(RESERVATION_ID), eq(TicketReservationStatus.COMPLETE.toString()), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any(), eq(PaymentProxy.ON_SITE.toString()), anyString());
        verify(ticketReservationRepository).lockReservationForUpdate(eq(RESERVATION_ID));
        verify(ticketRepository).updateTicketsStatusWithReservationId(eq(RESERVATION_ID), eq(TicketStatus.TO_BE_PAID.toString()));
        verify(specialPriceRepository).updateStatusForReservation(eq(singletonList(RESERVATION_ID)), eq(SpecialPrice.Status.TAKEN.toString()));
        verify(waitingQueueManager).fireReservationConfirmed(eq(RESERVATION_ID));
        verify(ticketReservationRepository).findReservationById(RESERVATION_ID);
        verify(configurationManager).hasAllConfigurationsForInvoice(eq(event));
        verify(ticketReservationRepository).updateBillingData(any(), anyString(), anyString(), anyBoolean(), anyString());
        verify(ticketRepository, atLeastOnce()).findTicketsInReservation(anyString());
        verifyNoMoreInteractions(ticketReservationRepository, paymentManager, ticketRepository, specialPriceRepository, waitingQueueManager, configurationManager);
    }

    @Test
    public void handleOfflinePaymentMethod() {
        initConfirmReservation();
        when(ticketReservationRepository.postponePayment(eq(RESERVATION_ID), any(Date.class), anyString(), anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(1);
        PaymentResult result = trm.confirm(GATEWAY_TOKEN, null, event, RESERVATION_ID, "", new CustomerName("Full Name", null, null, event), Locale.ENGLISH, "", "", new TotalPrice(100, 0, 0, 0), Optional.empty(), Optional.of(PaymentProxy.OFFLINE), true, null, null, null, false, false);
        assertTrue(result.isSuccessful());
        assertEquals(Optional.of(TicketReservationManager.NOT_YET_PAID_TRANSACTION_ID), result.getGatewayTransactionId());
        verify(waitingQueueManager, never()).fireReservationConfirmed(eq(RESERVATION_ID));
        verify(ticketReservationRepository).lockReservationForUpdate(eq(RESERVATION_ID));
        verify(ticketReservationRepository).postponePayment(eq(RESERVATION_ID), any(Date.class), any(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(configurationManager).getIntConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), OFFLINE_PAYMENT_DAYS)), eq(5));
        verify(configurationManager).hasAllConfigurationsForInvoice(eq(event));
        verify(ticketReservationRepository).updateBillingData(any(), anyString(), anyString(), anyBoolean(), anyString());
        verifyNoMoreInteractions(ticketReservationRepository, paymentManager, ticketRepository, specialPriceRepository, waitingQueueManager, configurationManager);
    }

    @Test
    public void confirmOfflinePayments() {
        initConfirmReservation();
        TicketReservation reservation = mock(TicketReservation.class);
        when(reservation.getConfirmationTimestamp()).thenReturn(ZonedDateTime.now());
        when(reservation.getId()).thenReturn(RESERVATION_ID);
        when(reservation.getPaymentMethod()).thenReturn(PaymentProxy.OFFLINE);
        when(reservation.getStatus()).thenReturn(OFFLINE_PAYMENT);
        when(reservation.getUserLanguage()).thenReturn("en");
        when(reservation.getFullName()).thenReturn("Full Name");
        when(reservation.getValidity()).thenReturn(new Date());
        when(ticketReservationRepository.findOptionalReservationById(eq(RESERVATION_ID))).thenReturn(Optional.of(reservation));
        when(ticketRepository.updateTicketsStatusWithReservationId(eq(RESERVATION_ID), eq(TicketStatus.ACQUIRED.toString()))).thenReturn(1);
        when(ticketReservationRepository.updateTicketReservation(eq(RESERVATION_ID), eq(COMPLETE.toString()), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any(ZonedDateTime.class), eq(PaymentProxy.OFFLINE.toString()), anyString())).thenReturn(1);
        when(configurationManager.getStringConfigValue(any())).thenReturn(Optional.of("vatnr"));
        when(ticketRepository.findTicketsInReservation(eq(RESERVATION_ID))).thenReturn(Collections.emptyList());
        when(eventRepository.findByReservationId(eq(RESERVATION_ID))).thenReturn(event);

        trm.confirmOfflinePayment(event, RESERVATION_ID, "username");
        verify(ticketReservationRepository, atLeastOnce()).findOptionalReservationById(RESERVATION_ID);
        verify(ticketReservationRepository, atLeastOnce()).findReservationById(RESERVATION_ID);
        verify(ticketReservationRepository).lockReservationForUpdate(eq(RESERVATION_ID));
        verify(ticketReservationRepository).confirmOfflinePayment(eq(RESERVATION_ID), eq(COMPLETE.toString()), any(ZonedDateTime.class));
        verify(ticketRepository).updateTicketsStatusWithReservationId(eq(RESERVATION_ID), eq(TicketStatus.ACQUIRED.toString()));
        verify(ticketReservationRepository).updateTicketReservation(eq(RESERVATION_ID), eq(TicketReservationStatus.COMPLETE.toString()), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any(), eq(PaymentProxy.OFFLINE.toString()), anyString());
        verify(waitingQueueManager).fireReservationConfirmed(eq(RESERVATION_ID));
        verify(ticketRepository, atLeastOnce()).findTicketsInReservation(RESERVATION_ID);
        verify(specialPriceRepository).updateStatusForReservation(eq(singletonList(RESERVATION_ID)), eq(SpecialPrice.Status.TAKEN.toString()));
        verify(configurationManager, atLeastOnce()).getStringConfigValue(any());
        verify(configurationManager, atLeastOnce()).getRequiredValue(any());
        verify(configurationManager, atLeastOnce()).getShortReservationID(eq(event), eq(RESERVATION_ID));
        verify(ticketRepository).countTicketsInReservation(eq(RESERVATION_ID));
        verify(configurationManager).getBooleanConfigValue(any(), eq(false));
        verifyNoMoreInteractions(ticketReservationRepository, paymentManager, ticketRepository, specialPriceRepository, waitingQueueManager, configurationManager);
    }

    @Test
    public void reservationURLGeneration() {
        String shortName = "shortName";
        String ticketId = "ticketId";
        when(event.getShortName()).thenReturn(shortName);
        when(ticketReservation.getUserLanguage()).thenReturn("en");
        when(ticketReservationRepository.findReservationById(RESERVATION_ID)).thenReturn(ticketReservation);
        when(ticketRepository.findByUUID(ticketId)).thenReturn(ticket);
        when(ticket.getUserLanguage()).thenReturn(USER_LANGUAGE);
        //generate the reservationUrl from RESERVATION_ID
        assertEquals(BASE_URL + "event/" + shortName + "/reservation/" + RESERVATION_ID + "?lang=en", trm.reservationUrl(RESERVATION_ID));
        //generate the reservationUrl from RESERVATION_ID and event
        assertEquals(BASE_URL + "event/" + shortName + "/reservation/" + RESERVATION_ID + "?lang=en", trm.reservationUrl(RESERVATION_ID, event));
        //generate the ticket URL
        assertEquals(BASE_URL + "event/" + shortName + "/ticket/ticketId?lang=it", trm.ticketUrl(event, ticketId));
        //generate the ticket update URL
        assertEquals(BASE_URL + "event/" + shortName + "/ticket/ticketId/update?lang=it", trm.ticketUpdateUrl(event, "ticketId"));
    }

    //sendReminderForOptionalInfo
    private void initReminder() {
        when(ticketFieldRepository.countAdditionalFieldsForEvent(EVENT_ID)).thenReturn(1);
    }


    @Test
    public void sendReminderOnlyIfNoPreviousNotifications() {
        initReminder();
        when(event.getId()).thenReturn(EVENT_ID);
        when(configurationManager.getIntConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ASSIGNMENT_REMINDER_START)), anyInt())).thenReturn(10);
        when(configurationManager.getStringConfigValue(any())).thenReturn(Optional.empty());
        when(configurationManager.getBooleanConfigValue(any(), eq(true))).thenReturn(true);
        when(ticketReservation.latestNotificationTimestamp(any())).thenReturn(Optional.empty());
        when(ticketReservation.getId()).thenReturn(RESERVATION_ID);
        when(ticket.getTicketsReservationId()).thenReturn(RESERVATION_ID);
        int ticketId = 2;
        when(ticket.getId()).thenReturn(ticketId);
        when(ticketRepository.findAllAssignedButNotYetNotified(EVENT_ID)).thenReturn(singletonList(ticket));
        when(ticketReservationRepository.findOptionalReservationById(eq(RESERVATION_ID))).thenReturn(Optional.of(ticketReservation));

        when(eventRepository.findByReservationId(RESERVATION_ID)).thenReturn(event);
        when(event.getZoneId()).thenReturn(ZoneId.systemDefault());
        when(event.getBegin()).thenReturn(ZonedDateTime.now().plusDays(1));
        when(eventRepository.findAll()).thenReturn(singletonList(event));
        when(ticketRepository.findAllReservationsConfirmedButNotAssigned(anyInt())).thenReturn(singletonList(RESERVATION_ID));
        when(ticketRepository.flagTicketAsReminderSent(ticketId)).thenReturn(1);
        when(ticketRepository.findByUUID(anyString())).thenReturn(ticket);
        trm.sendReminderForOptionalData();
        verify(notificationManager, times(1)).sendSimpleEmail(eq(event), anyString(), anyString(), any(TextTemplateGenerator.class));
    }

    @Test
    public void doNotSendReminderIfPreviousNotifications() {
        initReminder();
        when(event.getId()).thenReturn(EVENT_ID);
        when(configurationManager.getIntConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ASSIGNMENT_REMINDER_START)), anyInt())).thenReturn(10);
        when(configurationManager.getStringConfigValue(any())).thenReturn(Optional.empty());
        when(ticketReservation.latestNotificationTimestamp(any())).thenReturn(Optional.of(ZonedDateTime.now().minusDays(10)));
        String RESERVATION_ID = "abcd";
        when(ticketReservation.getId()).thenReturn(RESERVATION_ID);
        when(ticket.getTicketsReservationId()).thenReturn(RESERVATION_ID);
        int ticketId = 2;
        when(ticket.getId()).thenReturn(ticketId);
        when(ticketRepository.findAllAssignedButNotYetNotified(EVENT_ID)).thenReturn(singletonList(ticket));
        when(ticketReservationRepository.findReservationById(eq(RESERVATION_ID))).thenReturn(ticketReservation);

        when(eventRepository.findByReservationId(RESERVATION_ID)).thenReturn(event);
        when(event.getZoneId()).thenReturn(ZoneId.systemDefault());
        when(event.getBegin()).thenReturn(ZonedDateTime.now().plusDays(1));
        when(eventRepository.findAll()).thenReturn(singletonList(event));
        when(ticketRepository.flagTicketAsReminderSent(ticketId)).thenReturn(1);
        trm.sendReminderForOptionalData();
        verify(notificationManager, never()).sendSimpleEmail(eq(event), anyString(), anyString(), any(TextTemplateGenerator.class));
    }

    @Test
    public void doNotSendReminderIfTicketHasAlreadyBeenModified() {
        initReminder();
        when(event.getId()).thenReturn(EVENT_ID);
        when(configurationManager.getIntConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ASSIGNMENT_REMINDER_START)), anyInt())).thenReturn(10);
        when(configurationManager.getStringConfigValue(any())).thenReturn(Optional.empty());
        when(ticketReservation.latestNotificationTimestamp(any())).thenReturn(Optional.empty());
        String RESERVATION_ID = "abcd";
        when(ticketReservation.getId()).thenReturn(RESERVATION_ID);
        when(ticket.getTicketsReservationId()).thenReturn(RESERVATION_ID);
        int ticketId = 2;
        when(ticket.getId()).thenReturn(ticketId);
        when(ticketRepository.findAllAssignedButNotYetNotified(EVENT_ID)).thenReturn(singletonList(ticket));
        when(ticketReservationRepository.findReservationById(eq(RESERVATION_ID))).thenReturn(ticketReservation);

        when(eventRepository.findByReservationId(RESERVATION_ID)).thenReturn(event);
        when(event.getZoneId()).thenReturn(ZoneId.systemDefault());
        when(event.getBegin()).thenReturn(ZonedDateTime.now().plusDays(1));
        when(eventRepository.findAll()).thenReturn(singletonList(event));
        when(ticketRepository.flagTicketAsReminderSent(ticketId)).thenReturn(0);
        trm.sendReminderForOptionalData();
        verify(notificationManager, never()).sendSimpleEmail(eq(event), anyString(), anyString(), any(TextTemplateGenerator.class));
    }
}