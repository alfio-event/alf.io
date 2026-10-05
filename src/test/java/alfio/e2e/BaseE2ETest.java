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
package alfio.e2e;

import alfio.config.Initializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Each test runs on its own browser session, against its own event. The event is created and published
 * through the admin console before the test, and deleted afterwards.
 * <p>
 * For testing with browserstack you need to set the following ENV Variables:
 * ALFIO_RUN_E2E: true
 * BROWSERSTACK_USERNAME
 * BROWSERSTACK_ACCESS_KEY
 * BROWSERSTACK_PROJECT_NAME
 * BROWSERSTACK_BUILD_NAME
 * E2E_SERVER_URL
 * E2E_ADMIN_USERNAME
 * E2E_ADMIN_PASSWORD
 * E2E_ORGANIZATION (optional, defaults to the first organization available to the admin user)
 * E2E_BROWSER: chrome
 * <p>
 * The organization must have Stripe and Bank Transfer configured.
 */
@ContextConfiguration(classes = { E2EConfiguration.class })
@ActiveProfiles(value = {Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST, "e2e"})
@EnabledIfEnvironmentVariable(named = "ALFIO_RUN_E2E", matches = "true")
@SpringBootTest
@Timeout(value = 10L, unit = TimeUnit.MINUTES)
abstract class BaseE2ETest {

    static final boolean CI_RUN = "true".equals(System.getenv("E2E_CI_RUN"));
    private static final List<String> PAYMENT_METHODS = List.of("Stripe: Credit cards", "On site (cash) payment", "Offline payment");

    @Autowired
    private BrowserWebDriver.Factory browserWebDriverFactory;
    @Autowired
    private Environment environment;

    private BrowserWebDriver browserWebDriver;
    private boolean eventSubmitted;

    protected String slug;
    protected AdminConsole adminConsole;
    protected ReservationFlow reservationFlow;

    @BeforeEach
    void setUpEvent(TestInfo testInfo) {
        var serverBaseUrl = environment.getRequiredProperty("e2e.server.url");
        slug = "e2e-" + UUID.randomUUID();
        browserWebDriver = browserWebDriverFactory.create(getClass().getSimpleName() + "." + testInfo.getTestMethod().orElseThrow().getName());
        adminConsole = new AdminConsole(browserWebDriver,
            serverBaseUrl,
            environment.getRequiredProperty("e2e.admin.username"),
            environment.getRequiredProperty("e2e.admin.password"),
            environment.getProperty("e2e.organization"));
        reservationFlow = new ReservationFlow(browserWebDriver,
            serverBaseUrl + "/event/" + slug,
            environment.getProperty("e2e.email", "noreply@example.org"));
        //
        // the organizer sets up the event
        adminConsole.login();
        eventSubmitted = true;
        adminConsole.createEvent(eventDefinition());
        adminConsole.publishEvent(slug);
        adminConsole.logout();
    }

    /**
     * If the test has already failed, JUnit reports the errors thrown here as suppressed,
     * so the original failure is not hidden.
     */
    @AfterEach
    void deleteEvent() {
        if (browserWebDriver == null) {
            return;
        }
        try {
            if (eventSubmitted) {
                adminConsole.login();
                adminConsole.deleteEvent(slug);
            }
        } finally {
            browserWebDriver.driver.quit();
        }
    }

    /**
     * Short identifier, unique to the current test, for the data created by the test (messages, notes...)
     */
    protected String uniqueId() {
        return slug.substring(4, 12);
    }

    private AdminConsole.EventDefinition eventDefinition() {
        return new AdminConsole.EventDefinition(slug,
            "Event Name",
            "Pollegio 6742 Switzerland",
            "text description",
            "https://alf.io",
            "https://alf.io",
            "https://alf.io",
            10,
            "10",
            "CHF",
            "7.7",
            true,
            PAYMENT_METHODS,
            "Standard");
    }
}
