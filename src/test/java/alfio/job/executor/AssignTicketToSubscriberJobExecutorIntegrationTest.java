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
package alfio.job.executor;

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.controller.api.ControllerConfiguration;
import alfio.manager.*;
import alfio.manager.user.UserManager;
import alfio.model.*;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.modification.UploadBase64FileModification;
import alfio.model.system.AdminJobSchedule;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Role;
import alfio.model.user.User;
import alfio.repository.*;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.BaseIntegrationTest;
import alfio.util.ClockProvider;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static alfio.test.util.IntegrationTestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, ControllerConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
@Transactional
class AssignTicketToSubscriberJobExecutorIntegrationTest {

    private static final Map<String, String> DESCRIPTION = Collections.singletonMap("en", "desc");
    private static final String FIRST_CATEGORY_NAME = "default";

    private final EventManager eventManager;
    private final UserManager userManager;
    private final SubscriptionManager subscriptionManager;
    private final SubscriptionRepository subscriptionRepository;
    private final FileUploadManager fileUploadManager;
    private final ConfigurationRepository configurationRepository;
    private final OrganizationRepository organizationRepository;
    private final EventRepository eventRepository;
    private final TicketReservationRepository ticketReservationRepository;
    private final AssignTicketToSubscriberJobExecutor executor;
    private final AdminReservationRequestRepository adminReservationRequestRepository;
    private final AdminReservationRequestManager adminReservationRequestManager;
    private final UserRepository userRepository;
    private final AuthorityRepository authorityRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TicketRepository ticketRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final NotificationManager notificationManager;

    private Event event;
    private String userId;

    @Autowired
    AssignTicketToSubscriberJobExecutorIntegrationTest(EventManager eventManager,
                                                       UserManager userManager,
                                                       SubscriptionManager subscriptionManager,
                                                       SubscriptionRepository subscriptionRepository,
                                                       FileUploadManager fileUploadManager,
                                                       ConfigurationRepository configurationRepository,
                                                       OrganizationRepository organizationRepository,
                                                       EventRepository eventRepository,
                                                       TicketReservationRepository ticketReservationRepository,
                                                       AssignTicketToSubscriberJobExecutor executor,
                                                       AdminReservationRequestRepository adminReservationRequestRepository,
                                                       AdminReservationRequestManager adminReservationRequestManager,
                                                       UserRepository userRepository,
                                                       AuthorityRepository authorityRepository,
                                                       NamedParameterJdbcTemplate jdbcTemplate,
                                                       TicketRepository ticketRepository,
                                                       TicketCategoryRepository ticketCategoryRepository,
                                                       NotificationManager notificationManager) {
        this.eventManager = eventManager;
        this.userManager = userManager;
        this.subscriptionManager = subscriptionManager;
        this.subscriptionRepository = subscriptionRepository;
        this.fileUploadManager = fileUploadManager;
        this.configurationRepository = configurationRepository;
        this.organizationRepository = organizationRepository;
        this.eventRepository = eventRepository;
        this.ticketReservationRepository = ticketReservationRepository;
        this.executor = executor;
        this.adminReservationRequestRepository = adminReservationRequestRepository;
        this.adminReservationRequestManager = adminReservationRequestManager;
        this.userRepository = userRepository;
        this.authorityRepository = authorityRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.ticketRepository = ticketRepository;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.notificationManager = notificationManager;
    }

    @BeforeEach
    void setUp() {
        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);
        List<TicketCategoryModification> categories = List.of(
            new TicketCategoryModification(null, "hidden", TicketCategory.TicketAccessType.INHERIT, 2,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()).minusDays(1), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()).plusDays(1), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.ONE, true, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()),
            new TicketCategoryModification(null, FIRST_CATEGORY_NAME, TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()).minusDays(1), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()).plusDays(1), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty())
        );
        Pair<Event, String> eventAndUser = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        var uploadFileForm = new UploadBase64FileModification();
        uploadFileForm.setFile(BaseIntegrationTest.ONE_PIXEL_BLACK_GIF);
        uploadFileForm.setName("my-image.gif");
        uploadFileForm.setType("image/gif");
        String fileBlobId = fileUploadManager.insertFile(uploadFileForm);
        assertNotNull(fileBlobId);
        this.event = eventAndUser.getLeft();
        this.userId = eventAndUser.getRight();
        // init admin user
        userRepository.create(UserManager.ADMIN_USERNAME, "", "The", "Administrator", "admin@localhost", true, User.Type.INTERNAL, null, null);
        authorityRepository.create(UserManager.ADMIN_USERNAME, Role.ADMIN.getRoleName());
    }

    @AfterEach
    void tearDown() {
        try {
            eventManager.deleteEvent(event.getId(), userId);
        } catch(Exception ex) {
            //ignore exception because the transaction might be aborted
        }
    }

    @Test
    void process() {
        performTest(Map.of());
    }

    @Test
    void processOrganizationLevel() {
        performTest(Map.of(AssignTicketToSubscriberJobExecutor.ORGANIZATION_ID, event.getOrganizationId()));
    }

    @Test
    void processEventLevel() {
        performTest(Map.of(
            AssignTicketToSubscriberJobExecutor.ORGANIZATION_ID, event.getOrganizationId(),
            AssignTicketToSubscriberJobExecutor.EVENT_ID, event.getId()
        ));
    }

    private void performTest(Map<String, Object> metadata) {
        var adminRequest = new AdminJobSchedule(1L, "", ZonedDateTime.now(ClockProvider.clock()), AdminJobSchedule.Status.SCHEDULED, null, metadata, 1);
        int maxEntries = 2;
        var descriptorId = createSubscriptionDescriptor(event.getOrganizationId(), fileUploadManager, subscriptionManager, maxEntries);
        var descriptor = subscriptionRepository.findOne(descriptorId).orElseThrow();
        var subscriptionIdAndPin = confirmAndLinkSubscription(descriptor, event.getOrganizationId(), subscriptionRepository, ticketReservationRepository, maxEntries);
        subscriptionRepository.linkSubscriptionAndEvent(descriptorId, event.getId(), 0, event.getOrganizationId());
        // 1. check that subscription descriptor is not marked as "available" because it does not support ticket generation
        assertEquals(0, subscriptionRepository.loadAvailableSubscriptionsByEvent(null, null).size());
        // enable support for tickets generation
        assertEquals(1, jdbcTemplate.update("update subscription_descriptor set supports_tickets_generation = true where id = :id", Map.of("id", descriptorId)));

        // test different parameter combination. The following queries must all return the same result
        assertEquals(1, subscriptionRepository.loadAvailableSubscriptionsByEvent(null, null).size());
        assertEquals(1, subscriptionRepository.loadAvailableSubscriptionsByEvent(event.getId(), event.getOrganizationId()).size());
        assertEquals(1, subscriptionRepository.loadAvailableSubscriptionsByEvent(null, event.getOrganizationId()).size());
        assertEquals(1, subscriptionRepository.loadAvailableSubscriptionsByEvent(event.getId(), null).size());


        // 2. trigger job schedule with flag not active
        executor.process(adminRequest);
        assertEquals(1, subscriptionRepository.loadAvailableSubscriptionsByEvent(null, null).size());
        assertEquals(0, adminReservationRequestRepository.countPending());

        // 3. trigger job schedule with flag active
        configurationRepository.insert(ConfigurationKeys.GENERATE_TICKETS_FOR_SUBSCRIPTIONS.name(), "true", "");
        executor.process(adminRequest);
        assertEquals(1, subscriptionRepository.loadAvailableSubscriptionsByEvent(null, null).size());
        assertEquals(1, adminReservationRequestRepository.countPending());

        // trigger reservation processing
        var result = adminReservationRequestManager.processPendingReservations();
        assertEquals(1, result.getLeft()); //  1 success
        assertEquals(0, result.getRight()); // 0 failures
        assertEquals(0, subscriptionRepository.loadAvailableSubscriptionsByEvent(null, null).size());

        // check ticket
        var ticketUuid = jdbcTemplate.queryForObject("select uuid from ticket where event_id = :eventId and ext_reference = :ref",
            Map.of("eventId", event.getId(), "ref", subscriptionIdAndPin.getLeft() + "_auto"),
            String.class);
        assertNotNull(ticketUuid);

        // check category
        var ticket = ticketRepository.findByUUID(ticketUuid);
        assertEquals(Ticket.TicketStatus.ACQUIRED, ticket.getStatus());
        var category = ticketCategoryRepository.getByIdAndActive(ticket.getCategoryId());
        assertTrue(category.isPresent());
        assertEquals(FIRST_CATEGORY_NAME, category.get().getName());

        // check reservation
        var reservation = ticketReservationRepository.findReservationById(ticket.getTicketsReservationId());
        assertEquals(TicketReservation.TicketReservationStatus.COMPLETE, reservation.getStatus());
        assertEquals(PaymentProxy.ADMIN, reservation.getPaymentMethod());
        assertEquals(0, reservation.getFinalPriceCts());
        assertEquals(event.getVatStatus(), reservation.getVatStatus());

        // trigger email send
        int sent = notificationManager.sendWaitingMessages();
        assertTrue(sent > 0);
        var messagesPair = notificationManager.loadAllMessagesForPurchaseContext(event, null, null);
        assertEquals(1, messagesPair.getLeft());
        assertTrue(messagesPair.getRight().stream().allMatch(m -> m.getStatus() == EmailMessage.Status.SENT));
    }
}