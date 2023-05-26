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
import alfio.manager.PaymentManager.PaymentMethodDTO;
import alfio.manager.PaymentManager.PaymentMethodDTO.PaymentMethodStatus;
import alfio.manager.i18n.MessageSourceManager;
import alfio.manager.payment.*;
import alfio.manager.support.*;
import alfio.manager.support.reservation.OrderSummaryGenerator;
import alfio.manager.support.reservation.ReservationCostCalculator;
import alfio.manager.support.reservation.ReservationEmailContentHelper;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.system.ConfigurationManager.MaybeConfiguration;
import alfio.manager.testSupport.MaybeConfigurationBuilder;
import alfio.manager.user.UserManager;
import alfio.model.*;
import alfio.model.Ticket.TicketStatus;
import alfio.model.TicketReservation.TicketReservationStatus;
import alfio.model.modification.TicketReservationWithOptionalCodeModification;
import alfio.model.system.ConfigurationKeyValuePathLevel;
import alfio.model.system.ConfigurationKeys;
import alfio.model.system.command.FinalizeReservation;
import alfio.model.transaction.PaymentContext;
import alfio.model.transaction.PaymentMethod;
import alfio.model.transaction.PaymentProxy;
import alfio.model.transaction.Transaction;
import alfio.model.transaction.capabilities.ServerInitiatedTransaction;
import alfio.model.transaction.capabilities.WebhookHandler;
import alfio.model.transaction.token.StripeCreditCardToken;
import alfio.model.user.Organization;
import alfio.model.user.Role;
import alfio.repository.*;
import alfio.repository.system.AdminJobQueueRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.test.util.TestUtil;
import alfio.util.*;
import ch.digitalfondue.npjt.AffectedRowCountAndKey;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static alfio.manager.TicketReservationManager.buildCompleteBillingAddress;
import static alfio.model.Audit.EventType.PAYMENT_CONFIRMED;
import static alfio.model.TicketReservation.TicketReservationStatus.*;
import static alfio.model.system.ConfigurationKeys.*;
import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TicketReservationManagerTest {

    private static final int EVENT_ID = 42;
    private static final int TICKET_CATEGORY_ID = 24;
    private static final String SPECIAL_PRICE_CODE = "SPECIAL-PRICE";
    private static final int SPECIAL_PRICE_ID = -42;
    private static final String USER_LANGUAGE = "it";
    private static final int TICKET_ID = 2048756;
    private static final int ORGANIZATION_ID = 938249873;
    private static final String EVENT_NAME = "VoxxedDaysTicino";
    private static final String RESERVATION_ID = "what-a-reservation";
    private static final String RESERVATION_EMAIL = "me@mydomain.com";
    private static final String TRANSACTION_ID = "transaction-id";
    private static final String GATEWAY_TOKEN = "token";
    private static final String BASE_URL = "https://my-website/";
    private static final String ORG_EMAIL = "org@org.org";
    private static final String EVENT_CURRENCY = "CHF";
    private static final String CATEGORY_CURRENCY = "EUR";


    private TicketReservationManager trm;

    private NotificationManager notificationManager;
    private MessageSource messageSource;
    private MessageSourceManager messageSourceManager;
    private TicketReservationRepository ticketReservationRepository;
    private TicketFieldRepository ticketFieldRepository;
    private ConfigurationManager configurationManager;
    private EventRepository eventRepository;
    private OrganizationRepository organizationRepository;
    private TicketRepository ticketRepository;
    private TicketCategoryRepository ticketCategoryRepository;
    private TicketCategoryDescriptionRepository ticketCategoryDescriptionRepository;
    private PaymentManager paymentManager;
    private SpecialPriceRepository specialPriceRepository;
    private TransactionRepository transactionRepository;
    private WaitingQueueManager waitingQueueManager;
    private Event event;
    private SpecialPrice specialPrice;
    private TicketCategory ticketCategory;
    private Ticket ticket;
    private TicketReservationWithOptionalCodeModification reservationModification;
    private TicketReservation ticketReservation;
    private Organization organization;
    private BillingDocumentRepository billingDocumentRepository;
    private NamedParameterJdbcTemplate jdbcTemplate;
    private Json json;
    private UserRepository userRepository;
    private AuditingRepository auditingRepository;
    private TotalPrice totalPrice;
    private PurchaseContextManager purchaseContextManager;

    private final Set<ConfigurationKeys> BANKING_KEY = Set.of(INVOICE_ADDRESS, BANK_ACCOUNT_NR, BANK_ACCOUNT_OWNER);
    private final Map<ConfigurationKeys, MaybeConfiguration> BANKING_INFO = Map.of(
        INVOICE_ADDRESS, new MaybeConfiguration(INVOICE_ADDRESS),
        BANK_ACCOUNT_NR, new MaybeConfiguration(BANK_ACCOUNT_NR),
        BANK_ACCOUNT_OWNER,  new MaybeConfiguration(BANK_ACCOUNT_OWNER));
    private PromoCodeDiscountRepository promoCodeDiscountRepository;
    private BillingDocumentManager billingDocumentManager;
    private ApplicationEventPublisher applicationEventPublisher;
    private ReservationEmailContentHelper reservationHelper;
    private ReservationFinalizer reservationFinalizer;
    private ReservationCostCalculator reservationCostCalculator;
    private ReservationMetadata metadata;

    @BeforeEach
    void init() {
        notificationManager = mock(NotificationManager.class);
        messageSource = mock(MessageSource.class);
        messageSourceManager = mock(MessageSourceManager.class);
        ticketReservationRepository = mock(TicketReservationRepository.class);
        ticketFieldRepository = mock(TicketFieldRepository.class);
        configurationManager = mock(ConfigurationManager.class);
        eventRepository = mock(EventRepository.class);
        organizationRepository = mock(OrganizationRepository.class);
        ticketRepository = mock(TicketRepository.class);
        ticketCategoryRepository = mock(TicketCategoryRepository.class);
        ticketCategoryDescriptionRepository = mock(TicketCategoryDescriptionRepository.class);
        paymentManager = mock(PaymentManager.class);
        promoCodeDiscountRepository = mock(PromoCodeDiscountRepository.class);
        specialPriceRepository = mock(SpecialPriceRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        TemplateManager templateManager = mock(TemplateManager.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        waitingQueueManager = mock(WaitingQueueManager.class);
        AdditionalServiceRepository additionalServiceRepository = mock(AdditionalServiceRepository.class);
        AdditionalServiceTextRepository additionalServiceTextRepository = mock(AdditionalServiceTextRepository.class);
        AdditionalServiceItemRepository additionalServiceItemRepository = mock(AdditionalServiceItemRepository.class);
        auditingRepository = mock(AuditingRepository.class);
        event = mock(Event.class);
        specialPrice = mock(SpecialPrice.class);
        ticketCategory = mock(TicketCategory.class);
        ticket = mock(Ticket.class);
        when(ticket.getCurrencyCode()).thenReturn("CHF");
        when(ticket.getVatStatus()).thenReturn(PriceContainer.VatStatus.NOT_INCLUDED);
        when(ticket.getCategoryId()).thenReturn(TICKET_CATEGORY_ID);
        jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        json = mock(Json.class);

        reservationModification = mock(TicketReservationWithOptionalCodeModification.class);
        ticketReservation = mock(TicketReservation.class);
        when(ticketReservation.getStatus()).thenReturn(PENDING);
        when(ticketReservation.getUserLanguage()).thenReturn("en");
        when(ticketReservation.getId()).thenReturn(RESERVATION_ID);
        when(ticketReservation.getSrcPriceCts()).thenReturn(100);
        when(ticketReservation.getFinalPriceCts()).thenReturn(100);
        when(ticketReservation.getVatCts()).thenReturn(0);
        when(ticketReservation.getDiscountCts()).thenReturn(0);
        when(ticketReservation.getCurrencyCode()).thenReturn(EVENT_CURRENCY);
        when(ticketReservationRepository.findReservationByIdForUpdate(RESERVATION_ID)).thenReturn(ticketReservation);
        when(ticketReservationRepository.findReservationById(RESERVATION_ID)).thenReturn(ticketReservation);
        when(ticketReservationRepository.getAdditionalInfo(any())).thenReturn(mock(TicketReservationAdditionalInfo.class));
        organization = new Organization(ORGANIZATION_ID, "org", "desc", ORG_EMAIL, null, null);
        TicketSearchRepository ticketSearchRepository = mock(TicketSearchRepository.class);
        GroupManager groupManager = mock(GroupManager.class);
        userRepository = mock(UserRepository.class);
        ExtensionManager extensionManager = mock(ExtensionManager.class);
        billingDocumentRepository = mock(BillingDocumentRepository.class);
        when(ticketCategoryRepository.getByIdAndActive(anyInt(), eq(EVENT_ID))).thenReturn(ticketCategory);
        when(ticketCategoryRepository.getByIdsAndActive(anyCollection(), eq(EVENT_ID))).thenReturn(List.of(ticketCategory));
        when(ticketCategory.getName()).thenReturn("Category Name");
        when(ticketCategory.getCurrencyCode()).thenReturn(CATEGORY_CURRENCY);
        when(configurationManager.getFor(eq(VAT_NR), any())).thenReturn(new MaybeConfiguration(VAT_NR));

        when(messageSourceManager.getMessageSourceFor(any())).thenReturn(messageSource);
        when(messageSourceManager.getRootMessageSource()).thenReturn(messageSource);

        MaybeConfiguration configuration = mock(MaybeConfiguration.class);
        when(configurationManager.getFor(eq(SEND_TICKETS_AUTOMATICALLY), any())).thenReturn(configuration);
        when(configuration.getValueAsBooleanOrDefault()).thenReturn(true);

        purchaseContextManager = mock(PurchaseContextManager.class);
        when(purchaseContextManager.findByReservationId(anyString())).thenReturn(Optional.of(event));

        billingDocumentManager = mock(BillingDocumentManager.class);
        applicationEventPublisher = mock(ApplicationEventPublisher.class);
        reservationHelper = mock(ReservationEmailContentHelper.class);
        reservationCostCalculator = mock(ReservationCostCalculator.class);
        var osm = mock(OrderSummaryGenerator.class);
        reservationFinalizer = new ReservationFinalizer(transactionManager,
            ticketReservationRepository, userRepository, extensionManager, auditingRepository, TestUtil.clockProvider(),
            configurationManager, null, ticketRepository, reservationHelper, specialPriceRepository,
            waitingQueueManager, ticketCategoryRepository, reservationCostCalculator, billingDocumentManager, additionalServiceItemRepository,
            osm, transactionRepository, mock(AdminJobQueueRepository.class), purchaseContextManager, mock(Json.class));
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
            messageSourceManager,
            templateManager,
            transactionManager,
            waitingQueueManager,
            ticketFieldRepository,
            additionalServiceRepository,
            additionalServiceItemRepository,
            additionalServiceTextRepository,
            auditingRepository,
            userRepository,
            extensionManager,
            ticketSearchRepository,
            groupManager,
            billingDocumentRepository,
            jdbcTemplate,
            json,
            billingDocumentManager,
            TestUtil.clockProvider(),
            purchaseContextManager,
            mock(SubscriptionRepository.class),
            mock(UserManager.class),
            applicationEventPublisher,
            reservationCostCalculator,
            reservationHelper,
            reservationFinalizer,
            osm);

        when(event.getId()).thenReturn(EVENT_ID);
        when(event.getOrganizationId()).thenReturn(ORGANIZATION_ID);
        when(event.mustUseFirstAndLastName()).thenReturn(false);
        when(event.getCurrency()).thenReturn(EVENT_CURRENCY);
        when(event.now(any(ClockProvider.class))).thenReturn(ZonedDateTime.now(ClockProvider.clock()));
        when(event.now(any(Clock.class))).thenReturn(ZonedDateTime.now(ClockProvider.clock()));
        when(event.event()).thenReturn(Optional.of(event));
        when(event.getType()).thenReturn(PurchaseContext.PurchaseContextType.event);
        when(event.ofType(eq(PurchaseContext.PurchaseContextType.event))).thenReturn(true);
        when(ticketCategoryRepository.getByIdAndActive(eq(TICKET_CATEGORY_ID), eq(EVENT_ID))).thenReturn(ticketCategory);
        when(specialPrice.getCode()).thenReturn(SPECIAL_PRICE_CODE);
        when(specialPrice.getId()).thenReturn(SPECIAL_PRICE_ID);
        when(eventRepository.findByReservationId(eq(RESERVATION_ID))).thenReturn(event);
        when(eventRepository.findAll()).thenReturn(Collections.singletonList(event));
        var baseUrlConf = new MaybeConfiguration(ConfigurationKeys.BASE_URL, new ConfigurationKeyValuePathLevel(null, BASE_URL, null));
        when(configurationManager.baseUrl(any())).thenReturn(StringUtils.removeEnd(BASE_URL, "/"));
        when(configurationManager.getForSystem(ConfigurationKeys.BASE_URL)).thenReturn(baseUrlConf);
        when(configurationManager.getFor(eq(ConfigurationKeys.BASE_URL), any())).thenReturn(baseUrlConf);
        when(configurationManager.hasAllConfigurationsForInvoice(eq(event))).thenReturn(false);
        when(ticketReservationRepository.findReservationByIdForUpdate(RESERVATION_ID)).thenReturn(ticketReservation);
        when(ticket.getId()).thenReturn(TICKET_ID);
        when(ticket.getSrcPriceCts()).thenReturn(10);
        when(ticketCategory.getId()).thenReturn(TICKET_CATEGORY_ID);
        when(organizationRepository.getById(eq(ORGANIZATION_ID))).thenReturn(organization);
        when(event.getZoneId()).thenReturn(ClockProvider.clock().getZone());
        when(event.getBegin()).thenReturn(ZonedDateTime.now(ClockProvider.clock()).plusDays(1));
        when(event.getVatStatus()).thenReturn(PriceContainer.VatStatus.NOT_INCLUDED);
        when(userRepository.findIdByUserName(anyString())).thenReturn(Optional.empty());
        when(extensionManager.handleInvoiceGeneration(any(), any(), any(), anyMap())).thenReturn(Optional.empty());
        when(messageSource.getMessage(eq("ticket-has-changed-owner-subject"), any(), any())).thenReturn("subject");
        when(messageSource.getMessage(eq("reminder.ticket-not-assigned.subject"), any(), any())).thenReturn("subject");
        when(billingDocumentRepository.insert(anyInt(), anyString(), anyString(), any(), anyString(), any(), anyInt())).thenReturn(new AffectedRowCountAndKey<>(1, 1L));
        totalPrice = mock(TotalPrice.class);
        metadata = mock(ReservationMetadata.class);
        when(ticketReservationRepository.getMetadata(RESERVATION_ID)).thenReturn(metadata);
        when(metadata.isFinalized()).thenReturn(true);
    }

    private void initUpdateTicketOwner(Ticket original, Ticket modified, String ticketId, String originalEmail, String originalName, UpdateTicketOwnerForm form) {
        when(original.getUuid()).thenReturn(ticketId);
        when(original.getEmail()).thenReturn(originalEmail);
        when(original.getFullName()).thenReturn(originalName);
        when(ticketRepository.findByUUID(eq(ticketId))).thenReturn(modified);
        form.setEmail("new@email.tld");
        form.setFullName(originalName);
        form.setUserLanguage(USER_LANGUAGE);
    }

    @Test
    void doNotSendWarningEmailIfAdmin() {
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
    void sendWarningEmailIfNotAdmin() {
        final String ticketId = "abcde";
        final String originalEmail = "me@myaddress.com";
        final String originalName = "First Last";
        Ticket original = mock(Ticket.class);
        when(original.getStatus()).thenReturn(TicketStatus.ACQUIRED);
        when(original.getTicketsReservationId()).thenReturn(RESERVATION_ID);
        Ticket modified = mock(Ticket.class);
        when(modified.getStatus()).thenReturn(TicketStatus.ACQUIRED);
        when(modified.getTicketsReservationId()).thenReturn(RESERVATION_ID);
        UpdateTicketOwnerForm form = new UpdateTicketOwnerForm();
        when(event.getShortName()).thenReturn("short-name");
        initUpdateTicketOwner(original, modified, ticketId, originalEmail, originalName, form);
        PartialTicketTextGenerator ownerChangeTextBuilder = mock(PartialTicketTextGenerator.class);
        when(ownerChangeTextBuilder.generate(eq(modified))).thenReturn(RenderedTemplate.multipart("Hello, world", "<p>Hello, world</p>", Map.of()));
        when(original.getUserLanguage()).thenReturn(USER_LANGUAGE);
        trm.updateTicketOwner(original, Locale.ENGLISH, event, form, (a) -> null, ownerChangeTextBuilder, Optional.empty());
        verify(messageSource, times(1)).getMessage(eq("ticket-has-changed-owner-subject"), any(), eq(Locale.ITALIAN));
        verify(notificationManager, times(1)).sendSimpleEmail(eq(event), eq(RESERVATION_ID), eq(originalEmail), anyString(), any(TemplateGenerator.class));
    }

    @Test
    void sendWarningEmailToOrganizer() {
        final String ticketId = "abcde";
        final String originalEmail = "me@myaddress.com";
        final String originalName = "First Last";
        Ticket original = mock(Ticket.class);
        when(original.getStatus()).thenReturn(TicketStatus.ACQUIRED);
        when(original.getTicketsReservationId()).thenReturn(RESERVATION_ID);
        Ticket modified = mock(Ticket.class);
        when(modified.getStatus()).thenReturn(TicketStatus.ACQUIRED);
        when(modified.getTicketsReservationId()).thenReturn(RESERVATION_ID);
        UpdateTicketOwnerForm form = new UpdateTicketOwnerForm();
        when(event.getShortName()).thenReturn("short-name");
        initUpdateTicketOwner(original, modified, ticketId, originalEmail, originalName, form);
        PartialTicketTextGenerator ownerChangeTextBuilder = mock(PartialTicketTextGenerator.class);
        when(ownerChangeTextBuilder.generate(eq(modified))).thenReturn(RenderedTemplate.multipart("Hello, world", "<p>Hello, world</p>", Map.of()));
        when(original.getUserLanguage()).thenReturn(USER_LANGUAGE);
        when(event.getBegin()).thenReturn(ZonedDateTime.now(ClockProvider.clock()).minusSeconds(1));
        trm.updateTicketOwner(original, Locale.ENGLISH, event, form, (a) -> null, ownerChangeTextBuilder, Optional.empty());
        verify(messageSource, times(1)).getMessage(eq("ticket-has-changed-owner-subject"), any(), eq(Locale.ITALIAN));
        verify(notificationManager, times(1)).sendSimpleEmail(eq(event), eq(RESERVATION_ID), eq(originalEmail), anyString(), any(TemplateGenerator.class));
        verify(notificationManager, times(1)).sendSimpleEmail(eq(event), eq(null), eq(ORG_EMAIL), anyString(), any(TemplateGenerator.class));
    }

    // check we don't send the ticket-has-changed-owner email if the originalEmail and name are present and the status is not ACQUIRED
    @Test
    void dontSendWarningEmailIfNotAcquiredStatus() {
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
        when(ownerChangeTextBuilder.generate(eq(modified))).thenReturn(RenderedTemplate.multipart("Hello, world", "<p>Hello, world</p>", Map.of()));
        when(original.getUserLanguage()).thenReturn(USER_LANGUAGE);
        trm.updateTicketOwner(original, Locale.ENGLISH, event, form, (a) -> null, ownerChangeTextBuilder, Optional.empty());
        verifyNoInteractions(messageSource);
    }

    @Test
    void fallbackToCurrentLocale() {
        final String ticketId = "abcde";
        final String originalEmail = "me@myaddress.com";
        final String originalName = "First Last";
        Ticket original = mock(Ticket.class);
        when(original.getTicketsReservationId()).thenReturn(RESERVATION_ID);
        when(original.getStatus()).thenReturn(TicketStatus.ACQUIRED);
        Ticket modified = mock(Ticket.class);
        when(modified.getStatus()).thenReturn(TicketStatus.ACQUIRED);
        when(modified.getTicketsReservationId()).thenReturn(RESERVATION_ID);
        UpdateTicketOwnerForm form = new UpdateTicketOwnerForm();
        when(event.getShortName()).thenReturn("short-name");
        initUpdateTicketOwner(original, modified, ticketId, originalEmail, originalName, form);
        form.setUserLanguage("");
        PartialTicketTextGenerator ownerChangeTextBuilder = mock(PartialTicketTextGenerator.class);
        when(ownerChangeTextBuilder.generate(eq(modified))).thenReturn(RenderedTemplate.multipart("Hello, world", "<p>Hello, world</p>", Map.of()));
        when(original.getUserLanguage()).thenReturn(USER_LANGUAGE);
        trm.updateTicketOwner(original, Locale.ENGLISH, event, form, (a) -> null, ownerChangeTextBuilder, Optional.empty());
        verify(messageSource, times(1)).getMessage(eq("ticket-has-changed-owner-subject"), any(), eq(Locale.ITALIAN));
        verify(reservationHelper).sendTicketByEmail(eq(modified), eq(Locale.ENGLISH), eq(event), any(PartialTicketTextGenerator.class));
        verify(notificationManager, times(1)).sendSimpleEmail(eq(event), eq(RESERVATION_ID), eq(originalEmail), anyString(), any(TemplateGenerator.class));
    }

    @Test
    void sendAssignmentReminderBeforeEventEnd() {
        TicketReservation reservation = mock(TicketReservation.class);
        when(configurationManager.getFor(eq(ASSIGNMENT_REMINDER_START), any())).thenReturn(new MaybeConfiguration(ASSIGNMENT_REMINDER_START));
        when(configurationManager.getFor(eq(ASSIGNMENT_REMINDER_INTERVAL), any())).thenReturn(new MaybeConfiguration(ASSIGNMENT_REMINDER_INTERVAL));
        //when(configurationManager.getForSystem(any())).thenReturn(Optional.empty());
        when(configurationManager.getFor(eq(BANKING_KEY), any())).thenReturn(BANKING_INFO);
        when(reservation.latestNotificationTimestamp(any())).thenReturn(Optional.empty());
        when(reservation.getId()).thenReturn("abcd");
        when(reservation.getUserLanguage()).thenReturn("en");
        when(reservation.getEmail()).thenReturn("ciccio");
        when(reservation.getValidity()).thenReturn(new Date(Instant.now(ClockProvider.clock()).getEpochSecond()));
        when(ticketReservationRepository.findReservationById(eq("abcd"))).thenReturn(reservation);
        when(ticketReservationRepository.findOptionalReservationById(eq("abcd"))).thenReturn(Optional.of(reservation));

        when(eventRepository.findByReservationId("abcd")).thenReturn(event);

        when(eventRepository.findAll()).thenReturn(singletonList(event));
        when(ticketRepository.findAllReservationsConfirmedButNotAssignedForUpdate(anyInt())).thenReturn(singleton("abcd"));
        trm.sendReminderForTicketAssignment();
        verify(notificationManager, times(1)).sendSimpleEmail(eq(event), eq("abcd"), eq("ciccio"), anyString(), any(TemplateGenerator.class));
    }

    @Test
    void doNotSendAssignmentReminderAfterEventEnd() {
        TicketReservation reservation = mock(TicketReservation.class);
        when(configurationManager.getFor(eq(ASSIGNMENT_REMINDER_START), any())).thenReturn(new MaybeConfiguration(ASSIGNMENT_REMINDER_START));
        //when(configurationManager.getStringConfigValue(any())).thenReturn(Optional.empty());
        when(reservation.latestNotificationTimestamp(any())).thenReturn(Optional.empty());
        when(reservation.getId()).thenReturn("abcd");
        when(ticketReservationRepository.findReservationById(eq("abcd"))).thenReturn(reservation);

        when(eventRepository.findByReservationId("abcd")).thenReturn(event);
        when(event.getZoneId()).thenReturn(ClockProvider.clock().getZone());
        when(event.getBegin()).thenReturn(ZonedDateTime.now(ClockProvider.clock()).minusDays(1));
        when(eventRepository.findAll()).thenReturn(singletonList(event));
        when(ticketRepository.findAllReservationsConfirmedButNotAssignedForUpdate(anyInt())).thenReturn(singleton("abcd"));
        trm.sendReminderForTicketAssignment();
        verify(notificationManager, never()).sendSimpleEmail(eq(event), anyString(), anyString(), anyString(), any(TemplateGenerator.class));
    }

    @Test
    void considerZoneIdWhileChecking() {
        TicketReservation reservation = mock(TicketReservation.class);
        when(configurationManager.getFor(eq(ASSIGNMENT_REMINDER_START), any())).thenReturn(new MaybeConfiguration(ASSIGNMENT_REMINDER_START));
        when(configurationManager.getFor(eq(ASSIGNMENT_REMINDER_INTERVAL), any())).thenReturn(new MaybeConfiguration(ASSIGNMENT_REMINDER_INTERVAL));
        //when(configurationManager.getStringConfigValue(any())).thenReturn(Optional.empty());
        when(reservation.latestNotificationTimestamp(any())).thenReturn(Optional.empty());
        when(reservation.getId()).thenReturn("abcd");
        when(reservation.getUserLanguage()).thenReturn("en");
        when(reservation.getValidity()).thenReturn(new Date(Instant.now(ClockProvider.clock()).getEpochSecond()));
        when(ticketReservationRepository.findReservationById(eq("abcd"))).thenReturn(reservation);
        when(ticketReservationRepository.findOptionalReservationById(eq("abcd"))).thenReturn(Optional.of(reservation));
        when(configurationManager.getFor(eq(BANKING_KEY), any())).thenReturn(BANKING_INFO);
        when(eventRepository.findByReservationId("abcd")).thenReturn(event);

        var zoneClock = Clock.offset(ClockProvider.clock(), Duration.ofHours(4).negated());
        when(event.getZoneId()).thenReturn(zoneClock.getZone());
        when(event.getBegin()).thenReturn(ZonedDateTime.now(zoneClock.getZone()).plusDays(1));
        when(eventRepository.findAll()).thenReturn(singletonList(event));
        when(ticketRepository.findAllReservationsConfirmedButNotAssignedForUpdate(anyInt())).thenReturn(singleton("abcd"));
        when(reservation.getEmail()).thenReturn("ciccio");
        trm.sendReminderForTicketAssignment();
        verify(notificationManager, times(1)).sendSimpleEmail(eq(event), eq("abcd"), anyString(), anyString(), any(TemplateGenerator.class));
    }

    @Test
    void considerZoneIdWhileCheckingExpired() {
        TicketReservation reservation = mock(TicketReservation.class);
        when(configurationManager.getFor(eq(ASSIGNMENT_REMINDER_START), any())).thenReturn(new MaybeConfiguration(ASSIGNMENT_REMINDER_START));
//        when(configurationManager.getStringConfigValue(any())).thenReturn(Optional.empty());
        when(reservation.latestNotificationTimestamp(any())).thenReturn(Optional.empty());
        when(reservation.getId()).thenReturn("abcd");
        when(ticketReservationRepository.findReservationById(eq("abcd"))).thenReturn(reservation);

        when(eventRepository.findByReservationId("abcd")).thenReturn(event);
        when(event.getZoneId()).thenReturn(ZoneId.of("UTC-8"));
        when(event.getBegin()).thenReturn(ZonedDateTime.now(ZoneId.of("UTC-8")));//same day
        when(eventRepository.findAll()).thenReturn(singletonList(event));
        when(ticketRepository.findAllReservationsConfirmedButNotAssignedForUpdate(anyInt())).thenReturn(singleton("abcd"));
        trm.sendReminderForTicketAssignment();
        verify(notificationManager, never()).sendSimpleEmail(eq(event), anyString(), anyString(), anyString(), any(TemplateGenerator.class));
    }

    @Test
    void doNotSendReminderTooEarly() {
        TicketReservation reservation = mock(TicketReservation.class);
        when(configurationManager.getFor(eq(ASSIGNMENT_REMINDER_START), any())).thenReturn(new MaybeConfiguration(ASSIGNMENT_REMINDER_START));
//        when(configurationManager.getStringConfigValue(any())).thenReturn(Optional.empty());
        when(reservation.latestNotificationTimestamp(any())).thenReturn(Optional.empty());
        when(reservation.getId()).thenReturn("abcd");
        when(ticketReservationRepository.findReservationById(eq("abcd"))).thenReturn(reservation);

        when(eventRepository.findByReservationId("abcd")).thenReturn(event);
        when(event.getZoneId()).thenReturn(ZoneId.of("UTC-8"));
        when(event.getBegin()).thenReturn(ZonedDateTime.now(ZoneId.of("UTC-8")).plusMonths(3).plusDays(1));
        when(eventRepository.findAll()).thenReturn(singletonList(event));
        when(ticketRepository.findAllReservationsConfirmedButNotAssignedForUpdate(anyInt())).thenReturn(singleton("abcd"));
        List<Event> events = trm.getNotifiableEventsStream().toList();
        Assertions.assertEquals(0, events.size());
        verify(notificationManager, never()).sendSimpleEmail(eq(event), anyString(), anyString(), anyString(), any(TemplateGenerator.class));
    }

    private void initOfflinePaymentTest() {
        when(configurationManager.getFor(eq(OFFLINE_PAYMENT_DAYS), any()))
            .thenReturn(new MaybeConfiguration(OFFLINE_PAYMENT_DAYS, new ConfigurationKeyValuePathLevel(OFFLINE_PAYMENT_DAYS.getValue(), "2", null)));
        when(event.getZoneId()).thenReturn(ClockProvider.clock().getZone());
    }

    @Test
    void returnTheExpiredDateAsConfigured() {
        initOfflinePaymentTest();
        when(event.getBegin()).thenReturn(ZonedDateTime.now(ClockProvider.clock()).plusDays(3));
        ZonedDateTime offlinePaymentDeadline = BankTransferManager.getOfflinePaymentDeadline(new PaymentContext(event), configurationManager);
        ZonedDateTime expectedDate = ZonedDateTime.now(ClockProvider.clock()).plusDays(2L).truncatedTo(ChronoUnit.HALF_DAYS).with(WorkingDaysAdjusters.defaultWorkingDays());
        Assertions.assertEquals(expectedDate, offlinePaymentDeadline);
    }

    @Test
    void returnTheConfiguredWaitingTime() {
        initOfflinePaymentTest();
        when(event.getBegin()).thenReturn(ZonedDateTime.now(ClockProvider.clock()).plusDays(3));
        OptionalInt offlinePaymentWaitingPeriod = BankTransferManager.getOfflinePaymentWaitingPeriod(new PaymentContext(event), configurationManager);
        Assertions.assertTrue(offlinePaymentWaitingPeriod.isPresent());
        Assertions.assertEquals(2, offlinePaymentWaitingPeriod.getAsInt());
    }

    @Test
    void considerEventBeginDateWhileCalculatingExpDate() {
        initOfflinePaymentTest();
        when(event.getBegin()).thenReturn(ZonedDateTime.now(ClockProvider.clock()).plusDays(1));
        ZonedDateTime offlinePaymentDeadline = BankTransferManager.getOfflinePaymentDeadline(new PaymentContext(event), configurationManager);

        long days = ChronoUnit.DAYS.between(LocalDate.now(ClockProvider.clock()), offlinePaymentDeadline.toLocalDate());
        Assertions.assertTrue(LocalDate.now(ClockProvider.clock()).getDayOfWeek() != DayOfWeek.FRIDAY || days == 3, "value must be 3 on Friday");
        Assertions.assertTrue(LocalDate.now(ClockProvider.clock()).getDayOfWeek() != DayOfWeek.SATURDAY || days == 2, "value must be 2 on Saturday");
        Assertions.assertTrue(!EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY).contains(LocalDate.now(ClockProvider.clock()).getDayOfWeek()) || days == 1, "value must be 1 on week days");
    }

    @Test
    void returnConfiguredWaitingTimeConsideringEventStart() {
        initOfflinePaymentTest();
        when(event.getBegin()).thenReturn(ZonedDateTime.now(ClockProvider.clock()).plusDays(1));
        OptionalInt offlinePaymentWaitingPeriod = BankTransferManager.getOfflinePaymentWaitingPeriod(new PaymentContext(event), configurationManager);
        Assertions.assertTrue(offlinePaymentWaitingPeriod.isPresent());
        Assertions.assertEquals(1, offlinePaymentWaitingPeriod.getAsInt());
    }

    @Test
    void neverReturnADateInThePast() {
        initOfflinePaymentTest();
        when(event.getBegin()).thenReturn(ZonedDateTime.now(ClockProvider.clock()));
        ZonedDateTime offlinePaymentDeadline = BankTransferManager.getOfflinePaymentDeadline(new PaymentContext(event), configurationManager);
        Assertions.assertTrue(offlinePaymentDeadline.isAfter(ZonedDateTime.now(ClockProvider.clock())));
    }

//    FIXME implement test
//    @Test
//    void throwExceptionAfterEventStart() {
//        initOfflinePaymentTest();
//        when(event.getBegin()).thenReturn(ZonedDateTime.now(ClockProvider.clock()).minusDays(1));
//        assertThrows(BankTransactionManager.OfflinePaymentException.class, () -> BankTransactionManager.getOfflinePaymentDeadline(event, configurationManager));
//    }

    //fix token

    @Test
    void reserveTicketsForCategoryWithAccessCode() {
        PromoCodeDiscount discount = mock(PromoCodeDiscount.class);
        when(discount.getCodeType()).thenReturn(PromoCodeDiscount.CodeType.ACCESS);
        when(reservationModification.getTicketCategoryId()).thenReturn(TICKET_CATEGORY_ID);
        when(reservationModification.getQuantity()).thenReturn(2);
        when(discount.getHiddenCategoryId()).thenReturn(TICKET_CATEGORY_ID);
        int accessCodeId = 666;
        when(discount.getId()).thenReturn(accessCodeId);
        when(ticketCategoryRepository.isAccessRestricted(eq(TICKET_CATEGORY_ID))).thenReturn(true);
        when(ticketReservation.getSrcPriceCts()).thenReturn(1000);
        when(ticket.getSrcPriceCts()).thenReturn(1000);
        when(promoCodeDiscountRepository.lockAccessCodeForUpdate(eq(accessCodeId))).thenReturn(accessCodeId);
        when(specialPriceRepository.bindToAccessCode(eq(TICKET_CATEGORY_ID), eq(accessCodeId), eq(2))).thenReturn(List.of(
            new SpecialPrice(1, "AAAA", 0, TICKET_CATEGORY_ID, SpecialPrice.Status.FREE.name(), null, null, null, accessCodeId),
            new SpecialPrice(2, "BBBB", 0, TICKET_CATEGORY_ID, SpecialPrice.Status.FREE.name(), null, null, null, accessCodeId)
        ));
        when(ticketRepository.selectNotAllocatedTicketsForUpdateSkipLocked(eq(EVENT_ID), eq(2), eq(List.of("FREE")))).thenReturn(List.of(TICKET_ID,2));
        when(ticketRepository.findById(eq(TICKET_ID), eq(TICKET_CATEGORY_ID))).thenReturn(ticket);
        String query = "batch-reserve-tickets";
        when(ticketRepository.batchReserveTicketsForSpecialPrice()).thenReturn(query);
        trm.reserveTicketsForCategory(event, RESERVATION_ID, reservationModification, Locale.ENGLISH, false, discount, null);
        verify(jdbcTemplate).batchUpdate(eq(query), any(SqlParameterSource[].class));
        verify(specialPriceRepository).batchUpdateStatus(eq(List.of(1,2)), eq(SpecialPrice.Status.PENDING), eq(accessCodeId));
    }

    //reserve tickets for category


    @Test
    void reserveTicketsForBoundedCategories() {
        when(ticketCategory.isBounded()).thenReturn(true);
        List<Integer> ids = singletonList(1);
        when(ticketRepository.selectTicketInCategoryForUpdateSkipLocked(eq(EVENT_ID), eq(TICKET_CATEGORY_ID), eq(1), eq(singletonList(Ticket.TicketStatus.FREE.name())))).thenReturn(ids);
        when(reservationModification.getQuantity()).thenReturn(1);
        when(reservationModification.getTicketCategoryId()).thenReturn(TICKET_CATEGORY_ID);
        when(ticketRepository.findById(1, TICKET_CATEGORY_ID)).thenReturn(ticket);
        when(ticketRepository.reserveTickets(eq("trid"), eq(ids), same(ticketCategory), eq(Locale.ENGLISH.getLanguage()), any(), any()))
            .thenReturn(1);
        trm.reserveTicketsForCategory(event, "trid", reservationModification, Locale.ENGLISH, false, null, null);
        verify(ticketRepository).reserveTickets(eq("trid"), eq(ids), eq(ticketCategory), eq(Locale.ENGLISH.getLanguage()), any(), any());
    }

    @Test
    void reserveTicketsForBoundedCategoriesWaitingQueue() {
        when(ticketCategory.isBounded()).thenReturn(true);
        List<Integer> ids = singletonList(1);
        when(ticketRepository.selectTicketInCategoryForUpdateSkipLocked(eq(EVENT_ID), eq(TICKET_CATEGORY_ID), eq(1), eq(asList(TicketStatus.RELEASED.name(), TicketStatus.PRE_RESERVED.name())))).thenReturn(ids);
        when(reservationModification.getQuantity()).thenReturn(1);
        when(reservationModification.getTicketCategoryId()).thenReturn(TICKET_CATEGORY_ID);
        when(ticketRepository.findById(1, TICKET_CATEGORY_ID)).thenReturn(ticket);
        when(ticketRepository.reserveTickets(eq("trid"), eq(ids), same(ticketCategory), eq(Locale.ENGLISH.getLanguage()), any(), any()))
            .thenReturn(1);
        trm.reserveTicketsForCategory(event, "trid", reservationModification, Locale.ENGLISH, true, null, null);
        verify(ticketRepository).reserveTickets(eq("trid"), eq(ids), eq(ticketCategory), eq(Locale.ENGLISH.getLanguage()), any(), any());
    }

    @Test
    void reserveTicketsForUnboundedCategories() {
        when(ticketCategory.isBounded()).thenReturn(false);
        List<Integer> ids = singletonList(1);
        when(ticketRepository.selectNotAllocatedTicketsForUpdateSkipLocked(eq(EVENT_ID), eq(1), eq(singletonList(Ticket.TicketStatus.FREE.name())))).thenReturn(ids);
        when(reservationModification.getQuantity()).thenReturn(1);
        when(reservationModification.getTicketCategoryId()).thenReturn(TICKET_CATEGORY_ID);
        when(ticketRepository.findById(1, TICKET_CATEGORY_ID)).thenReturn(ticket);
        when(ticketRepository.reserveTickets(eq("trid"), eq(ids), eq(ticketCategory), eq(Locale.ENGLISH.getLanguage()), any(), any()))
            .thenReturn(1);
        trm.reserveTicketsForCategory(event, "trid", reservationModification, Locale.ENGLISH, false, null, null);
        verify(ticketRepository).reserveTickets(eq("trid"), eq(ids), eq(ticketCategory), eq(Locale.ENGLISH.getLanguage()), any(), any());
    }

    @Test
    void reserveTicketsForUnboundedCategoriesWaitingQueue() {
        when(ticketCategory.isBounded()).thenReturn(false);
        List<Integer> ids = singletonList(1);
        when(ticketRepository.selectNotAllocatedTicketsForUpdateSkipLocked(eq(EVENT_ID), eq(1), eq(asList(TicketStatus.RELEASED.name(), TicketStatus.PRE_RESERVED.name())))).thenReturn(ids);
        when(reservationModification.getQuantity()).thenReturn(1);
        when(reservationModification.getTicketCategoryId()).thenReturn(TICKET_CATEGORY_ID);
        when(ticketRepository.findById(1, TICKET_CATEGORY_ID)).thenReturn(ticket);
        when(ticketRepository.reserveTickets(eq("trid"), eq(ids), same(ticketCategory), eq(Locale.ENGLISH.getLanguage()), any(), any()))
            .thenReturn(1);
        trm.reserveTicketsForCategory(event, "trid", reservationModification, Locale.ENGLISH, true, null, null);
        verify(ticketRepository).reserveTickets(eq("trid"), eq(ids), eq(ticketCategory), eq(Locale.ENGLISH.getLanguage()), any(), any());
    }

    //cleanup expired reservations

    @Test
    void doNothingIfNoReservations() {
        Date now = new Date(Instant.now(ClockProvider.clock()).getEpochSecond());
        when(ticketReservationRepository.findExpiredReservationForUpdate(eq(now))).thenReturn(Collections.emptyList());
        trm.cleanupExpiredReservations(now);
        verify(ticketReservationRepository).findExpiredReservationForUpdate(eq(now));
        verifyNoMoreInteractions(ticketReservationRepository, specialPriceRepository, ticketRepository, waitingQueueManager);
    }

    @Test
    void cancelExpiredReservations() {
        Date now = new Date(Instant.now(ClockProvider.clock()).getEpochSecond());
        List<String> reservationIds = singletonList("reservation-id");
        when(ticketReservationRepository.findExpiredReservationForUpdate(now)).thenReturn(reservationIds);
        trm.cleanupExpiredReservations(now);
        verify(ticketReservationRepository).findExpiredReservationForUpdate(now);
        verify(specialPriceRepository).resetToFreeAndCleanupForReservation(reservationIds);
        verify(ticketRepository).resetCategoryIdForUnboundedCategories(reservationIds);
        verify(ticketRepository).freeFromReservation(reservationIds);
        verify(ticketReservationRepository).remove(reservationIds);
        verify(waitingQueueManager).cleanExpiredReservations(reservationIds);
        verify(ticketReservationRepository).getReservationIdAndEventId(reservationIds);
        verify(ticketReservationRepository).findReservationsWithPendingTransaction(reservationIds);
        verifyNoMoreInteractions(ticketReservationRepository, specialPriceRepository, ticketRepository);
    }

    @Test
    void countAvailableTickets() {
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
        when(ticket.getFinalPriceCts()).thenReturn(0);
        var mockExistingConfig = mock(MaybeConfiguration.class);
        when(mockExistingConfig.getValueAsBooleanOrDefault()).thenReturn(true);
        when(configurationManager.getFor(eq(ALLOW_FREE_TICKETS_CANCELLATION), any())).thenReturn(mockExistingConfig);
        when(ticketReservation.getId()).thenReturn(RESERVATION_ID);
        when(ticketCategoryRepository.getByIdAndActive(eq(TICKET_CATEGORY_ID), eq(EVENT_ID))).thenReturn(ticketCategory);
        when(organizationRepository.getById(eq(ORGANIZATION_ID))).thenReturn(organization);
        when(event.getShortName()).thenReturn(EVENT_NAME);
    }

    @Test
    void sendEmailToAssigneeOnSuccess() {
        initReleaseTicket();
        when(ticketCategory.isAccessRestricted()).thenReturn(false);
        when(ticketRepository.releaseTicket(eq(RESERVATION_ID), anyString(), eq(EVENT_ID), eq(TICKET_ID))).thenReturn(1);
        when(ticketCategory.isAccessRestricted()).thenReturn(false);
        List<String> expectedReservations = singletonList(RESERVATION_ID);
        when(ticketReservationRepository.remove(eq(expectedReservations))).thenReturn(1);
        when(transactionRepository.loadOptionalByReservationId(anyString())).thenReturn(Optional.empty());
        trm.releaseTicket(event, ticketReservation, ticket);
        verify(ticketRepository).releaseTicket(eq(RESERVATION_ID), anyString(), eq(EVENT_ID), eq(TICKET_ID));
        verify(notificationManager).sendSimpleEmail(eq(event), eq(RESERVATION_ID), eq(RESERVATION_EMAIL), any(), any(TemplateGenerator.class));
        verify(notificationManager).sendSimpleEmail(eq(event), isNull(), eq(ORG_EMAIL), any(), any(TemplateGenerator.class));
        verify(organizationRepository).getById(eq(ORGANIZATION_ID));
        verify(ticketReservationRepository).remove(eq(expectedReservations));
    }

    @Test
    void cannotReleaseRestrictedTicketIfNoUnboundedCategory() {
        initReleaseTicket();
        when(ticketCategoryRepository.getByIdAndActive(eq(TICKET_CATEGORY_ID), eq(EVENT_ID))).thenReturn(ticketCategory);
        when(ticketCategoryRepository.countUnboundedCategoriesByEventId(eq(EVENT_ID))).thenReturn(0);
        when(ticketCategory.isAccessRestricted()).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> trm.releaseTicket(event, ticketReservation, ticket));
        verify(ticketCategoryRepository).countUnboundedCategoriesByEventId(eq(EVENT_ID));
    }

    @Test
    void releaseRestrictedTicketIfUnboundedCategoryPresent() {
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
        verify(notificationManager).sendSimpleEmail(eq(event), eq(RESERVATION_ID), eq(RESERVATION_EMAIL), any(), any(TemplateGenerator.class));
        verify(organizationRepository).getById(eq(ORGANIZATION_ID));
        verify(ticketReservationRepository).remove(eq(expectedReservations));
    }

    @Test
    void throwExceptionIfMultipleTickets() {
        initReleaseTicket();
        when(ticketRepository.releaseTicket(eq(RESERVATION_ID), anyString(), eq(EVENT_ID), eq(TICKET_ID))).thenReturn(2);
        try {
            trm.releaseTicket(event, ticketReservation, ticket);
            Assertions.fail();
        } catch (IllegalArgumentException e) {
            Assertions.assertEquals("Expected 1 row to be updated, got 2", e.getMessage());
            verify(ticketRepository).releaseTicket(eq(RESERVATION_ID), anyString(), eq(EVENT_ID), eq(TICKET_ID));
            verify(notificationManager, never()).sendSimpleEmail(any(), any(), any(), any(), any(TemplateGenerator.class));
        }
    }

    //performPayment reservation

    private void initConfirmReservation() {
        when(event.getZoneId()).thenReturn(ClockProvider.clock().getZone());
        when(event.getBegin()).thenReturn(ZonedDateTime.now(ClockProvider.clock()).plusDays(5));
    }

    @Test
    void confirmPaidReservation() {
        when(ticketReservationRepository.findOptionalStatusAndValidationById(eq(RESERVATION_ID))).thenReturn(Optional.of(new TicketReservationStatusAndValidation(PENDING, true)));
        when(configurationManager.getFor(eq(ENABLE_TICKET_TRANSFER), any())).thenReturn(
            new MaybeConfiguration(ENABLE_TICKET_TRANSFER)
        );
        when(configurationManager.getFor(eq(SEND_TICKETS_AUTOMATICALLY), any())).thenReturn(
            new MaybeConfiguration(SEND_TICKETS_AUTOMATICALLY)
        );
        when(configurationManager.getFor(eq(BANKING_KEY), any())).thenReturn(BANKING_INFO);
        mockBillingDocument();
        testPaidReservation(true, true);
        verify(notificationManager, never()).sendTicketByEmail(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void confirmPaidReservationButDoNotSendEmail() {
        when(ticketReservationRepository.findOptionalStatusAndValidationById(eq(RESERVATION_ID))).thenReturn(Optional.of(new TicketReservationStatusAndValidation(PENDING, true)));
        when(configurationManager.getFor(eq(ENABLE_TICKET_TRANSFER), any())).thenReturn(
            new MaybeConfiguration(ENABLE_TICKET_TRANSFER)
        );
        when(configurationManager.getFor(eq(SEND_TICKETS_AUTOMATICALLY), any())).thenReturn(
            new MaybeConfiguration(SEND_TICKETS_AUTOMATICALLY, new ConfigurationKeyValuePathLevel(null, "false", null))
        );
        when(configurationManager.getFor(eq(BANKING_KEY), any())).thenReturn(BANKING_INFO);
        mockBillingDocument();
        testPaidReservation(true, true);
        verify(notificationManager, never()).sendTicketByEmail(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void confirmAndLockTickets() {
        when(ticketReservationRepository.findOptionalStatusAndValidationById(eq(RESERVATION_ID))).thenReturn(Optional.of(new TicketReservationStatusAndValidation(PENDING, true)));
        when(configurationManager.getFor(eq(SEND_TICKETS_AUTOMATICALLY), any())).thenReturn(
            new MaybeConfiguration(SEND_TICKETS_AUTOMATICALLY)
        );
        when(configurationManager.getFor(eq(BANKING_KEY), any())).thenReturn(BANKING_INFO);
        when(ticketRepository.forbidReassignment(any())).thenReturn(1);
        mockBillingDocument();
        testPaidReservation(true, true);
    }

    @Test
    void doNotCompleteReservationIfAlreadyCompleted() {
        when(auditingRepository.countAuditsOfTypeForReservation(eq(RESERVATION_ID), eq(PAYMENT_CONFIRMED))).thenReturn(1);
        when(ticketReservationRepository.findOptionalStatusAndValidationById(eq(RESERVATION_ID))).thenReturn(Optional.of(new TicketReservationStatusAndValidation(COMPLETE, true)));
        when(configurationManager.getFor(eq(SEND_TICKETS_AUTOMATICALLY), any())).thenReturn(
            new MaybeConfiguration(SEND_TICKETS_AUTOMATICALLY)
        );
        when(configurationManager.getFor(eq(BANKING_KEY), any())).thenReturn(BANKING_INFO);
        when(ticketRepository.forbidReassignment(any())).thenReturn(1);
        mockBillingDocument();
        testPaidReservation(true, true, false);
    }

    private void mockBillingDocument() {
        BillingDocument document = new BillingDocument(42, 42, "", "42", BillingDocument.Type.INVOICE, "{}", ZonedDateTime.now(ClockProvider.clock()), BillingDocument.Status.VALID, "42");
        when(billingDocumentRepository.findLatestByReservationId(eq(RESERVATION_ID))).thenReturn(Optional.of(document));
    }

    @Test
    void lockFailed() {
        when(ticketRepository.forbidReassignment(any())).thenReturn(0);
        when(configurationManager.getFor(eq(SEND_TICKETS_AUTOMATICALLY), any())).thenReturn(
            new MaybeConfiguration(SEND_TICKETS_AUTOMATICALLY)
        );
        when(configurationManager.getFor(eq(BANKING_KEY), any())).thenReturn(BANKING_INFO);
        when(userRepository.nullSafeFindIdByUserName(anyString())).thenReturn(Optional.empty());
        when(json.asJsonString(any())).thenReturn("{}");
        when(billingDocumentRepository.insert(eq(event.getId()), anyString(), anyString(), any(BillingDocument.Type.class), anyString(), any(ZonedDateTime.class), anyInt()))
            .thenReturn(new AffectedRowCountAndKey<>(1, 1L));


        testPaidReservation(false, false);
    }

    private void testPaidReservation(boolean enableTicketTransfer, boolean expectSuccess, boolean expectCompleteReservation) {
        initConfirmReservation();
        when(configurationManager.getFor(eq(ENABLE_TICKET_TRANSFER), any())).thenReturn(
            new MaybeConfiguration(ENABLE_TICKET_TRANSFER, new ConfigurationKeyValuePathLevel(null, Boolean.toString(enableTicketTransfer), null))
        );
        when(ticketReservationRepository.updateTicketReservation(eq(RESERVATION_ID), eq(TicketReservationStatus.COMPLETE.toString()), anyString(), anyString(), isNull(), isNull(), anyString(), anyString(), any(), eq(PaymentProxy.STRIPE.toString()), isNull())).thenReturn(1);
        when(ticketRepository.updateTicketsStatusWithReservationId(eq(RESERVATION_ID), eq(TicketStatus.ACQUIRED.toString()))).thenReturn(1);
        when(ticketReservation.getPromoCodeDiscountId()).thenReturn(null);
        when(ticketReservationRepository.findOptionalReservationById(eq(RESERVATION_ID))).thenReturn(Optional.of(ticketReservation));
        when(ticketReservationRepository.updateTicketReservation(eq(RESERVATION_ID), eq(IN_PAYMENT.toString()), anyString(), anyString(), isNull(), isNull(), anyString(), anyString(), isNull(), eq(PaymentProxy.STRIPE.toString()), isNull())).thenReturn(1);
        when(ticketRepository.findTicketsInReservation(eq(RESERVATION_ID))).thenReturn(List.of(ticket));
        when(ticket.getFullName()).thenReturn("Giuseppe Garibaldi");
        when(ticket.getUserLanguage()).thenReturn("en");
        StripeCreditCardManager stripeCreditCardManager = mock(StripeCreditCardManager.class);
        when(stripeCreditCardManager.accept(eq(PaymentMethod.CREDIT_CARD), any(), any())).thenReturn(true);
        when(paymentManager.streamActiveProvidersByProxy(eq(PaymentProxy.STRIPE), any())).thenReturn(Stream.of(stripeCreditCardManager));
        when(stripeCreditCardManager.getTokenAndPay(any())).thenReturn(PaymentResult.successful(TRANSACTION_ID));
        PaymentSpecification spec = new PaymentSpecification(RESERVATION_ID, new StripeCreditCardToken(GATEWAY_TOKEN), 100, event, "test@email",
            new CustomerName("Full Name", null, null, event.mustUseFirstAndLastName()), "", null, Locale.ENGLISH,
            true, false, null, "IT", "123456", PriceContainer.VatStatus.INCLUDED, true, false);
        when(ticketReservation.getStatus()).thenReturn(IN_PAYMENT);
        when(configurationManager.getBlacklistedMethodsForReservation(eq(event), any())).thenReturn(List.of());
        PaymentResult result = trm.performPayment(spec, new TotalPrice(100, 0, 0, 0, "CHF"), PaymentProxy.STRIPE, PaymentMethod.CREDIT_CARD, null);
        if(expectSuccess) {
            Assertions.assertTrue(result.isSuccessful());
            Assertions.assertEquals(Optional.of(TRANSACTION_ID), result.getGatewayId());
            verify(ticketReservationRepository).findReservationByIdForUpdate(eq(RESERVATION_ID));
            verify(ticketReservationRepository, atLeastOnce()).findReservationById(RESERVATION_ID);
            verify(ticketReservationRepository).updateBillingData(eq(PriceContainer.VatStatus.INCLUDED), eq(100), eq(100), eq(0), eq(0), eq(EVENT_CURRENCY), eq("123456"), eq("IT"), eq(true), eq(RESERVATION_ID));

            verify(ticketRepository, expectCompleteReservation ? atLeastOnce() : times(1)).findTicketsInReservation(anyString());

            var verificationMode = expectCompleteReservation ? times(1) : never();
            verify(ticketReservationRepository, verificationMode).updateTicketReservation(eq(RESERVATION_ID), eq(TicketReservationStatus.IN_PAYMENT.toString()), anyString(), anyString(), isNull(), isNull(), anyString(), anyString(), any(), eq(PaymentProxy.STRIPE.toString()), isNull());
            verify(billingDocumentManager, never()).generateInvoiceNumber(eq(spec), any());
            verify(specialPriceRepository, verificationMode).updateStatusForReservation(singletonList(RESERVATION_ID), SpecialPrice.Status.TAKEN.toString());

            if (expectCompleteReservation) {
                verify(applicationEventPublisher).publishEvent(new FinalizeReservation(spec, PaymentProxy.STRIPE, true, true, null, PENDING));
            }
            verify(ticketRepository, never()).updateTicketsStatusWithReservationId(RESERVATION_ID, TicketStatus.ACQUIRED.toString());
            verify(ticketReservationRepository, never()).updateTicketReservation(eq(RESERVATION_ID), eq(TicketReservationStatus.COMPLETE.toString()), anyString(), anyString(), isNull(), isNull(), anyString(), anyString(), any(), eq(PaymentProxy.STRIPE.toString()), isNull());
            verify(waitingQueueManager, never()).fireReservationConfirmed(RESERVATION_ID);
            verify(ticketReservationRepository, never()).setInvoiceNumber(eq(RESERVATION_ID), any());
        } else {
            Assertions.assertFalse(result.isSuccessful());
            Assertions.assertTrue(result.isFailed());
        }
    }

    private void testPaidReservation(boolean enableTicketTransfer, boolean expectSuccess) {
        testPaidReservation(enableTicketTransfer, expectSuccess, true);
    }

    @Test
    void returnFailureCodeIfPaymentNotSuccessful() {
        initConfirmReservation();
        when(ticketReservationRepository.updateTicketReservation(eq(RESERVATION_ID), eq(IN_PAYMENT.toString()), anyString(), anyString(), isNull(), isNull(), anyString(), isNull(), isNull(), eq(PaymentProxy.STRIPE.toString()), isNull())).thenReturn(1);
        when(ticketReservationRepository.updateReservationStatus(eq(RESERVATION_ID), eq(TicketReservationStatus.PENDING.toString()))).thenReturn(1);
        when(ticketReservation.getPromoCodeDiscountId()).thenReturn(null);
        StripeCreditCardManager stripeCreditCardManager = mock(StripeCreditCardManager.class);
        when(paymentManager.streamActiveProvidersByProxy(eq(PaymentProxy.STRIPE), any())).thenReturn(Stream.of(stripeCreditCardManager));
        when(stripeCreditCardManager.getTokenAndPay(any())).thenReturn(PaymentResult.failed("error-code"));
        when(stripeCreditCardManager.accept(eq(PaymentMethod.CREDIT_CARD), any(), any())).thenReturn(true);
        when(ticketReservationRepository.findOptionalStatusAndValidationById(RESERVATION_ID)).thenReturn(Optional.of(new TicketReservationStatusAndValidation(PENDING, true)));
        PaymentSpecification spec = new PaymentSpecification(RESERVATION_ID, new StripeCreditCardToken(GATEWAY_TOKEN), 100, event, "email@user", new CustomerName("Full Name", null, null, event.mustUseFirstAndLastName()), null, null, Locale.ENGLISH, true, false, null, "IT", "12345", PriceContainer.VatStatus.INCLUDED, true, false);
        PaymentResult result = trm.performPayment(spec, new TotalPrice(100, 0, 0, 0, "CHF"), PaymentProxy.STRIPE, PaymentMethod.CREDIT_CARD, null);
        Assertions.assertFalse(result.isSuccessful());
        Assertions.assertFalse(result.getGatewayId().isPresent());
        Assertions.assertEquals(Optional.of("error-code"), result.getErrorCode());
        verify(ticketReservationRepository).updateTicketReservation(eq(RESERVATION_ID), eq(TicketReservationStatus.IN_PAYMENT.toString()), anyString(), anyString(), isNull(), isNull(), anyString(), isNull(), isNull(), eq(PaymentProxy.STRIPE.toString()), isNull());
        verify(ticketReservationRepository).findReservationByIdForUpdate(RESERVATION_ID);
        verify(ticketReservationRepository).updateReservationStatus(RESERVATION_ID, TicketReservationStatus.PENDING.toString());
        verify(configurationManager, never()).hasAllConfigurationsForInvoice(event);
        verify(ticketReservationRepository).updateBillingData(PriceContainer.VatStatus.INCLUDED, 100, 100, 0, 0, EVENT_CURRENCY, "12345", "IT", true, RESERVATION_ID);
    }

    @Test
    void handleOnSitePaymentMethod() {
        when(ticketReservationRepository.findOptionalStatusAndValidationById(eq(RESERVATION_ID))).thenReturn(Optional.of(new TicketReservationStatusAndValidation(PENDING, true)));
        initConfirmReservation();
        when(ticketRepository.updateTicketsStatusWithReservationId(eq(RESERVATION_ID), eq(TicketStatus.TO_BE_PAID.toString()))).thenReturn(1);
        when(ticketReservationRepository.updateTicketReservation(eq(RESERVATION_ID), eq(COMPLETE.toString()), anyString(), anyString(), isNull(), isNull(), anyString(), anyString(), any(ZonedDateTime.class), eq(PaymentProxy.ON_SITE.toString()), isNull())).thenReturn(1);
        when(ticketReservationRepository.findOptionalReservationById(eq(RESERVATION_ID))).thenReturn(Optional.of(ticketReservation));
        when(configurationManager.getFor(eq(ENABLE_TICKET_TRANSFER), any())).thenReturn(
            new MaybeConfiguration(ENABLE_TICKET_TRANSFER)
        );
        when(configurationManager.getFor(eq(BANKING_KEY), any())).thenReturn(BANKING_INFO);
        OnSiteManager onSiteManager = mock(OnSiteManager.class);
        when(onSiteManager.accept(eq(PaymentMethod.ON_SITE), any(), any())).thenReturn(true);
        when(paymentManager.streamActiveProvidersByProxy(eq(PaymentProxy.ON_SITE), any())).thenReturn(Stream.of(onSiteManager));
        when(ticketReservation.getPromoCodeDiscountId()).thenReturn(null);
        when(onSiteManager.getTokenAndPay(any())).thenReturn(PaymentResult.successful(TicketReservationManager.NOT_YET_PAID_TRANSACTION_ID));
        PaymentSpecification spec = new PaymentSpecification(RESERVATION_ID, new StripeCreditCardToken(GATEWAY_TOKEN), 100, event, "test@email",
            new CustomerName("Full Name", null, null, event.mustUseFirstAndLastName()),
            "", null, Locale.ENGLISH, true, false, null, "IT", "123456", PriceContainer.VatStatus.INCLUDED, true, false);
        when(ticketReservationRepository.updateTicketReservation(eq(RESERVATION_ID), anyString(), anyString(), anyString(), isNull(), isNull(), eq(Locale.ENGLISH.getLanguage()), isNull(), any(), any(), isNull())).thenReturn(1);
        PaymentResult result = trm.performPayment(spec, new TotalPrice(100, 0, 0, 0, "CHF"), PaymentProxy.ON_SITE, PaymentMethod.ON_SITE, null);
        Assertions.assertTrue(result.isSuccessful());
        Assertions.assertEquals(Optional.of(TicketReservationManager.NOT_YET_PAID_TRANSACTION_ID), result.getGatewayId());
        verify(specialPriceRepository).updateStatusForReservation(singletonList(RESERVATION_ID), SpecialPrice.Status.TAKEN.toString());
        verify(applicationEventPublisher).publishEvent(new FinalizeReservation(spec, PaymentProxy.ON_SITE, true, true, null, PENDING));
        verify(ticketRepository, never()).updateTicketsStatusWithReservationId(RESERVATION_ID, TicketStatus.ACQUIRED.toString());
        verify(ticketReservationRepository, never()).updateTicketReservation(eq(RESERVATION_ID), eq(TicketReservationStatus.COMPLETE.toString()), anyString(), anyString(), isNull(), isNull(), anyString(), anyString(), any(), eq(PaymentProxy.ON_SITE.toString()), isNull());
        verify(waitingQueueManager, never()).fireReservationConfirmed(RESERVATION_ID);
        verify(ticketReservationRepository, never()).setInvoiceNumber(eq(RESERVATION_ID), any());
        verify(ticketReservationRepository).findReservationByIdForUpdate(eq(RESERVATION_ID));
        verify(ticketReservationRepository, atLeastOnce()).findReservationById(RESERVATION_ID);
        verify(billingDocumentManager, never()).generateInvoiceNumber(eq(spec), any());
        verify(ticketReservationRepository).updateBillingData(eq(PriceContainer.VatStatus.INCLUDED), eq(100), eq(100), eq(0), eq(0), eq(EVENT_CURRENCY), eq("123456"), eq("IT"), eq(true), eq(RESERVATION_ID));
        verify(ticketRepository, atLeastOnce()).findTicketsInReservation(anyString());
    }

    @Test
    void handleOfflinePaymentMethod() {
        when(ticketReservationRepository.findOptionalStatusAndValidationById(eq(RESERVATION_ID))).thenReturn(Optional.of(new TicketReservationStatusAndValidation(PENDING, true)));
        initConfirmReservation();
        when(ticketReservationRepository.postponePayment(eq(RESERVATION_ID), eq(OFFLINE_PAYMENT), any(Date.class), anyString(), anyString(), isNull(), isNull(), anyString(), isNull())).thenReturn(1);
        when(ticketReservation.getPromoCodeDiscountId()).thenReturn(null);
        when(configurationManager.getFor(eq(BANKING_KEY), any())).thenReturn(BANKING_INFO);
        BankTransferManager bankTransferManager = mock(BankTransferManager.class);
        when(bankTransferManager.accept(eq(PaymentMethod.BANK_TRANSFER), any(), any())).thenReturn(true);
        when(paymentManager.streamActiveProvidersByProxy(eq(PaymentProxy.OFFLINE), any())).thenReturn(Stream.of(bankTransferManager));
        when(bankTransferManager.getTokenAndPay(any())).thenReturn(PaymentResult.successful(TicketReservationManager.NOT_YET_PAID_TRANSACTION_ID));
        PaymentSpecification spec = new PaymentSpecification(RESERVATION_ID, new StripeCreditCardToken(GATEWAY_TOKEN), 100, event, "test@email",
            new CustomerName("Full Name", null, null, event.mustUseFirstAndLastName()),
            "", null, Locale.ENGLISH, true, false, null, "IT", "123456", PriceContainer.VatStatus.INCLUDED, true, false);
        var invoiceNumber = "1234";
        when(billingDocumentManager.generateInvoiceNumber(eq(spec), any())).thenReturn(Optional.of(invoiceNumber));
        PaymentResult result = trm.performPayment(spec, new TotalPrice(100, 0, 0, 0,"CHF"), PaymentProxy.OFFLINE, PaymentMethod.BANK_TRANSFER, null);
        Assertions.assertTrue(result.isSuccessful());
        Assertions.assertEquals(Optional.of(TicketReservationManager.NOT_YET_PAID_TRANSACTION_ID), result.getGatewayId());
        verify(waitingQueueManager, never()).fireReservationConfirmed(eq(RESERVATION_ID));
        verify(ticketReservationRepository).findReservationByIdForUpdate(eq(RESERVATION_ID));
        verify(billingDocumentManager, never()).generateInvoiceNumber(eq(spec), any());
        verify(ticketReservationRepository, never()).setInvoiceNumber(RESERVATION_ID, invoiceNumber);
        verify(ticketReservationRepository).updateBillingData(eq(PriceContainer.VatStatus.INCLUDED), eq(100), eq(100), eq(0), eq(0), eq(EVENT_CURRENCY), eq("123456"), eq("IT"), eq(true), eq(RESERVATION_ID));
    }

    @Test
    void confirmOfflinePayments() {
        initConfirmReservation();
        TicketReservation reservation = mock(TicketReservation.class);
        when(reservation.getConfirmationTimestamp()).thenReturn(ZonedDateTime.now(ClockProvider.clock()));
        when(reservation.getId()).thenReturn(RESERVATION_ID);
        when(reservation.getPaymentMethod()).thenReturn(PaymentProxy.OFFLINE);
        when(reservation.getStatus()).thenReturn(OFFLINE_PAYMENT);
        when(reservation.getUserLanguage()).thenReturn("en");
        when(reservation.getFullName()).thenReturn("Full Name");
        when(reservation.getEmail()).thenReturn("ciccio");
        when(reservation.getValidity()).thenReturn(new Date(Instant.now(ClockProvider.clock()).getEpochSecond()));
        when(reservation.getInvoiceModel()).thenReturn("{\"summary\":[], \"originalTotalPrice\":{\"priceWithVAT\":100}}");

        TicketReservation copy = copy(reservation);
        Event event = copy(this.event);
        when(ticketReservationRepository.findOptionalReservationById(eq(RESERVATION_ID))).thenReturn(Optional.of(copy));
        when(ticketReservationRepository.findReservationByIdForUpdate(eq(RESERVATION_ID))).thenReturn(copy);
        when(ticketRepository.updateTicketsStatusWithReservationId(eq(RESERVATION_ID), eq(TicketStatus.ACQUIRED.toString()))).thenReturn(1);
        when(ticketReservationRepository.updateTicketReservation(eq(RESERVATION_ID), eq(COMPLETE.toString()), anyString(), anyString(), isNull(), isNull(), anyString(), isNull(), any(), eq(PaymentProxy.OFFLINE.toString()), isNull())).thenReturn(1);
        when(configurationManager.getFor(eq(VAT_NR), any())).thenReturn(new MaybeConfiguration(VAT_NR, new ConfigurationKeyValuePathLevel(null, "vatnr", null)));
        when(configurationManager.getFor(eq(BANKING_KEY), any())).thenReturn(BANKING_INFO);
        when(configurationManager.getFor(eq(ENABLE_TICKET_TRANSFER), any())).thenReturn(
            new MaybeConfiguration(ENABLE_TICKET_TRANSFER)
        );
        when(configurationManager.getFor(eq(PLATFORM_MODE_ENABLED), any())).thenReturn(new MaybeConfiguration(PLATFORM_MODE_ENABLED));
        when(ticketRepository.findTicketsInReservation(eq(RESERVATION_ID))).thenReturn(Collections.emptyList());
        when(eventRepository.findByReservationId(eq(RESERVATION_ID))).thenReturn(event);
        when(reservation.getUserLanguage()).thenReturn("en");
        when(reservation.getPromoCodeDiscountId()).thenReturn(null);
        when(organizationRepository.getById(eq(ORGANIZATION_ID))).thenReturn(new Organization(1, "", "", "", null, null));
//        when(configurationManager.getBooleanConfigValue(eq(Configuration.from(event).apply(ENABLE_TICKET_TRANSFER)), eq(true))).thenReturn(true);

        when(billingDocumentRepository.insert(anyInt(), anyString(), anyString(), any(BillingDocument.Type.class), anyString(), any(ZonedDateTime.class), anyInt()))
            .thenReturn(new AffectedRowCountAndKey<>(1, 1L));
        when(billingDocumentRepository.findByIdAndReservationId(anyLong(), anyString()))
            .thenReturn(Optional.of(new BillingDocument(1, 1, "1", "42", BillingDocument.Type.INVOICE, "{}", ZonedDateTime.now(ClockProvider.clock()),
                BillingDocument.Status.VALID, null)));
        when(json.fromJsonString(anyString(), eq(OrderSummary.class))).thenReturn(mock(OrderSummary.class));
        when(json.asJsonString(any())).thenReturn("{}");
        when(configurationManager.getFor(eq(EnumSet.of(DEFERRED_BANK_TRANSFER_ENABLED, DEFERRED_BANK_TRANSFER_SEND_CONFIRMATION_EMAIL)), any())).thenReturn(Map.of(DEFERRED_BANK_TRANSFER_ENABLED, new MaybeConfiguration(DEFERRED_BANK_TRANSFER_ENABLED)));
        when(reservationCostCalculator.totalReservationCostWithVAT(RESERVATION_ID)).thenReturn(Pair.of(new TotalPrice(0, 0, 0, 0, "CHF"), Optional.empty()));
        assertThrows(IncompatibleStateException.class, () -> trm.confirmOfflinePayment(event, RESERVATION_ID, null, "username"));
        when(metadata.isReadyForConfirmation()).thenReturn(true);
        trm.confirmOfflinePayment(event, RESERVATION_ID, null, "username");
        verify(ticketReservationRepository, atLeastOnce()).findOptionalReservationById(RESERVATION_ID);
        verify(ticketReservationRepository, atLeastOnce()).findReservationById(RESERVATION_ID);
        verify(ticketReservationRepository, times(2)).lockReservationForUpdate(eq(RESERVATION_ID));
        verify(ticketReservationRepository).confirmOfflinePayment(eq(RESERVATION_ID), eq(COMPLETE.toString()), any(ZonedDateTime.class));
        verify(ticketRepository).updateTicketsStatusWithReservationId(eq(RESERVATION_ID), eq(TicketStatus.ACQUIRED.toString()));
        verify(ticketReservationRepository).updateTicketReservation(eq(RESERVATION_ID), eq(TicketReservationStatus.COMPLETE.toString()), anyString(), anyString(), isNull(), isNull(), anyString(), isNull(), any(), eq(PaymentProxy.OFFLINE.toString()), isNull());
        verify(waitingQueueManager).fireReservationConfirmed(eq(RESERVATION_ID));
        verify(ticketRepository, atLeastOnce()).findTicketsInReservation(RESERVATION_ID);
        verify(specialPriceRepository).updateStatusForReservation(eq(singletonList(RESERVATION_ID)), eq(SpecialPrice.Status.TAKEN.toString()));
        verify(reservationHelper).sendConfirmationEmail(event, copy, Locale.ENGLISH, "username");
        verify(ticketRepository).countTicketsInReservation(eq(RESERVATION_ID));
        verify(configurationManager).getFor(eq(PLATFORM_MODE_ENABLED), any());
    }

    private static Event copy(Event event) {
        return new Event(
            EVENT_ID,
            event.getFormat(),
            event.getShortName(),
            event.getDisplayName(),
            event.getLocation(),
            event.getLatitude(),
            event.getLongitude(),
            Optional.ofNullable(event.getBegin()).orElse(ZonedDateTime.now(ClockProvider.clock()).plusDays(2).minusHours(2)),
            Optional.ofNullable(event.getEnd()).orElse(ZonedDateTime.now(ClockProvider.clock()).plusDays(2)),
            "UTC",
            event.getWebsiteUrl(),
            event.getExternalUrl(),
            event.getFileBlobId(),
            event.getTermsAndConditionsUrl(),
            event.getPrivacyPolicyUrl(),
            event.getImageUrl(),
            event.getCurrency(),
            event.getVat(),
            event.getShortName(),
            event.getPrivateKey(),
            event.getOrganizationId(),
            event.getLocales(),
            event.getId(),
            event.getVatStatus(),
            event.getVersion(),
            event.getStatus()
        );
    }

    private static TicketReservation copy(TicketReservation reservation) {
        return new TicketReservation(reservation.getId(),
            reservation.getValidity(),
            reservation.getStatus(),
            reservation.getFullName(),
            reservation.getFirstName(),
            reservation.getLastName(),
            reservation.getEmail(),
            reservation.getBillingAddress(),
            reservation.getConfirmationTimestamp(),
            reservation.getLatestReminder(),
            reservation.getPaymentMethod(),
            reservation.getReminderSent(),
            reservation.getPromoCodeDiscountId(),
            reservation.isAutomatic(),
            reservation.getUserLanguage(),
            reservation.isDirectAssignmentRequested(),
            reservation.getInvoiceNumber(),
            reservation.getInvoiceModel(),
            reservation.getVatStatus(),
            reservation.getVatNr(),
            reservation.getVatCountryCode(),
            reservation.isInvoiceRequested(),
            reservation.getUsedVatPercent(),
            reservation.getVatIncluded(),
            reservation.getCreationTimestamp(),
            reservation.getCustomerReference(),
            reservation.getRegistrationTimestamp(),
            reservation.getSrcPriceCts(),
            reservation.getFinalPriceCts(),
            reservation.getVatCts(),
            reservation.getDiscountCts(),
            reservation.getCurrencyCode());
    }

    @Test
    void reservationURLGeneration() {
        String shortName = "shortName";
        String ticketId = "ticketId";
        when(event.getType()).thenReturn(PurchaseContext.PurchaseContextType.event);
        when(event.getPublicIdentifier()).thenReturn(shortName);
        when(ticketReservation.getUserLanguage()).thenReturn("en");
        when(ticketReservation.getId()).thenReturn(RESERVATION_ID);
        when(ticketReservationRepository.findReservationById(RESERVATION_ID)).thenReturn(ticketReservation);
        when(ticketRepository.findByUUID(ticketId)).thenReturn(ticket);
        when(ticket.getUuid()).thenReturn(ticketId);
        when(ticket.getUserLanguage()).thenReturn(USER_LANGUAGE);
        //generate the reservationUrl from RESERVATION_ID
        Assertions.assertEquals(BASE_URL + "event/" + shortName + "/reservation/" + RESERVATION_ID + "?lang=en", trm.reservationUrl(RESERVATION_ID));
        //generate the reservationUrl from RESERVATION_ID and event
        Assertions.assertEquals(BASE_URL + "event/" + shortName + "/reservation/" + RESERVATION_ID + "?lang=en", trm.reservationUrl(RESERVATION_ID, event));
        //generate the reservationUrl from reservation and event
        Assertions.assertEquals(BASE_URL + "event/" + shortName + "/reservation/" + RESERVATION_ID + "?lang=en", trm.reservationUrl(ticketReservation, event));

        when(event.getShortName()).thenReturn(shortName);

        //generate the ticket URL
        Assertions.assertEquals(BASE_URL + "event/" + shortName + "/ticket/ticketId?lang=it", trm.ticketUrl(event, ticketId));
        //generate the ticket update URL
        Assertions.assertEquals(BASE_URL + "event/" + shortName + "/ticket/ticketId/update?lang=it", ReservationUtil.ticketUpdateUrl(event, ticket, configurationManager));
    }

    @Test
    void reservationUrlForExternalClients() {
        String shortName = "shortName";
        when(event.getType()).thenReturn(PurchaseContext.PurchaseContextType.event);
        when(event.getPublicIdentifier()).thenReturn(shortName);
        when(ticketReservation.getUserLanguage()).thenReturn("en");
        when(ticketReservation.getId()).thenReturn(RESERVATION_ID);

        var maybeOpenId = mock(MaybeConfiguration.class);
        var maybeBaseUrl = mock(MaybeConfiguration.class);
        when(maybeBaseUrl.getRequiredValue()).thenReturn(BASE_URL);

        when(configurationManager.getFor(eq(EnumSet.of(ConfigurationKeys.BASE_URL, OPENID_PUBLIC_ENABLED)), any()))
            .thenReturn(Map.of(ConfigurationKeys.BASE_URL, maybeBaseUrl, OPENID_PUBLIC_ENABLED, maybeOpenId));

        // OpenID active
        when(maybeOpenId.getValueAsBooleanOrDefault()).thenReturn(true);
        Assertions.assertEquals(BASE_URL + "openid/authentication?reservation=" + RESERVATION_ID + "&contextType=" + PurchaseContext.PurchaseContextType.event + "&id=" + shortName, trm.reservationUrlForExternalClients(RESERVATION_ID, event, "en", true, null));

        // user not specified in the request
        Assertions.assertEquals(BASE_URL + "event/" + shortName + "/reservation/" + RESERVATION_ID + "?lang=en", trm.reservationUrlForExternalClients(RESERVATION_ID, event, "en", false, null));
        // OpenID not active
        when(maybeOpenId.getValueAsBooleanOrDefault()).thenReturn(false);
        Assertions.assertEquals(BASE_URL + "event/" + shortName + "/reservation/" + RESERVATION_ID + "?lang=en", trm.reservationUrlForExternalClients(RESERVATION_ID, event, "en", true, null));
        // SubscriptionId is present
        var subscriptionId = "subscription-id";
        Assertions.assertEquals(BASE_URL + "event/" + shortName + "/reservation/" + RESERVATION_ID + "?lang=en&subscription=" + subscriptionId, trm.reservationUrlForExternalClients(RESERVATION_ID, event, "en", true, subscriptionId));
    }

    //sendReminderForOptionalInfo
    private void initReminder() {
        when(ticketFieldRepository.countAdditionalFieldsForEvent(EVENT_ID)).thenReturn(1);
    }


    @Test
    void sendReminderOnlyIfNoPreviousNotifications() {
        initReminder();
        when(event.getId()).thenReturn(EVENT_ID);
        when(configurationManager.getFor(eq(ASSIGNMENT_REMINDER_START), any())).thenReturn(new MaybeConfiguration(ASSIGNMENT_REMINDER_START));
        when(ticketReservation.latestNotificationTimestamp(any())).thenReturn(Optional.empty());
        when(ticketReservation.getId()).thenReturn(RESERVATION_ID);
        when(ticket.getTicketsReservationId()).thenReturn(RESERVATION_ID);
        int ticketId = 2;
        when(ticket.getId()).thenReturn(ticketId);
        when(ticket.getUuid()).thenReturn("uuid");
        when(ticket.getEmail()).thenReturn("ciccio");
        when(ticketRepository.findAllAssignedButNotYetNotifiedForUpdate(EVENT_ID)).thenReturn(singletonList(ticket));
        when(ticketReservationRepository.findOptionalReservationById(eq(RESERVATION_ID))).thenReturn(Optional.of(ticketReservation));

        when(eventRepository.findByReservationId(RESERVATION_ID)).thenReturn(event);
        when(event.getZoneId()).thenReturn(ClockProvider.clock().getZone());
        when(event.getBegin()).thenReturn(ZonedDateTime.now(ClockProvider.clock()).plusDays(1));
        when(eventRepository.findAll()).thenReturn(singletonList(event));
        when(ticketRepository.findAllReservationsConfirmedButNotAssignedForUpdate(anyInt())).thenReturn(singleton(RESERVATION_ID));
        when(ticketRepository.flagTicketAsReminderSent(ticketId)).thenReturn(1);
        when(ticketRepository.findByUUID(anyString())).thenReturn(ticket);
        when(messageSource.getMessage(eq("reminder.ticket-additional-info.subject"), any(), any())).thenReturn("subject");
        when(configurationManager.getFor(eq(OPTIONAL_DATA_REMINDER_ENABLED), any())).thenReturn(
            MaybeConfigurationBuilder.existing(OPTIONAL_DATA_REMINDER_ENABLED, "true")
        );
        trm.sendReminderForOptionalData();
        verify(notificationManager, times(1)).sendSimpleEmail(eq(event), eq(RESERVATION_ID), eq("ciccio"), eq("subject"), any(TemplateGenerator.class));
    }

    @Test
    void doNotSendReminderIfPreviousNotifications() {
        initReminder();
        when(event.getId()).thenReturn(EVENT_ID);
        when(configurationManager.getFor(eq(ASSIGNMENT_REMINDER_START), any())).thenReturn(new MaybeConfiguration(ASSIGNMENT_REMINDER_START));
//        when(configurationManager.getStringConfigValue(any())).thenReturn(Optional.empty());
        when(configurationManager.getFor(eq(OPTIONAL_DATA_REMINDER_ENABLED), any())).thenReturn(
            MaybeConfigurationBuilder.existing(OPTIONAL_DATA_REMINDER_ENABLED, "true")
        );
        when(ticketReservation.latestNotificationTimestamp(any())).thenReturn(Optional.of(ZonedDateTime.now(ClockProvider.clock()).minusDays(10)));
        String RESERVATION_ID = "abcd";
        when(ticketReservation.getId()).thenReturn(RESERVATION_ID);
        when(ticket.getTicketsReservationId()).thenReturn(RESERVATION_ID);
        int ticketId = 2;
        when(ticket.getId()).thenReturn(ticketId);
        when(ticketRepository.findAllAssignedButNotYetNotifiedForUpdate(EVENT_ID)).thenReturn(singletonList(ticket));
        when(ticketReservationRepository.findReservationByIdForUpdate(eq(RESERVATION_ID))).thenReturn(ticketReservation);

        when(eventRepository.findByReservationId(RESERVATION_ID)).thenReturn(event);
        when(event.getZoneId()).thenReturn(ClockProvider.clock().getZone());
        when(event.getBegin()).thenReturn(ZonedDateTime.now(ClockProvider.clock()).plusDays(1));
        when(eventRepository.findAll()).thenReturn(singletonList(event));
        when(ticketRepository.flagTicketAsReminderSent(ticketId)).thenReturn(1);
        trm.sendReminderForOptionalData();
        verify(notificationManager, never()).sendSimpleEmail(eq(event), anyString(), anyString(), anyString(), any(TemplateGenerator.class));
    }

    @Test
    void doNotSendReminderIfTicketHasAlreadyBeenModified() {
        initReminder();
        when(event.getId()).thenReturn(EVENT_ID);
        when(configurationManager.getFor(eq(ASSIGNMENT_REMINDER_START), any())).thenReturn(new MaybeConfiguration(ASSIGNMENT_REMINDER_START));
//        when(configurationManager.getStringConfigValue(any())).thenReturn(Optional.empty());
        when(configurationManager.getFor(eq(OPTIONAL_DATA_REMINDER_ENABLED), any())).thenReturn(
            MaybeConfigurationBuilder.existing(OPTIONAL_DATA_REMINDER_ENABLED, "true")
        );
        when(ticketReservation.latestNotificationTimestamp(any())).thenReturn(Optional.empty());
        String RESERVATION_ID = "abcd";
        when(ticketReservation.getId()).thenReturn(RESERVATION_ID);
        when(ticket.getTicketsReservationId()).thenReturn(RESERVATION_ID);
        int ticketId = 2;
        when(ticket.getId()).thenReturn(ticketId);
        when(ticketRepository.findAllAssignedButNotYetNotifiedForUpdate(EVENT_ID)).thenReturn(singletonList(ticket));
        when(ticketReservationRepository.findReservationByIdForUpdate(eq(RESERVATION_ID))).thenReturn(ticketReservation);

        when(eventRepository.findByReservationId(RESERVATION_ID)).thenReturn(event);
        when(event.getZoneId()).thenReturn(ClockProvider.clock().getZone());
        when(event.getBegin()).thenReturn(ZonedDateTime.now(ClockProvider.clock()).plusDays(1));
        when(eventRepository.findAll()).thenReturn(singletonList(event));
        when(ticketRepository.flagTicketAsReminderSent(ticketId)).thenReturn(0);
        trm.sendReminderForOptionalData();
        verify(notificationManager, never()).sendSimpleEmail(eq(event), anyString(), anyString(), anyString(), any(TemplateGenerator.class));
    }

    @Test
    void testDetectTicketReassigned() {
        when(ticket.getEmail()).thenReturn("test@test.ch");
        when(ticket.getFullName()).thenReturn("Test Test");
        when(event.mustUseFirstAndLastName()).thenReturn(true);
        UpdateTicketOwnerForm form = new UpdateTicketOwnerForm();
        form.setEmail("test@test.ch");
        form.setFirstName("Test2");
        form.setLastName("Test");
        Assertions.assertTrue(trm.isTicketBeingReassigned(ticket, form, event));
    }

    @Test
    void testDetectTicketNotReassigned() {
        when(ticket.getEmail()).thenReturn("test@test.ch");
        when(ticket.getFullName()).thenReturn("Test Test");
        when(event.mustUseFirstAndLastName()).thenReturn(true);
        UpdateTicketOwnerForm form = new UpdateTicketOwnerForm();
        form.setEmail("test@test.ch");
        form.setFirstName("Test");
        form.setLastName("Test");
        Assertions.assertFalse(trm.isTicketBeingReassigned(ticket, form, event));
    }

    @Test
    void testDetectTicketWasNotYetAssigned() {
        when(ticket.getEmail()).thenReturn(null);
        when(ticket.getFullName()).thenReturn(null);
        when(event.mustUseFirstAndLastName()).thenReturn(true);
        UpdateTicketOwnerForm form = new UpdateTicketOwnerForm();
        form.setEmail("test@test.ch");
        form.setFirstName("Test");
        form.setLastName("Test");
        Assertions.assertFalse(trm.isTicketBeingReassigned(ticket, form, event));
    }

    @Test
    void testBuildCompleteBillingAddress() {
        CustomerName customerName = new CustomerName(null, "First", "Last", true);
        Assertions.assertEquals("First Last\nline1\nzip city state\nSwitzerland", buildCompleteBillingAddress(customerName, "   ", "line1", null, "zip", "city", "state", "CH", Locale.ENGLISH));
        Assertions.assertEquals("Company\nFirst Last\nline1\nzip city state\nSwitzerland", buildCompleteBillingAddress(customerName, "Company", "line1", null, "zip", "city", "state", "CH", Locale.ENGLISH));
        Assertions.assertEquals("Company\nFirst Last\nline1\nline2\nzip city state\nSwitzerland", buildCompleteBillingAddress(customerName, "Company", "line1", "line2", "zip", "city", "state", "CH", Locale.ENGLISH));
    }

    @Test
    void testValidatePaymentMethodsReservationFreeOfCharge() {
        when(totalPrice.requiresPayment()).thenReturn(false);
        when(configurationManager.getFor(eq(RESERVATION_TIMEOUT), any())).thenReturn(new MaybeConfiguration(RESERVATION_TIMEOUT));
        when(ticketRepository.getCategoriesIdToPayInReservation(RESERVATION_ID)).thenReturn(List.of(1));
        when(configurationManager.getBlacklistedMethodsForReservation(eq(event), any())).thenReturn(Arrays.asList(PaymentMethod.values()));
        when(paymentManager.getPaymentMethods(eq(event), any())).thenReturn(Arrays.stream(PaymentProxy.values()).map(pp -> new PaymentMethodDTO(pp, pp.getPaymentMethod(), PaymentMethodStatus.ACTIVE)).collect(Collectors.toList()));
        Assertions.assertTrue(trm.canProceedWithPayment(event, totalPrice, RESERVATION_ID));
    }

    @Test
    void testValidatePaymentMethodsPayPalError() {
        when(totalPrice.requiresPayment()).thenReturn(true);
        when(ticketRepository.getCategoriesIdToPayInReservation(RESERVATION_ID)).thenReturn(List.of(1));
        when(configurationManager.getBlacklistedMethodsForReservation(eq(event), any())).thenReturn(new ArrayList<>(EnumSet.complementOf(EnumSet.of(PaymentMethod.PAYPAL, PaymentMethod.NONE))));
        when(paymentManager.getPaymentMethods(eq(event), any())).thenReturn(Arrays.stream(PaymentProxy.values()).map(pp -> new PaymentMethodDTO(pp, pp.getPaymentMethod(), pp.getPaymentMethod() == PaymentMethod.PAYPAL ? PaymentMethodStatus.ERROR : PaymentMethodStatus.ACTIVE)).collect(Collectors.toList()));
        Assertions.assertFalse(trm.canProceedWithPayment(event, totalPrice, RESERVATION_ID));
    }

    @Test
    void testValidatePaymentMethodsAllBlacklisted() {
        when(totalPrice.requiresPayment()).thenReturn(true);
        when(ticketRepository.getCategoriesIdToPayInReservation(RESERVATION_ID)).thenReturn(List.of(1));
        when(configurationManager.getBlacklistedMethodsForReservation(eq(event), any())).thenReturn(new ArrayList<>(EnumSet.complementOf(EnumSet.of(PaymentMethod.NONE))));
        when(paymentManager.getPaymentMethods(eq(event), any())).thenReturn(Arrays.stream(PaymentProxy.values()).map(pp -> new PaymentMethodDTO(pp, pp.getPaymentMethod(), PaymentMethodStatus.ACTIVE)).collect(Collectors.toList()));
        Assertions.assertFalse(trm.canProceedWithPayment(event, totalPrice, RESERVATION_ID));
    }

    @Test
    void testValidatePaymentMethodsAllowed() {
        when(totalPrice.requiresPayment()).thenReturn(true);
        when(ticketRepository.getCategoriesIdToPayInReservation(RESERVATION_ID)).thenReturn(List.of(1));
        when(configurationManager.getBlacklistedMethodsForReservation(eq(event), any())).thenReturn(List.of());
        when(paymentManager.getPaymentMethods(eq(event), any())).thenReturn(Arrays.stream(PaymentProxy.values()).map(pp -> new PaymentMethodDTO(pp, pp.getPaymentMethod(), PaymentMethodStatus.ACTIVE)).collect(Collectors.toList()));
        Assertions.assertTrue(trm.canProceedWithPayment(event, totalPrice, RESERVATION_ID));
    }

    @Test
    void testValidatePaymentMethodsPartiallyAllowed() {
        when(totalPrice.requiresPayment()).thenReturn(true);
        when(ticketRepository.getCategoriesIdToPayInReservation(RESERVATION_ID)).thenReturn(List.of(1, 2));
        when(configurationManager.getBlacklistedMethodsForReservation(eq(event), any())).thenReturn(List.of(PaymentMethod.CREDIT_CARD));
        when(paymentManager.getPaymentMethods(eq(event), any())).thenReturn(Arrays.stream(PaymentProxy.values()).map(pp -> new PaymentMethodDTO(pp, pp.getPaymentMethod(), PaymentMethodStatus.ACTIVE)).collect(Collectors.toList()));
        Assertions.assertTrue(trm.canProceedWithPayment(event, totalPrice, RESERVATION_ID));
    }

    @Nested
    class TestCancelReservationsPendingPayment {

        private static final String PENDING_RESERVATION_ID = "reservation-id2";
        private static final String EXPIRED_RESERVATION_ID = "reservation-id1";
        private static final String PAYMENT_ID = "paymentId";
        private final List<String> reservationIds = List.of(EXPIRED_RESERVATION_ID, PENDING_RESERVATION_ID);
        private final List<String> expiredReservationIds = List.of(EXPIRED_RESERVATION_ID);
        Date now = new Date(Instant.now(TestUtil.clockProvider().getClock()).getEpochSecond());
        Transaction transactionMock;
        TicketReservation pendingReservationMock;
        PurchaseContext purchaseContextMock;

        @BeforeEach
        void setUp() {
            when(ticketReservationRepository.findExpiredReservationForUpdate(now)).thenReturn(reservationIds);
            pendingReservationMock = mock(TicketReservation.class);
            when(pendingReservationMock.getId()).thenReturn(PENDING_RESERVATION_ID);
            when(pendingReservationMock.getSrcPriceCts()).thenReturn(100);
            when(pendingReservationMock.getVatCts()).thenReturn(1);
            when(pendingReservationMock.getVatStatus()).thenReturn(PriceContainer.VatStatus.INCLUDED);
            when(pendingReservationMock.getFirstName()).thenReturn("First");
            when(pendingReservationMock.getLastName()).thenReturn("Last");
            when(ticketReservationRepository.findOptionalStatusAndValidationById(PENDING_RESERVATION_ID))
                .thenReturn(Optional.of(new TicketReservationStatusAndValidation(COMPLETE, true)));
            purchaseContextMock = mock(PurchaseContext.class);
            when(purchaseContextMock.getCurrency()).thenReturn("CHF");
            transactionMock = mock(Transaction.class);
            when(transactionMock.getPaymentId()).thenReturn(PAYMENT_ID);
            when(transactionMock.getStatus()).thenReturn(Transaction.Status.PENDING);
            when(ticketReservationRepository.findReservationsWithPendingTransaction(reservationIds)).thenReturn(List.of(pendingReservationMock));
            when(purchaseContextManager.findByReservationId(PENDING_RESERVATION_ID)).thenReturn(Optional.of(purchaseContextMock));
            when(transactionRepository.loadOptionalByReservationIdAndStatusForUpdate(PENDING_RESERVATION_ID, Transaction.Status.PENDING))
                .thenReturn(Optional.of(transactionMock));
        }

        @Test
        void cancelExpiredReservationsPendingPaymentConfirmed() {
            var stripeManager = mock(StripeWebhookPaymentManager.class);
            when(paymentManager.lookupProviderByTransactionAndCapabilities(transactionMock, List.of(WebhookHandler.class)))
                .thenReturn(Optional.of(stripeManager));
            when(stripeManager.forceTransactionCheck(eq(pendingReservationMock), eq(transactionMock), any()))
                .thenReturn(PaymentWebhookResult.successful(new StripeCreditCardToken("")));
            when(reservationCostCalculator.totalReservationCostWithVAT(pendingReservationMock)).thenReturn(Pair.of(new TotalPrice(0, 0, 0, 0, "CHF"), Optional.empty()));
            trm.cleanupExpiredReservations(now);
            verify(ticketReservationRepository).findExpiredReservationForUpdate(now);
            verify(specialPriceRepository).resetToFreeAndCleanupForReservation(expiredReservationIds);
            verify(ticketRepository).resetCategoryIdForUnboundedCategories(expiredReservationIds);
            verify(ticketRepository).freeFromReservation(expiredReservationIds);
            verify(ticketReservationRepository).remove(expiredReservationIds);
            verify(waitingQueueManager).cleanExpiredReservations(expiredReservationIds);
            verify(ticketReservationRepository).getReservationIdAndEventId(expiredReservationIds);
            verify(ticketReservationRepository).findReservationsWithPendingTransaction(reservationIds);
            verify(ticketReservationRepository).findOptionalStatusAndValidationById(PENDING_RESERVATION_ID);
            verifyNoMoreInteractions(ticketReservationRepository, specialPriceRepository, ticketRepository);
        }

        @Test
        void cancelExpiredReservationsPendingPaymentPending() {
            var stripeManager = mock(StripeWebhookPaymentManager.class);
            when(paymentManager.lookupProviderByTransactionAndCapabilities(transactionMock, List.of(WebhookHandler.class)))
                .thenReturn(Optional.of(stripeManager));
            when(paymentManager.lookupProviderByTransactionAndCapabilities(transactionMock, List.of(ServerInitiatedTransaction.class)))
                .thenReturn(Optional.of(stripeManager));
            when(stripeManager.forceTransactionCheck(eq(pendingReservationMock), eq(transactionMock), any()))
                .thenReturn(PaymentWebhookResult.pending());
            when(stripeManager.discardTransaction(transactionMock, purchaseContextMock))
                .thenReturn(true);
            when(ticketReservationRepository.findOptionalReservationById(PENDING_RESERVATION_ID))
                .thenReturn(Optional.of(pendingReservationMock));
            when(transactionRepository.loadOptionalByReservationId(PENDING_RESERVATION_ID))
                .thenReturn(Optional.of(transactionMock));
            when(ticketReservationRepository.updateReservationStatus(PENDING_RESERVATION_ID, TicketReservationStatus.PENDING.toString()))
                .thenReturn(1);
            trm.cleanupExpiredReservations(now);
            verify(ticketReservationRepository).findExpiredReservationForUpdate(now);
            verify(specialPriceRepository).resetToFreeAndCleanupForReservation(reservationIds);
            verify(ticketRepository).resetCategoryIdForUnboundedCategories(reservationIds);
            verify(ticketRepository).freeFromReservation(reservationIds);
            verify(ticketReservationRepository).remove(reservationIds);
            verify(waitingQueueManager).cleanExpiredReservations(reservationIds);
            verify(ticketReservationRepository).getReservationIdAndEventId(reservationIds);
            verify(transactionRepository).deleteForReservationsWithStatus(List.of(PENDING_RESERVATION_ID), Transaction.Status.PENDING);
            verify(ticketReservationRepository).findOptionalReservationById(PENDING_RESERVATION_ID);
            verify(transactionRepository).loadOptionalByReservationId(PENDING_RESERVATION_ID);
            verify(ticketReservationRepository).updateReservationStatus(PENDING_RESERVATION_ID, TicketReservationStatus.PENDING.toString());
            verify(ticketReservationRepository).findReservationsWithPendingTransaction(reservationIds);
            verify(stripeManager).discardTransaction(transactionMock, purchaseContextMock);
            verifyNoMoreInteractions(ticketReservationRepository, specialPriceRepository, ticketRepository);
        }
    }

    @Nested
    class TestSendReservationEmailIfNecessary {

        private MaybeConfiguration sendReservationEmailIfNecessary;
        private MaybeConfiguration sendTickets;
        private final String reservationEmail = "blabla@example.org";
        private ReservationFinalizer finalizer;

        @BeforeEach
        @SuppressWarnings("unchecked")
        void setUp() {
            finalizer = new ReservationFinalizer(mock(PlatformTransactionManager.class),
                ticketReservationRepository, userRepository, mock(ExtensionManager.class), auditingRepository, mock(ClockProvider.class), configurationManager,
                mock(SubscriptionRepository.class), ticketRepository, reservationHelper, mock(SpecialPriceRepository.class),
                waitingQueueManager, ticketCategoryRepository, mock(ReservationCostCalculator.class), billingDocumentManager, mock(AdditionalServiceItemRepository.class),
                mock(OrderSummaryGenerator.class), transactionRepository, mock(AdminJobQueueRepository.class), purchaseContextManager, mock(Json.class));
            sendReservationEmailIfNecessary = mock(MaybeConfiguration.class);
            sendTickets = mock(MaybeConfiguration.class);
            when(ticketReservation.getSrcPriceCts()).thenReturn(0);
            when(ticketReservation.getEmail()).thenReturn(reservationEmail);
            when(ticket.getEmail()).thenReturn(reservationEmail);
            Map<ConfigurationKeys, MaybeConfiguration> configurations = mock(Map.class);
            when(configurations.get(any(ConfigurationKeys.class))).thenReturn(mock(MaybeConfiguration.class));
            when(configurations.get(eq(SEND_RESERVATION_EMAIL_IF_NECESSARY))).thenReturn(sendReservationEmailIfNecessary);
            when(configurations.get(eq(SEND_TICKETS_AUTOMATICALLY))).thenReturn(sendTickets);
            when(configurationManager.getFor(anyCollection(), any())).thenReturn(configurations);
        }

        @Test
        void emailSentBecauseReservationIsNotFreeOfCharge() {
            when(ticketReservation.getSrcPriceCts()).thenReturn(1);
            finalizer.sendConfirmationEmailIfNecessary(ticketReservation, List.of(ticket), event, Locale.ENGLISH, null);
            verify(reservationHelper).sendConfirmationEmail(event, ticketReservation, Locale.ENGLISH, null);
        }

        @Test
        void emailSentBecauseThereIsMoreThanOneTicketInTheReservation() {
            finalizer.sendConfirmationEmailIfNecessary(ticketReservation, List.of(ticket, ticket), event, Locale.ENGLISH, null);
            verify(reservationHelper).sendConfirmationEmail(event, ticketReservation, Locale.ENGLISH, null);
        }

        @Test
        void emailSentBecauseTicketListIsNull() {
            finalizer.sendConfirmationEmailIfNecessary(ticketReservation, null, event, Locale.ENGLISH, null);
            verify(reservationHelper).sendConfirmationEmail(event, ticketReservation, Locale.ENGLISH, null);
        }

        @Test
        void emailSentBecauseTicketListIsEmpty() {
            finalizer.sendConfirmationEmailIfNecessary(ticketReservation, List.of(), event, Locale.ENGLISH, null);
            verify(reservationHelper).sendConfirmationEmail(event, ticketReservation, Locale.ENGLISH, null);
        }

        @Test
        void emailSentBecauseTicketHolderEmailIsDifferentFromReservation() {
            when(ticket.getEmail()).thenReturn("blabla2@example.org");
            finalizer.sendConfirmationEmailIfNecessary(ticketReservation, List.of(ticket), event, Locale.ENGLISH, null);
            verify(reservationHelper).sendConfirmationEmail(event, ticketReservation, Locale.ENGLISH, null);
        }

        @Test
        void emailSentBecauseFlagIsSetToFalse() {
            when(sendReservationEmailIfNecessary.getValueAsBooleanOrDefault()).thenReturn(false);
            finalizer.sendConfirmationEmailIfNecessary(ticketReservation, List.of(ticket), event, Locale.ENGLISH, null);
            verify(reservationHelper).sendConfirmationEmail(event, ticketReservation, Locale.ENGLISH, null);
        }

        @Test
        void emailSentBecauseTicketIsNotSent() {
            when(sendReservationEmailIfNecessary.getValueAsBooleanOrDefault()).thenReturn(true);
            when(sendTickets.getValueAsBooleanOrDefault()).thenReturn(false);
            finalizer.sendConfirmationEmailIfNecessary(ticketReservation, List.of(ticket), event, Locale.ENGLISH, null);
            verify(reservationHelper).sendConfirmationEmail(event, ticketReservation, Locale.ENGLISH, null);
        }

        @Test
        void emailNOTSentBecauseFlagIsSetToTrue() {
            when(sendReservationEmailIfNecessary.getValueAsBooleanOrDefault()).thenReturn(true);
            when(sendTickets.getValueAsBooleanOrDefault()).thenReturn(true);
            finalizer.sendConfirmationEmailIfNecessary(ticketReservation, List.of(ticket), event, Locale.ENGLISH, null);
            verify(reservationHelper, never()).sendConfirmationEmail(event, ticketReservation, Locale.ENGLISH, null);
        }

        @Test
        void emailNotSentBecauseReservationNotFinalized() {
            when(metadata.isFinalized()).thenReturn(false);
            when(sendReservationEmailIfNecessary.getValueAsBooleanOrDefault()).thenReturn(true);
            when(sendTickets.getValueAsBooleanOrDefault()).thenReturn(true);
            assertThrows(IncompatibleStateException.class, () -> finalizer.sendConfirmationEmailIfNecessary(ticketReservation, List.of(ticket), event, Locale.ENGLISH, null));
            verify(reservationHelper, never()).sendConfirmationEmail(event, ticketReservation, Locale.ENGLISH, null);
        }
    }
}