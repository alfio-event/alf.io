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

import alfio.BaseTestConfiguration;
import alfio.config.Initializer;
import alfio.util.BaseIntegrationTest;
import alfio.util.ClockProvider;
import alfio.util.HttpUtils;
import lombok.SneakyThrows;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.*;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
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
import org.springframework.core.env.Profiles;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated;

/**
 * For testing with browserstack you need:
 * Enable the profiles e2e and travis
 *  -Dspring.profiles.active=e2e,travis
 *
 * And pass the following env var:
 *  - browserstack.username=
 *  - browserstack.access.key=
 *  - e2e.server.url=
 *  - e2e.server.apikey=
 *  - e2e.browser=ie11|safari|firefox|chrome
 */
@ContextConfiguration(classes = { BaseTestConfiguration.class, NormalFlowE2ETest.E2EConfiguration.class })
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
@SpringBootTest
public class NormalFlowE2ETest extends BaseIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(NormalFlowE2ETest.class);
    private static final String JSON_BODY;

    static {
        try (var jsonStream = NormalFlowE2ETest.class.getResourceAsStream("/e2e/create-event-for-e2e.json")) {
            JSON_BODY = String.join("\n", IOUtils.readLines(new InputStreamReader(requireNonNull(jsonStream), StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private String eventUrl;
    private String slug;

    private final List<BrowserWebDriver> webDrivers;
    private final Environment environment;
    private final ClockProvider clockProvider;

    @Autowired
    public NormalFlowE2ETest(List<BrowserWebDriver> webDrivers,
                             Environment environment,
                             ClockProvider clockProvider) {
        this.webDrivers = webDrivers;
        this.environment = environment;
        this.clockProvider = clockProvider;
    }

    @BeforeEach
    public void init() throws Exception {
        if(environment.acceptsProfiles(Profiles.of("e2e"))) {
            var serverBaseUrl = environment.getRequiredProperty("e2e.server.url");
            var serverApiKey = environment.getRequiredProperty("e2e.server.apikey");

            slug = UUID.randomUUID().toString();
            var now = LocalDateTime.now(clockProvider.getClock());
            var requestBody = JSON_BODY.replace("--CATEGORY_START_SELLING--", now.minusDays(1).toString())
                .replace("--SLUG--", slug)
                .replace("--EVENT_START_DATE--", now.plusDays(2).toString())
                .replace("--EVENT_END_DATE--", now.plusDays(2).plusHours(2).toString());
            // create temporary event
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/admin/event/create"))
                .header("Content-Type", "application/json")
                .header("Authorization", "ApiKey " + serverApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
            var response = HTTP_CLIENT.send(request, BodyHandlers.ofString());
            if(!HttpUtils.callSuccessful(response)) {
                throw new IllegalStateException(response.statusCode() + ": "+response.body());
            }
            eventUrl = serverBaseUrl + "/event/"+slug;
        }
    }

    @AfterEach
    public void destroy() throws Exception {
        if(environment.acceptsProfiles(Profiles.of("e2e"))) {
            var serverBaseUrl = environment.getRequiredProperty("e2e.server.url");
            var serverApiKey = environment.getRequiredProperty("e2e.server.apikey");
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/v1/admin/event/"+slug))
                .header("Content-Type", "application/json")
                .header("Authorization", "ApiKey " + serverApiKey)
                .DELETE()
                .build();
            var response = HTTP_CLIENT.send(request, BodyHandlers.ofString());
            if(!HttpUtils.callSuccessful(response)) {
                throw new IllegalStateException(response.body());
            }
        }
    }

    @Test
    @Timeout(value = 15L, unit = TimeUnit.MINUTES)
    public void testFlow() {
        for(var browserWebDriver : webDrivers) {
            var driver = browserWebDriver.driver;
            try {
                driver.navigate().to(eventUrl);
                WebDriverWait wait = new WebDriverWait(driver, 10);
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
                WebElement fourthPageElem = new WebDriverWait(driver, 30).until(presenceOfElementLocated(By.cssSelector("div.attendees-data")));
                Assertions.assertNotNull(fourthPageElem);
            } finally {
                driver.quit();
            }
        }
    }

    private void page1TicketSelection(BrowserWebDriver browserWebDriver) {
        // select 1 ticket
        WebElement dropdown = browserWebDriver.driver.findElement(By.cssSelector("select[formcontrolname=amount]"));
        dropdown.findElement(By.xpath("//option[. = '1']")).click();
        //
        // click continue button, submit form
        browserWebDriver.driver.findElement(By.cssSelector("button.btn-success[translate='show-event.continue']")).sendKeys(Keys.RETURN);

    }

    private void page2ContactDetails(BrowserWebDriver browserWebDriver, WebDriverWait wait) {
        var driver = browserWebDriver.driver;
        driver.findElement(By.id("first-name")).sendKeys("Test");
        driver.findElement(By.id("last-name")).sendKeys("McTest");
        driver.findElement(By.id("email")).sendKeys(environment.getProperty("e2e.email", "noreply@example.org"));

        var invoiceRequested = driver.findElements(By.cssSelector("label[for=invoiceRequested]"));
        if(CollectionUtils.isNotEmpty(invoiceRequested)) {
            // select "I need an invoice for this reservation"
            selectElement(invoiceRequested.get(0), browserWebDriver);
        }

        driver.findElement(By.cssSelector("label[for=invoiceTypePrivate]")).click();
        driver.findElement(By.id("billingAddressLine1")).sendKeys("Bahnhofstrasse 1");
        driver.findElement(By.id("billingAddressZip")).sendKeys("8000");
        driver.findElement(By.id("billingAddressCity")).sendKeys("Zürich");


        driver.findElement(By.cssSelector("ng-select[formcontrolname='vatCountryCode']")).click();
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

    @SneakyThrows
    private void page3Payment(BrowserWebDriver browserWebDriver, WebDriverWait wait) {
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

    @SneakyThrows
    private void sendSlowInput(WebElement element, BrowserWebDriver browserWebDriver, CharSequence... strings) {
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

    private void selectElement(WebElement element, BrowserWebDriver driver) {
        selectElement(element, driver, Keys.SPACE);
    }

    private void selectElement(WebElement element, BrowserWebDriver driver, Keys keyToSend) {
        if(driver.browser == BrowserWebDriver.Browser.SAFARI) {
            element.sendKeys(keyToSend);
        } else {
            element.click();
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
            caps.setCapability("os", os);
            caps.setCapability("os_version", osVersion);
            caps.setCapability("browser", browser);
            caps.setCapability("browser_version", browserVersion);
            caps.setCapability("name", profileName);
            caps.setCapability("browserstack.console", "errors");
            caps.setCapability("browserstack.networkLogs", "true");
            caps.setCapability("seleniumVersion", "4.0.0-beta-2");
            caps.setCapability("browserstack.idleTimeout", "180");
            return new RemoteWebDriver(url, caps);
        }

        @Bean
        String browserStackUrl(Environment env) {
            if(env.acceptsProfiles(Profiles.of("e2e")) && env.acceptsProfiles(Profiles.of("travis"))) {
                return "https://"
                    + env.getRequiredProperty("browserstack.username")
                    + ":"
                    + env.getRequiredProperty("browserstack.access.key")
                    + "@hub-cloud.browserstack.com/wd/hub";
            }
            return null;
        }


        private BrowserWebDriver build(String browser, URL url, String githubBuildNumber) {
            switch (browser) {
                case "ie11": return new BrowserWebDriver(BrowserWebDriver.Browser.IE, buildRemoteDriver(url,"Windows", "10", "IE", "11", "testFlowIE11"+githubBuildNumber));
                case "chrome": return new BrowserWebDriver(BrowserWebDriver.Browser.CHROME, buildRemoteDriver(url,"Windows", "10", "Chrome", "latest", "testFlowChrome"+githubBuildNumber));
                case "firefox": return new BrowserWebDriver(BrowserWebDriver.Browser.FIREFOX, buildRemoteDriver(url,"Windows", "10", "Firefox", "latest", "testFlowFirefox"+githubBuildNumber));
                case "safari": return new BrowserWebDriver(BrowserWebDriver.Browser.SAFARI, buildRemoteDriver(url,"OS X", "Catalina", "Safari", "13.1", "testFlowSafari"+githubBuildNumber));
                default: throw new IllegalStateException("unknown browser" + browser);
            }
        }

        @Bean
        List<BrowserWebDriver> webDrivers(Environment env, String browserStackUrl) throws Exception {
            boolean e2e = env.acceptsProfiles(Profiles.of("e2e"));
            if(e2e && env.acceptsProfiles(Profiles.of("travis"))) {
                var browser = env.getRequiredProperty("e2e.browser");
                LOGGER.info("e2e profile detected, CI profile detected. Running full suite on BrowserStack");
                var url = new URL(browserStackUrl);
                var githubBuildNumber = "-" + env.getProperty("github.run.number", "NA");
                return List.of(build(browser, url, githubBuildNumber));
            } else if(e2e) {
                LOGGER.info("e2e profile detected, outside of CI. Returning local ChromeDriver");
                return List.of(new BrowserWebDriver(BrowserWebDriver.Browser.CHROME, new ChromeDriver()));
            }
            LOGGER.info("e2e profile was not detected, test will be skipped");
            return List.of();
        }
    }

    private static class BrowserWebDriver {
        private enum Browser {
            IE, CHROME, FIREFOX, SAFARI
        }
        private final Browser browser;
        private final WebDriver driver;

        private BrowserWebDriver(Browser browser, WebDriver driver) {
            this.browser = browser;
            this.driver = driver;
        }
    }
}
