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
import alfio.model.Event;
import alfio.model.TicketCategory;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.repository.*;
import alfio.repository.audit.ScanAuditRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.util.BaseIntegrationTest;
import alfio.util.ClockProvider;
import org.apache.commons.lang3.tuple.Pair;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static alfio.test.util.IntegrationTestUtil.AVAILABLE_SEATS;
import static alfio.test.util.IntegrationTestUtil.initEvent;

@SpringBootTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, ControllerConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
@Transactional
class ReservationFlowIntegrationTest extends BaseReservationFlowTest {

    private final OrganizationRepository organizationRepository;
    private final UserManager userManager;

    private static final Map<String, String> DESCRIPTION = Collections.singletonMap("en", "desc");

    @Autowired
    public ReservationFlowIntegrationTest(OrganizationRepository organizationRepository,
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
                                          UserRepository userRepository) {
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
            notificationManager,
            userRepository);
        this.organizationRepository = organizationRepository;
        this.userManager = userManager;
    }

    private ReservationFlowContext createContext() {
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
        return new ReservationFlowContext(eventAndUser.getLeft(), eventAndUser.getRight() + "_owner");
    }

    @Test
    void inPersonEvent() throws Exception {
        super.testBasicFlow(this::createContext);
    }

    @Override
    protected void performAdditionalTests(ReservationFlowContext context) {
        var event = context.event;
        BaseIntegrationTest.testTransferEventToAnotherOrg(event.getId(), event.getOrganizationId(), context.userId, jdbcTemplate);
    }
}
