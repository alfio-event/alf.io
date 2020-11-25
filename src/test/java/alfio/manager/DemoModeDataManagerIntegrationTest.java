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

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.manager.user.UserManager;
import alfio.model.TicketCategory;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.repository.EventRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.BaseIntegrationTest;
import alfio.util.ClockProvider;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static alfio.test.util.IntegrationTestUtil.AVAILABLE_SEATS;
import static alfio.test.util.IntegrationTestUtil.initEvent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST, Initializer.PROFILE_DEMO})
@Transactional
class DemoModeDataManagerIntegrationTest extends BaseIntegrationTest {
    private final ConfigurationRepository configurationRepository;
    private final OrganizationRepository organizationRepository;
    private final UserManager userManager;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final EventManager eventManager;
    private final EventRepository eventRepository;
    private final DemoModeDataManager demoModeDataManager;

    @Autowired
    DemoModeDataManagerIntegrationTest(ConfigurationRepository configurationRepository,
                                       OrganizationRepository organizationRepository,
                                       UserManager userManager,
                                       NamedParameterJdbcTemplate jdbcTemplate,
                                       EventManager eventManager,
                                       EventRepository eventRepository,
                                       DemoModeDataManager demoModeDataManager) {
        this.configurationRepository = configurationRepository;
        this.organizationRepository = organizationRepository;
        this.userManager = userManager;
        this.jdbcTemplate = jdbcTemplate;
        this.eventManager = eventManager;
        this.eventRepository = eventRepository;
        this.demoModeDataManager = demoModeDataManager;
    }

    @BeforeEach
    public void ensureConfiguration() {
        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);
    }

    @Test
    void deleteExpiredUsersAndEvents() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                Map.of("en", "desc"), BigDecimal.TEN, false, "", false, null,
                null, null, null, null, 0, null, null, AlfioMetadata.empty()));

        var eventAndUser = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        var event = eventAndUser.getLeft();
        var expirationDate = DateUtils.addDays(new Date(), -30);
        int updateResult = jdbcTemplate.update("update ba_user set user_type = 'DEMO', user_creation_time = :date where username = :username", Map.of("date", expirationDate, "username", eventAndUser.getRight()));
        assertEquals(1, updateResult);
        demoModeDataManager.cleanupForDemoMode();
        assertTrue(eventRepository.findOptionalById(event.getId()).isEmpty());

    }

    @Test
    void doNotDeleteNewUsers() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                Map.of("en", "desc"), BigDecimal.TEN, false, "", false, null,
                null, null, null, null, 0, null, null, AlfioMetadata.empty()));

        var eventAndUser = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        var event = eventAndUser.getLeft();
        demoModeDataManager.cleanupForDemoMode();
        assertTrue(eventRepository.findOptionalById(event.getId()).isPresent());
    }

}