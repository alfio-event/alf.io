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
import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.net.URL;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static alfio.e2e.E2EUtils.*;
import static java.util.Map.entry;
import static org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated;

/**
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
 *
 * The event is created, published and deleted through the admin console, using the same browser.
 * The organization must have Stripe configured.
 */
@ContextConfiguration(classes = { NormalFlowE2ETest.E2EConfiguration.class })
@ActiveProfiles(value = {Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST, "e2e"})
@EnabledIfEnvironmentVariable(named = "ALFIO_RUN_E2E", matches = "true")
@SpringBootTest
class NormalFlowE2ETest {

    private static final Logger LOGGER = LoggerFactory.getLogger(NormalFlowE2ETest.class);
    private static final boolean CI_RUN = "true".equals(System.getenv("E2E_CI_RUN"));
    private static final List<String> PAYMENT_METHODS = List.of("Stripe: Credit cards", "On site (cash) payment");

    private String serverBaseUrl;
    private String eventUrl;
    private String slug;

    private final List<BrowserWebDriver> webDrivers;
    private final Environment environment;

    @Autowired
    public NormalFlowE2ETest(List<BrowserWebDriver> webDrivers,
                             Environment environment) {
        this.webDrivers = webDrivers;
        this.environment = environment;
    }

    @BeforeEach
    void init() {
        serverBaseUrl = environment.getRequiredProperty("e2e.server.url");
        slug = "e2e-" + UUID.randomUUID();
        eventUrl = serverBaseUrl + "/event/" + slug;
    }

    private AdminConsole adminConsole(BrowserWebDriver browserWebDriver) {
        return new AdminConsole(browserWebDriver,
            serverBaseUrl,
            environment.getRequiredProperty("e2e.admin.username"),
            environment.getRequiredProperty("e2e.admin.password"),
            environment.getProperty("e2e.organization"));
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

    @Test
    @Timeout(value = 15L, unit = TimeUnit.MINUTES)
    void testFlow() throws InterruptedException {
        for(var browserWebDriver : webDrivers) {
            var driver = browserWebDriver.driver;
            var adminConsole = adminConsole(browserWebDriver);
            boolean eventSubmitted = false;
            boolean completed = false;
            try {
                // the organizer sets up the event
                adminConsole.login();
                eventSubmitted = true;
                adminConsole.createEvent(eventDefinition());
                adminConsole.publishEvent(slug);
                adminConsole.logout();
                //
                // the attendee buys a ticket
                driver.navigate().to(eventUrl);
                WebDriverWait wait = new WebDriverWait(driver, Duration.of(30, ChronoUnit.SECONDS));
                wait.until(presenceOfElementLocated(By.cssSelector("div.markdown-content")));
                page1TicketSelection(browserWebDriver);
                //wait until page is loaded
                wait.until(presenceOfElementLocated(By.cssSelector("h2[translate='reservation-page.your-details']")));
                //
                page2ContactDetails(browserWebDriver, wait);
                //wait until page is loaded
                wait.until(presenceOfElementLocated(By.cssSelector("h2[translate='reservation-page.title']")));
                //
                page3Payment(browserWebDriver, wait);
                WebElement fourthPageElem = new WebDriverWait(driver, Duration.of(30, ChronoUnit.SECONDS)).until(presenceOfElementLocated(By.cssSelector("div.attendees-data")));
                Assertions.assertNotNull(fourthPageElem);
                completed = true;
            } finally {
                try {
                    if (eventSubmitted) {
                        deleteEvent(adminConsole, completed);
                    }
                } finally {
                    driver.quit();
                }
            }
        }
    }

    private void deleteEvent(AdminConsole adminConsole, boolean flowCompleted) {
        try {
            adminConsole.login();
            adminConsole.deleteEvent(slug);
        } catch (RuntimeException e) {
            if (flowCompleted) {
                throw e;
            }
            // don't hide the original failure
            LOGGER.error("cannot delete event {}", slug, e);
        }
    }

    private void page1TicketSelection(BrowserWebDriver browserWebDriver) {
        // select 1 ticket
        WebElement dropdown = browserWebDriver.driver.findElement(By.cssSelector("select[formcontrolname=amount]"));
        dropdown.findElement(By.xpath("//option[. = '1']")).click();
        //
        // click continue button, submit form
        browserWebDriver.driver.findElement(By.id("show-event-continue")).sendKeys(Keys.RETURN);

    }

    private void page2ContactDetails(BrowserWebDriver browserWebDriver, WebDriverWait wait) {
        var driver = browserWebDriver.driver;
        driver.findElement(By.id("first-name")).sendKeys("Test");
        driver.findElement(By.id("last-name")).sendKeys("McTest");
        driver.findElement(By.id("email")).sendKeys(environment.getProperty("e2e.email", "noreply@example.org"));

        var invoiceRequested = driver.findElements(By.cssSelector("label[for=invoiceRequested]"));
        if(CollectionUtils.isNotEmpty(invoiceRequested)) {
            // select "I need an invoice for this reservation"
            selectElement(invoiceRequested.getFirst(), browserWebDriver);
        }

        scrollTo(driver, driver.findElement(By.id("invoiceTypePrivate"))).click();
        driver.findElement(By.id("billingAddressLine1")).sendKeys("Bahnhofstrasse 1");
        driver.findElement(By.id("billingAddressZip")).sendKeys("8000");
        driver.findElement(By.id("billingAddressCity")).sendKeys("Zürich");


        Actions actions = new Actions(driver);
        actions.moveToElement(driver.findElement(By.cssSelector("ng-select[formcontrolname='vatCountryCode']")));
        clickWithJs(driver, driver.findElement(By.cssSelector("ng-select[formcontrolname='vatCountryCode']")));

        driver.findElement(By.cssSelector("ng-select[formcontrolname='vatCountryCode'] input[id=vatCountry]")).sendKeys("switzerland");
        wait.until(presenceOfElementLocated(By.cssSelector("ng-select[formcontrolname='vatCountryCode'] ng-dropdown-panel div[role=option]")));
        selectElement(driver.findElement(By.cssSelector("ng-select[formcontrolname='vatCountryCode'] ng-dropdown-panel div[role=option]")), browserWebDriver, Keys.TAB);

        Assertions.assertTrue(driver.findElement(By.cssSelector("ng-select[formcontrolname='vatCountryCode'] div.ng-value")).getText().contains("(CH)"));

        // insert data for attendee:
        // first&last name + email are already filled
        //we set the value only for the mandatory elements
        driver.findElements(By.cssSelector("app-additional-field label")).stream().filter(e -> e.getText().endsWith("*")).forEach(e -> {
            driver.findElement(By.id(e.getAttribute("for"))).sendKeys("A");
        });


        // submit
        driver.findElement(By.cssSelector("button[type=submit][translate='reservation-page.continue']")).sendKeys(Keys.RETURN);
    }


    private void page3Payment(BrowserWebDriver browserWebDriver, WebDriverWait wait) throws InterruptedException {
        var driver = browserWebDriver.driver;
        selectElement(driver.findElement(By.id("CREDIT_CARD-label")), browserWebDriver);
        wait.until(presenceOfElementLocated(By.cssSelector("#card-element iframe")));
        driver.findElement(By.id("card-name")).sendKeys("Test McTest");
        driver.switchTo().frame(By.cssSelector("#card-element iframe").findElement(driver));
        wait.until(presenceOfElementLocated(By.name("cardnumber")));
        var cardNumberElement = driver.findElement(By.name("cardnumber"));
        sendSlowInput(cardNumberElement, browserWebDriver, "4000000400000008".chars().mapToObj(Character::toString).toArray(String[]::new));
        sendSlowInput(driver.findElement(By.name("exp-date")),browserWebDriver, "12", "30");
        driver.findElement(By.name("cvc")).sendKeys("123");
        //driver.findElement(By.name("postal")).sendKeys("65000");
        driver.switchTo().defaultContent();
        driver.findElements(By.id("privacy-policy-label")).forEach(e -> selectElement(e, browserWebDriver));
        selectElement(driver.findElement(By.id("terms-conditions-label")), browserWebDriver);

        //submit
        driver.findElement(By.cssSelector(".btn-success")).sendKeys(Keys.RETURN);
    }


    private void sendSlowInput(WebElement element, BrowserWebDriver browserWebDriver, CharSequence... strings) throws InterruptedException {
        if(browserWebDriver.browser == BrowserWebDriver.Browser.SAFARI || browserWebDriver.browser == BrowserWebDriver.Browser.IE) {
            for (var str : strings) {
                element.sendKeys(str);
                Thread.sleep(50L);
            }
        } else {
            element.sendKeys(String.join(" ", strings));
            Thread.sleep(50L);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class E2EConfiguration {

        private static WebDriver buildRemoteDriver(URL url,
                                                   String os,
                                                   String osVersion,
                                                   String browser,
                                                   String browserVersion,
                                                   String profileName) {
            DesiredCapabilities caps = new DesiredCapabilities();
            caps.setCapability("browserName", browser);
            caps.setCapability("bstack:options", Map.ofEntries(
                entry("os", os),
                entry("osVersion", osVersion),
                entry("browserVersion", browserVersion),
                entry("buildName", profileName),
                entry("consoleLogs", "errors"),
                entry("networkLogs", "true"),
                entry("seleniumVersion", "4.16.1"),
                entry("idleTimeout", "180")
            ));
            return new RemoteWebDriver(url, caps);
        }

        private static String browserStackUrl(Environment env) {
            return "https://"
                + env.getRequiredProperty("browserstack.username")
                + ":"
                + env.getRequiredProperty("browserstack.access.key")
                + "@hub-cloud.browserstack.com/wd/hub";
        }


        private BrowserWebDriver build(String browser, URL url, String githubBuildNumber) {
            return switch (browser) {
                case "chrome" ->
                        new BrowserWebDriver(BrowserWebDriver.Browser.CHROME, buildRemoteDriver(url, "Windows", "10", "Chrome", "latest", "testFlowChrome" + githubBuildNumber));
                case "firefox" ->
                        new BrowserWebDriver(BrowserWebDriver.Browser.FIREFOX, buildRemoteDriver(url, "Windows", "10", "Firefox", "latest", "testFlowFirefox" + githubBuildNumber));
                case "safari" ->
                        new BrowserWebDriver(BrowserWebDriver.Browser.SAFARI, buildRemoteDriver(url, "OS X", "Big Sur", "Safari", "14.1", "testFlowSafari" + githubBuildNumber));
                default -> throw new IllegalStateException("unknown browser" + browser);
            };
        }

        @Bean
        List<BrowserWebDriver> webDrivers(Environment env) throws Exception {
            if(CI_RUN) {
                var browser = env.getRequiredProperty("e2e.browser");
                LOGGER.info("e2e profile detected, CI profile detected. Running full suite on BrowserStack");
                var url = new URL(browserStackUrl(env));
                var githubBuildNumber = "-" + env.getProperty("github.run.number", "NA");
                return List.of(build(browser, url, githubBuildNumber));
            } else {
                LOGGER.info("e2e profile detected, outside of CI. Returning local ChromeDriver");
                return List.of(new BrowserWebDriver(BrowserWebDriver.Browser.CHROME, new ChromeDriver()));
            }
        }
    }

    static class BrowserWebDriver {
        enum Browser {
            IE, CHROME, FIREFOX, SAFARI
        }
        final Browser browser;
        final WebDriver driver;

        private BrowserWebDriver(Browser browser, WebDriver driver) {
            this.browser = browser;
            this.driver = driver;
        }
    }
}
