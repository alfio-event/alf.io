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
import alfio.model.Event;
import alfio.model.TicketCategory;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.user.User;
import alfio.repository.EventRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.util.BaseIntegrationTest;
import alfio.util.ClockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static alfio.test.util.IntegrationTestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
@Transactional
public class EventNameManagerIntegrationTest  extends BaseIntegrationTest {

    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private EventManager eventManager;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserManager userManager;
    @Autowired
    private ClockProvider clockProvider;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;
    @Autowired
    private EventNameManager eventNameManager;

    private Event event;
    private Integer secondOrgId;

    @BeforeEach
    void setUp() {

        // ensure that the current user is not a superuser
        assertEquals(Boolean.FALSE, jdbcTemplate.queryForObject("select usesuper from pg_user where usename = CURRENT_USER",
                EmptySqlParameterSource.INSTANCE,
                Boolean.class));

        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        var eventAndUsername = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        event = eventAndUsername.getKey();
        String username = UUID.randomUUID().toString();
        userRepository.create(username, "password", "", "", "", true, User.Type.INTERNAL, null, "");
        secondOrgId = BaseIntegrationTest.createNewOrg(username, jdbcTemplate);
    }

    @Test
    void testValidSlug() {
        assertEquals(Boolean.TRUE, jdbcTemplate.queryForObject("select set_config('alfio.checkRowAccess', 'true', true)", EmptySqlParameterSource.INSTANCE, Boolean.class));
        assertEquals(String.valueOf(secondOrgId), jdbcTemplate.queryForObject("select set_config('alfio.currentUserOrgs', :orgs, true)", new MapSqlParameterSource("orgs", String.valueOf(secondOrgId)), String.class));
        assertTrue(eventNameManager.isUnique(event.getShortName() + "1"));
    }

    @Test
    void testAlreadyUsedSlug() {
        assertEquals(Boolean.TRUE, jdbcTemplate.queryForObject("select set_config('alfio.checkRowAccess', 'true', true)", EmptySqlParameterSource.INSTANCE, Boolean.class));
        assertEquals(String.valueOf(secondOrgId), jdbcTemplate.queryForObject("select set_config('alfio.currentUserOrgs', :orgs, true)", new MapSqlParameterSource("orgs", String.valueOf(secondOrgId)), String.class));
        assertFalse(eventNameManager.isUnique(event.getShortName()));
    }
}
