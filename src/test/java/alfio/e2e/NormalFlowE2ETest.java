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

import alfio.TestConfiguration;
import alfio.config.Initializer;
import alfio.util.BaseIntegrationTest;
import alfio.util.ClockProvider;
import alfio.util.HttpUtils;
import lombok.SneakyThrows;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
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

import static org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated;

@ContextConfiguration(classes = { TestConfiguration.class, NormalFlowE2ETest.E2EConfiguration.class })
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
@SpringBootTest
public class NormalFlowE2ETest extends BaseIntegrationTest {

    private static final String JSON_BODY;

    static {
        try (var jsonStream = NormalFlowE2ETest.class.getResourceAsStream("/e2e/create-event-for-e2e.json")) {
            JSON_BODY = String.join("\n", IOUtils.readLines(new InputStreamReader(jsonStream, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private String eventUrl;
    private String slug;

    private final List<WebDriver> webDrivers;
    private final Environment environment;
    private final ClockProvider clockProvider;

    @Autowired
    public NormalFlowE2ETest(List<WebDriver> webDrivers,
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
            var requestBody = JSON_BODY.replace("--NOW--", now.toString())
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
    public void testFlow() {
        for(var driver : webDrivers) {
            try {
                driver.navigate().to(eventUrl);
                WebDriverWait wait = new WebDriverWait(driver, 10);
                wait.until(presenceOfElementLocated(By.cssSelector("div.markdown-content")));
                page1TicketSelection(driver);
                //wait until page is loaded
                wait.until(presenceOfElementLocated(By.cssSelector("h2[translate='reservation-page.your-details']")));
                //
                page2ContactDetails(driver, wait);
                //wait until page is loaded
                wait.until(presenceOfElementLocated(By.cssSelector("h2[translate='reservation-page.title']")));
                //
                page3Payment(driver, wait);
                WebElement fourthPageElem = new WebDriverWait(driver, 30).until(presenceOfElementLocated(By.cssSelector("div.attendees-data")));
                Assert.assertNotNull(fourthPageElem);
            } finally {
                driver.quit();
            }
        }
    }

    private void page1TicketSelection(WebDriver driver) {
        // select 1 ticket
        WebElement dropdown = driver.findElement(By.cssSelector("select[formcontrolname=amount]"));
        dropdown.findElement(By.xpath("//option[. = '1']")).click();
        //
        // click continue button, submit form
        driver.findElement(By.cssSelector("button.btn-success[translate='show-event.continue']")).sendKeys(Keys.RETURN);

    }

    private void page2ContactDetails(WebDriver driver, WebDriverWait wait) {
        driver.findElement(By.id("first-name")).sendKeys("Test");
        driver.findElement(By.id("last-name")).sendKeys("McTest");
        driver.findElement(By.id("email")).sendKeys(environment.getProperty("e2e.email", "noreply@example.org"));

        var invoiceRequested = driver.findElements(By.cssSelector("label[for=invoiceRequested]"));
        if(CollectionUtils.isNotEmpty(invoiceRequested)) {
            // select "I need an invoice for this reservation"
            invoiceRequested.get(0).sendKeys(Keys.SPACE);
        }

        driver.findElement(By.cssSelector("label[for=invoiceTypePrivate]")).click();
        driver.findElement(By.id("billingAddressLine1")).sendKeys("Bahnhofstrasse 1");
        driver.findElement(By.id("billingAddressZip")).sendKeys("8000");
        driver.findElement(By.id("billingAddressCity")).sendKeys("Zürich");


        driver.findElement(By.cssSelector("ng-select[formcontrolname='vatCountryCode']")).click();
        driver.findElement(By.cssSelector("ng-select[formcontrolname='vatCountryCode'] input[id=vatCountry]")).sendKeys("switzerland");
        wait.until(presenceOfElementLocated(By.cssSelector("ng-select[formcontrolname='vatCountryCode'] ng-dropdown-panel div[role=option]")));
        driver.findElement(By.cssSelector("ng-select[formcontrolname='vatCountryCode'] ng-dropdown-panel div[role=option]")).sendKeys(Keys.TAB);

        Assert.assertTrue(driver.findElement(By.cssSelector("ng-select[formcontrolname='vatCountryCode'] div.ng-value")).getText().contains("(CH)"));

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
    private void page3Payment(WebDriver driver, WebDriverWait wait) {
        driver.findElement(By.id("CREDIT_CARD-label")).sendKeys(Keys.SPACE);
        wait.until(presenceOfElementLocated(By.cssSelector("iframe[allowpaymentrequest=true]")));
        driver.findElement(By.id("card-name")).sendKeys("Test McTest");
        driver.switchTo().frame(By.cssSelector("iframe[allowpaymentrequest=true]").findElement(driver));
        wait.until(presenceOfElementLocated(By.name("cardnumber")));
        var cardNumberElement = driver.findElement(By.name("cardnumber"));
        sendSlowInput("4242424242424242", cardNumberElement);
        sendSlowInput("1220", driver.findElement(By.name("exp-date")));
        sendSlowInput("123", driver.findElement(By.name("cvc")));
        sendSlowInput("65000", driver.findElement(By.name("postal")));
        driver.switchTo().defaultContent();
        driver.findElements(By.id("privacy-policy-label")).forEach(e -> e.sendKeys(Keys.SPACE));
        driver.findElement(By.id("terms-conditions-label")).sendKeys(Keys.SPACE);

        //submit
        driver.findElement(By.cssSelector(".btn-success")).sendKeys(Keys.RETURN);
    }

    @SneakyThrows
    private void sendSlowInput(String input, WebElement element) {
        for (var c : input.toCharArray()) {
            element.sendKeys(Character.toString(c));
            Thread.sleep(50L);
        }
    }

    @Configuration
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
            caps.setCapability("browserstack.sendKeys", "true");
            caps.setCapability("browserstack.console", "errors");
            caps.setCapability("browserstack.networkLogs", "true");
            caps.setCapability("seleniumVersion", "4.0.0-alpha-6");
            return new RemoteWebDriver(url, caps);
        }

        @Bean
        String browserStackUrl(Environment env) {
            if(env.acceptsProfiles(Profiles.of("travis"))) {
                return "https://"
                    + env.getRequiredProperty("browserstack.username")
                    + ":"
                    + env.getRequiredProperty("browserstack.access.key")
                    + "@hub-cloud.browserstack.com/wd/hub";
            }
            return null;
        }

        @Bean
        List<WebDriver> webDrivers(Environment env, String browserStackUrl) throws Exception {
            if(env.acceptsProfiles(Profiles.of("travis"))) {
                var url = new URL(browserStackUrl);
                return List.of(
                    buildRemoteDriver(url,"Windows", "10", "IE", "11", "testFlowIE11"),
                    buildRemoteDriver(url,"Windows", "10", "Chrome", "latest", "testFlowChrome"),
                    buildRemoteDriver(url,"Windows", "10", "Firefox", "latest", "testFlowFirefox"),
                    buildRemoteDriver(url,"OS X", "Catalina", "Safari", "13.1", "testFlowSafari")
                );
            } else if(env.acceptsProfiles(Profiles.of("e2e"))) {
                return List.of(new ChromeDriver());
            }
            return List.of();
        }
    }
}
