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
import alfio.extension.Extension;
import alfio.extension.ExtensionService;
import alfio.extension.ExtensionUtils;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.ExtensionCapabilitySummary;
import alfio.model.TicketCategory;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.modification.UploadBase64FileModification;
import alfio.repository.EventRepository;
import alfio.repository.ExtensionRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.BaseIntegrationTest;
import alfio.util.ClockProvider;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static alfio.manager.support.extension.ExtensionCapability.*;
import static alfio.test.util.IntegrationTestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
@Transactional
class ExtensionManagerIntegrationTest {

    @Autowired
    private ExtensionManager extensionManager;
    @Autowired
    private ExtensionService extensionService;
    @Autowired
    private ExtensionRepository extensionRepository;
    @Autowired
    private ConfigurationRepository configurationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AuthorityRepository authorityRepository;
    @Autowired
    private FileUploadManager fileUploadManager;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserManager userManager;
    @Autowired
    private EventManager eventManager;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private Event event;

    @BeforeEach
    void init() throws Exception {
        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);
        initAdminUser(userRepository, authorityRepository);
        UploadBase64FileModification toInsert = new UploadBase64FileModification();
        toInsert.setFile(BaseIntegrationTest.ONE_PIXEL_BLACK_GIF);
        toInsert.setName("image.gif");
        toInsert.setType("image/gif");
        fileUploadManager.insertFile(toInsert);

        //create test event
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()).minusDays(1), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()).plusDays(1), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> eventAndUser = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);

        event = eventAndUser.getLeft();

        // insert extension
        String script;
        try(var input = getClass().getResourceAsStream("/rhino-scripts/capabilities.js")) {
            script = StreamUtils.copyToString(input, StandardCharsets.UTF_8);
        }
        extensionService.createOrUpdate(null, null, new Extension("-", "test", script, true));
    }

    @Test
    void capabilitiesSaved() {
        var extension = extensionRepository.getSingle("-", "test").orElseThrow();
        assertEquals(List.of(CREATE_VIRTUAL_ROOM.name(), CREATE_GUEST_LINK.name()), extension.getExtensionMetadata().getCapabilities());
        assertFalse(extension.getExtensionMetadata().getCapabilityDetails().isEmpty());
        var results = jdbcTemplate.queryForList("select * from extension_capabilities", Map.of());
        assertEquals(2, results.size());
    }

    @Test
    void capabilitySupported() {
        assertTrue(extensionManager.isSupported(CREATE_GUEST_LINK, event));
        assertTrue(extensionManager.isSupported(CREATE_VIRTUAL_ROOM, event));
    }

    @Test
    void capabilityNotSupported() {
        assertFalse(extensionManager.isSupported(CREATE_ANONYMOUS_GUEST_LINK, event));
    }

    @Test
    void requestSupportedCapabilities() {
        var requested = Set.of(CREATE_VIRTUAL_ROOM, CREATE_ANONYMOUS_GUEST_LINK, CREATE_GUEST_LINK);
        assertEquals(Set.of(CREATE_VIRTUAL_ROOM, CREATE_GUEST_LINK), extensionManager.getSupportedCapabilities(requested, event).stream().map(ExtensionCapabilitySummary::getCapability).collect(Collectors.toSet()));
    }

    @ParameterizedTest
    @ValueSource(strings = { "room1", "room2" })
    void executeCapability(String selector) {
        var optionalResult = extensionManager.executeCapability(CREATE_VIRTUAL_ROOM, Map.of("selector", selector), event, String.class);
        assertTrue(optionalResult.isPresent());
        assertEquals("https://alf.io/"+selector, optionalResult.get());
    }

    @Test
    void executeCapabilityWithParameters() {
        var params = Map.of("firstName", "testName", "lastName", "testLastName", "email", "testEmail");
        var optionalResult = extensionManager.executeCapability(CREATE_GUEST_LINK, params, event, String.class);
        assertTrue(optionalResult.isPresent());
        var expectedParams = ExtensionUtils.base64UrlSafe("testName"+";"+"testLastName"+";"+"testEmail");
        assertEquals("https://alf.io?user="+expectedParams, optionalResult.get());
    }
}
