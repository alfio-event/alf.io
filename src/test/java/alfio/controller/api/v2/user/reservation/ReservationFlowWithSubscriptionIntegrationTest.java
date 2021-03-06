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
package alfio.controller.api.v2.user.reservation;

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.controller.IndexController;
import alfio.controller.api.ControllerConfiguration;
import alfio.controller.api.admin.AdditionalServiceApiController;
import alfio.controller.api.admin.CheckInApiController;
import alfio.controller.api.admin.EventApiController;
import alfio.controller.api.admin.UsersApiController;
import alfio.controller.api.v1.AttendeeApiController;
import alfio.controller.api.v2.InfoApiController;
import alfio.controller.api.v2.TranslationsApiController;
import alfio.controller.api.v2.user.EventApiV2Controller;
import alfio.controller.api.v2.user.ReservationApiV2Controller;
import alfio.controller.api.v2.user.TicketApiV2Controller;
import alfio.extension.ExtensionService;
import alfio.manager.*;
import alfio.manager.user.UserManager;
import alfio.model.AllocationStatus;
import alfio.model.Event;
import alfio.model.PriceContainer;
import alfio.model.TicketCategory;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.SubscriptionDescriptorModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.modification.UploadBase64FileModification;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.model.transaction.PaymentProxy;
import alfio.repository.*;
import alfio.repository.audit.ScanAuditRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import static alfio.test.util.IntegrationTestUtil.AVAILABLE_SEATS;
import static alfio.test.util.IntegrationTestUtil.initEvent;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, ControllerConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
@Transactional
public class ReservationFlowWithSubscriptionIntegrationTest extends BaseReservationFlowTest {

    private final OrganizationRepository organizationRepository;
    private final EventManager eventManager;
    private final EventRepository eventRepository;
    private final UserManager userManager;
    private final ClockProvider clockProvider;
    private final SubscriptionManager subscriptionManager;
    private final SubscriptionRepository subscriptionRepository;
    private final FileUploadManager fileUploadManager;
    private final TicketReservationRepository ticketReservationRepository;

    private static final Map<String, String> DESCRIPTION = Collections.singletonMap("en", "desc");

    private ReservationFlowContext context;

    @Autowired
    public ReservationFlowWithSubscriptionIntegrationTest(OrganizationRepository organizationRepository,
                                                          EventManager eventManager,
                                                          EventRepository eventRepository,
                                                          UserManager userManager,
                                                          ClockProvider clockProvider,
                                                          ConfigurationRepository configurationRepository,
                                                          EventStatisticsManager eventStatisticsManager,
                                                          TicketCategoryRepository ticketCategoryRepository,
                                                          TicketReservationRepository ticketReservationRepository,
                                                          EventApiController eventApiController,
                                                          TicketRepository ticketRepository,
                                                          TicketFieldRepository ticketFieldRepository,
                                                          AdditionalServiceApiController additionalServiceApiController,
                                                          SpecialPriceTokenGenerator specialPriceTokenGenerator,
                                                          SpecialPriceRepository specialPriceRepository,
                                                          CheckInApiController checkInApiController,
                                                          AttendeeApiController attendeeApiController,
                                                          UsersApiController usersApiController,
                                                          ScanAuditRepository scanAuditRepository,
                                                          AuditingRepository auditingRepository,
                                                          AdminReservationManager adminReservationManager,
                                                          TicketReservationManager ticketReservationManager,
                                                          InfoApiController infoApiController,
                                                          TranslationsApiController translationsApiController,
                                                          EventApiV2Controller eventApiV2Controller,
                                                          ReservationApiV2Controller reservationApiV2Controller,
                                                          TicketApiV2Controller ticketApiV2Controller,
                                                          IndexController indexController,
                                                          NamedParameterJdbcTemplate jdbcTemplate,
                                                          ExtensionLogRepository extensionLogRepository,
                                                          ExtensionService extensionService,
                                                          PollRepository pollRepository,
                                                          NotificationManager notificationManager,
                                                          SubscriptionManager subscriptionManager,
                                                          SubscriptionRepository subscriptionRepository,
                                                          FileUploadManager fileUploadManager) {
        super(configurationRepository,
            eventManager,
            eventRepository,
            eventStatisticsManager,
            ticketCategoryRepository,
            ticketReservationRepository,
            eventApiController,
            ticketRepository,
            ticketFieldRepository,
            additionalServiceApiController,
            specialPriceTokenGenerator,
            specialPriceRepository,
            checkInApiController,
            attendeeApiController,
            usersApiController,
            scanAuditRepository,
            auditingRepository,
            adminReservationManager,
            ticketReservationManager,
            infoApiController,
            translationsApiController,
            eventApiV2Controller,
            reservationApiV2Controller,
            ticketApiV2Controller,
            indexController,
            jdbcTemplate,
            extensionLogRepository,
            extensionService,
            pollRepository,
            clockProvider,
            notificationManager);
        this.organizationRepository = organizationRepository;
        this.eventManager = eventManager;
        this.eventRepository = eventRepository;
        this.userManager = userManager;
        this.clockProvider = clockProvider;
        this.subscriptionManager = subscriptionManager;
        this.subscriptionRepository = subscriptionRepository;
        this.fileUploadManager = fileUploadManager;
        this.ticketReservationRepository = ticketReservationRepository;
    }

    @BeforeEach
    void createContext() {
        List<TicketCategoryModification> categories = Arrays.asList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()).minusDays(1), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()).plusDays(1), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()),
            new TicketCategoryModification(null, "hidden", TicketCategory.TicketAccessType.INHERIT, 2,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()).minusDays(1), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()).plusDays(1), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.ONE, true, "", true, URL_CODE_HIDDEN, null, null, null, null, 0, null, null, AlfioMetadata.empty())
        );
        Pair<Event, String> eventAndUser = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        var uploadFileForm = new UploadBase64FileModification();
        uploadFileForm.setFile(BaseIntegrationTest.ONE_PIXEL_BLACK_GIF);
        uploadFileForm.setName("my-image.gif");
        uploadFileForm.setType("image/gif");
        String fileBlobId = fileUploadManager.insertFile(uploadFileForm);
        assertNotNull(fileBlobId);

        // create subscription descriptor

        var event = eventAndUser.getLeft();
        var subscriptionModification = new SubscriptionDescriptorModification(null,
            Map.of("en", "title"),
            Map.of("en", "description"),
            42,
            ZonedDateTime.now(ClockProvider.clock()),
            null,
            BigDecimal.TEN,
            new BigDecimal("7.7"),
            PriceContainer.VatStatus.INCLUDED,
            "CHF",
            false,
            event.getOrganizationId(),
            42,
            SubscriptionDescriptor.SubscriptionValidityType.CUSTOM,
            null,
            null,
            ZonedDateTime.now(ClockProvider.clock()).minusDays(1),
            ZonedDateTime.now(ClockProvider.clock()).plusDays(42),
            SubscriptionDescriptor.SubscriptionUsageType.ONCE_PER_EVENT,
            "https://example.org",
            null,
            fileBlobId,
            List.of(PaymentProxy.STRIPE),
            ZoneId.of("Europe/Zurich"));

        var descriptorId = subscriptionManager.createSubscriptionDescriptor(subscriptionModification).orElseThrow();
        var subscriptionId = subscriptionRepository.selectFreeSubscription(descriptorId).orElseThrow();
        var subscriptionReservationId = UUID.randomUUID().toString();
        ticketReservationRepository.createNewReservation(subscriptionReservationId, ZonedDateTime.now(clockProvider.getClock()), new Date(), null, "en", null, new BigDecimal("7.7"), true, "CHF", event.getOrganizationId());
        subscriptionRepository.bindSubscriptionToReservation(subscriptionReservationId, AllocationStatus.PENDING, subscriptionId);
        subscriptionRepository.updateSubscriptionStatus(subscriptionReservationId, AllocationStatus.ACQUIRED, "Test", "Mc Test", "tickettest@test.com");
        var subscription = subscriptionRepository.findSubscriptionById(subscriptionId);
        this.context = new ReservationFlowContext(event, eventAndUser.getRight() + "_owner", subscriptionId, subscription.getPin());
    }

    @AfterEach
    void deleteEvent() {
        eventManager.deleteEvent(context.event.getId(), context.userId);
    }

    @Test
    public void inPersonEventWithSubscriptionUsingID() {
        var modifiedContext = new ReservationFlowContext(context.event, context.userId, context.subscriptionId, null);
        super.testAddSubscription(modifiedContext);
    }

    @Test
    public void inPersonEventWithSubscriptionUsingPin() {
        super.testAddSubscription(context);
    }
}
