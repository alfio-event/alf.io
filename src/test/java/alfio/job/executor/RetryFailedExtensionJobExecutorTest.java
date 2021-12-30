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
import alfio.extension.Extension;
import alfio.extension.ExtensionService;
import alfio.extension.ScriptingExecutionService;
import alfio.manager.EventManager;
import alfio.manager.system.AdminJobManager;
import alfio.manager.system.AdminJobManagerInvoker;
import alfio.manager.user.UserManager;
import alfio.model.TicketCategory;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.system.AdminJobSchedule;
import alfio.repository.EventRepository;
import alfio.repository.system.AdminJobQueueRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.ClockProvider;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static alfio.test.util.IntegrationTestUtil.AVAILABLE_SEATS;
import static alfio.test.util.IntegrationTestUtil.initEvent;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, ControllerConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
@Transactional
class RetryFailedExtensionJobExecutorTest {

    private static final Map<String, String> DESCRIPTION = Collections.singletonMap("en", "desc");
    private static final String FIRST_CATEGORY_NAME = "default";

    private final EventManager eventManager;
    private final UserManager userManager;
    private final ExtensionService extensionService;
    private final ConfigurationRepository configurationRepository;
    private final OrganizationRepository organizationRepository;
    private final EventRepository eventRepository;
    private final AdminJobQueueRepository adminJobQueueRepository;
    private final AdminJobManager adminJobManager;

    @Autowired
    RetryFailedExtensionJobExecutorTest(EventManager eventManager,
                                        UserManager userManager,
                                        ExtensionService extensionService,
                                        ConfigurationRepository configurationRepository,
                                        OrganizationRepository organizationRepository,
                                        EventRepository eventRepository,
                                        AdminJobQueueRepository adminJobQueueRepository,
                                        AdminJobManager adminJobManager) {
        this.eventManager = eventManager;
        this.userManager = userManager;
        this.configurationRepository = configurationRepository;
        this.organizationRepository = organizationRepository;
        this.eventRepository = eventRepository;
        this.extensionService = extensionService;
        this.adminJobQueueRepository = adminJobQueueRepository;
        this.adminJobManager = adminJobManager;
    }

    @BeforeEach
    void setUp() throws Exception {
        registerExtension("async-extension-failing.js", "asyncExtension", false);
        registerExtension("sync-extension.js", "syncExtension", false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void recoverFromFailedExecution() throws Exception {
        buildEvent();
        // after the failed extension we should have a retry schedule
        var jobs = adminJobQueueRepository.loadAll();
        assertEquals(1, jobs.size());
        var job = jobs.get(0);
        var metadata = job.getMetadata();
        assertTrue(job.getRequestTimestamp().isAfter(ZonedDateTime.now(ClockProvider.clock())));
        assertEquals(1, job.getAttempts());
        assertEquals(AdminJobSchedule.Status.SCHEDULED, job.getStatus());
        assertEquals("-", metadata.get(ScriptingExecutionService.EXTENSION_PATH));
        assertEquals("asyncExtension", metadata.get(ScriptingExecutionService.EXTENSION_NAME));
        assertTrue(metadata.containsKey(ScriptingExecutionService.EXTENSION_PARAMS));
        var params = (Map<String, Object>) metadata.get(ScriptingExecutionService.EXTENSION_PARAMS);
        assertTrue(params.containsKey("eventId"));
        assertNotNull(params.get("eventId"));
        assertTrue(((Integer) params.get("eventId")) > 0);
        assertFalse(params.containsKey(ScriptingExecutionService.EXTENSION_CONFIGURATION_PARAMETERS));

        var invoker = new AdminJobManagerInvoker(adminJobManager);
        // try to run the jobs, this should still fail
        var expectedDate = ZonedDateTime.now(ClockProvider.clock()).plusSeconds(4).minus(100, ChronoUnit.MILLIS);
        invoker.invokeProcessPendingExtensionRetry(ZonedDateTime.now(ClockProvider.clock()).plus(2001L, ChronoUnit.MILLIS));
        jobs = adminJobQueueRepository.loadAll();
        job = jobs.get(0);
        assertEquals(2, job.getAttempts());
        assertTrue(job.getRequestTimestamp().isAfter(expectedDate));
        assertEquals(AdminJobSchedule.Status.SCHEDULED, job.getStatus());

        // fix the script
        registerExtension("async-extension-success.js", "asyncExtension", true);

        // trigger the job again
        adminJobQueueRepository.scheduleRetry(job.getId(), expectedDate);
        invoker.invokeProcessPendingExtensionRetry(expectedDate.plus(1L, ChronoUnit.MILLIS));
        jobs = adminJobQueueRepository.loadAll();
        job = jobs.get(0);
        assertEquals(3, job.getAttempts());
        assertEquals(AdminJobSchedule.Status.EXECUTED, job.getStatus());
    }

    private void buildEvent() {
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
        initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
    }

    private void registerExtension(String fileName, String extensionName, boolean override) throws Exception {
        try (var extensionInputStream = requireNonNull(getClass().getResourceAsStream("/retry-extension/" + fileName))) {
            var extensionStream = String.join("\n", IOUtils.readLines(new InputStreamReader(extensionInputStream, StandardCharsets.UTF_8)));
            extensionService.createOrUpdate(override ? "-" : null, override ? extensionName : null, new Extension("-", extensionName, extensionStream, true));
        }
    }
}