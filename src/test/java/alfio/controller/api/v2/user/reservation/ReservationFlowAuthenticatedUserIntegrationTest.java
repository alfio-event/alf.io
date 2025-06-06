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
import alfio.controller.api.ControllerConfiguration;
import alfio.controller.api.v2.user.UserApiV2Controller;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.TicketCategory;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.system.ConfigurationKeys;
import alfio.model.user.User;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.AlfioIntegrationTest;
import alfio.util.BaseIntegrationTest;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static alfio.test.util.IntegrationTestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

@AlfioIntegrationTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, ControllerConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
class ReservationFlowAuthenticatedUserIntegrationTest extends BaseReservationFlowTest {

    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserManager userManager;
    @Autowired
    private UserApiV2Controller publicUserApiController;

    private static final Map<String, String> DESCRIPTION = Collections.singletonMap("en", "desc");
    private String publicUserName;


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
        publicUserName = UUID.randomUUID().toString();
        var userIdContainer = userRepository.create(publicUserName, UUID.randomUUID().toString(), "First", "Last", "email@example.org", true, User.Type.PUBLIC, null, "");
        return new ReservationFlowContext(eventAndUser.getLeft(), owner(eventAndUser.getRight()), null, null, publicUserName, userIdContainer.getKey(), true, false);
    }

    @BeforeEach
    public void init() {
        publicUserName = UUID.randomUUID().toString();
        userRepository.create(publicUserName, UUID.randomUUID().toString(), "First", "Last", "email@example.org", true, User.Type.PUBLIC, null, "");
        configurationRepository.insert(ConfigurationKeys.OPENID_PUBLIC_ENABLED.name(), "true", "Openid is enabled for this test");
    }

    @Test
    public void authenticatedUserTest() throws Exception {
        super.testBasicFlow(this::createContext);
    }

    @Override
    protected void performAdditionalTests(ReservationFlowContext reservationFlowContext) {
        var reservationsResponse = publicUserApiController.getUserReservations(reservationFlowContext.getPublicAuthentication());
        assertEquals(HttpStatus.OK, reservationsResponse.getStatusCode());
        assertNotNull(reservationsResponse.getBody());
        assertFalse(reservationsResponse.getBody().isEmpty());
        assertEquals(1, reservationsResponse.getBody().size());

        var event = reservationFlowContext.event;
        BaseIntegrationTest.testTransferEventToAnotherOrg(event.getId(), event.getOrganizationId(), reservationFlowContext.userId, jdbcTemplate);
    }
}
