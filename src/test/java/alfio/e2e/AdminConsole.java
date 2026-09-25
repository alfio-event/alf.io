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

import org.openqa.selenium.*;
import org.openqa.selenium.remote.LocalFileDetector;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;

import static alfio.e2e.E2EUtils.*;
import static java.util.Objects.requireNonNull;
import static org.openqa.selenium.support.ui.ExpectedConditions.*;

/**
 * Drives the admin console (/admin) the way an organizer would, in order to set up
 * the event used by the public purchase flow.
 */
class AdminConsole {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminConsole.class);
    private static final String LOGO_RESOURCE = "/images/sample-logo.png";

    private final NormalFlowE2ETest.BrowserWebDriver browserWebDriver;
    private final WebDriver driver;
    private final WebDriverWait wait;
    private final String serverBaseUrl;
    private final String username;
    private final String password;
    private final String organizationName;

    AdminConsole(NormalFlowE2ETest.BrowserWebDriver browserWebDriver,
                 String serverBaseUrl,
                 String username,
                 String password,
                 String organizationName) {
        this.browserWebDriver = browserWebDriver;
        this.driver = browserWebDriver.driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        this.serverBaseUrl = serverBaseUrl;
        this.username = username;
        this.password = password;
        this.organizationName = organizationName;
    }

    void login() {
        driver.navigate().to(serverBaseUrl + "/authentication");
        // if a session is already active, the login page redirects to the admin
        wait.until(or(presenceOfElementLocated(By.id("username")), urlContains("/admin")));
        if (driver.findElements(By.id("username")).isEmpty()) {
            return;
        }
        driver.findElement(By.id("username")).sendKeys(username);
        driver.findElement(By.id("password")).sendKeys(password);
        driver.findElement(By.cssSelector("button[type=submit].btn-success")).click();
        wait.until(urlContains("/admin"));
    }

    void logout() {
        var logoutLinkLocator = By.xpath("//a[@data-ng-click and contains(., 'Log out')]");
        var logoutLink = findDisplayed(logoutLinkLocator);
        if (logoutLink == null) {
            // on small screens the link is inside the collapsed menu
            driver.findElement(By.cssSelector("button.navbar-toggle")).click();
            logoutLink = wait.until(d -> findDisplayed(logoutLinkLocator));
        }
        logoutLink.click();
        // the page is reloaded after logout, and the user is sent back to the login page
        wait.until(presenceOfElementLocated(By.id("username")));
    }

    private WebElement findDisplayed(By locator) {
        return driver.findElements(locator).stream()
            .filter(WebElement::isDisplayed)
            .findFirst()
            .orElse(null);
    }

    void createEvent(EventDefinition event) {
        driver.navigate().to(serverBaseUrl + "/admin#/events/new");
        wait.until(elementToBeClickable(By.id("displayName")));

        fillBasicInfo(event);
        fillUrls(event);
        uploadLogo();
        fillPrices(event);
        addCategory(event);

        LOGGER.info("saving event {}", event.slug());
        clickWithJs(driver, driver.findElement(By.cssSelector("form[name=editEvent] control-buttons button.btn-warning")));
        wait.until(urlContains("/events/" + event.slug() + "/detail"));
    }

    void publishEvent(String slug) {
        openEventDetail(slug);
        var publishButton = wait.until(elementToBeClickable(By.xpath("//a[contains(@class, 'btn-warning') and contains(., 'Publish now')]")));
        clickWithJs(driver, publishButton);
        wait.until(presenceOfElementLocated(By.xpath("//*[contains(., 'this event has been successfully published')]")));
    }

    void deleteEvent(String slug) {
        openEventDetail(slug);
        // the actions menu might be collapsed, depending on the window size
        clickWithJs(driver, driver.findElement(By.id("actions-dpdwn")));
        var deleteLink = wait.until(presenceOfElementLocated(By.xpath("//ul[@aria-labelledby='actions-dpdwn']//a[contains(., 'Delete')]")));
        clickWithJs(driver, deleteLink);
        var confirmInput = wait.until(elementToBeClickable(By.cssSelector(".modal-dialog #confirm")));
        confirmInput.sendKeys(slug);
        selectElement(driver.findElement(By.cssSelector(".modal-dialog input[type=checkbox]")), browserWebDriver);
        var confirmButton = wait.until(elementToBeClickable(By.cssSelector(".modal-dialog button[type=submit]")));
        clickWithJs(driver, confirmButton);
        wait.until(invisibilityOfElementLocated(By.cssSelector(".modal-dialog")));
        LOGGER.info("event {} deleted", slug);
    }

    private void openEventDetail(String slug) {
        driver.navigate().to(serverBaseUrl + "/admin#/events/" + slug + "/detail");
        wait.until(presenceOfElementLocated(By.id("actions-dpdwn")));
    }

    private void fillBasicInfo(EventDefinition event) {
        var displayName = driver.findElement(By.id("displayName"));
        displayName.sendKeys(event.name());

        var organizationSelect = new Select(driver.findElement(By.id("organizationId")));
        if (organizationName != null && !organizationName.isBlank()) {
            organizationSelect.selectByVisibleText(organizationName);
        } else {
            // angular adds a placeholder option with value "?" while the model is undefined
            var firstOrganization = organizationSelect.getOptions().stream()
                .filter(o -> !o.getAttribute("value").startsWith("?"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No organization available for user " + username));
            organizationSelect.selectByVisibleText(firstOrganization.getText());
        }

        var location = driver.findElement(By.id("location"));
        location.sendKeys(event.location());
        // location is geocoded on blur, which can overwrite the time zone
        location.sendKeys(Keys.TAB);
        waitForGeolocation();
        selectBrowserTimeZone();

        driver.findElement(By.id("description")).sendKeys(event.description());

        // the event URL is generated from the name. Wait for it, then replace it with our slug
        var shortName = driver.findElement(By.id("shortName"));
        try {
            new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> !shortName.getAttribute("value").isEmpty());
        } catch (TimeoutException e) {
            LOGGER.warn("event URL was not generated automatically");
        }
        clearInput(shortName);
        shortName.sendKeys(event.slug());
        shortName.sendKeys(Keys.TAB);
    }

    private void waitForGeolocation() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(15)).until(invisibilityOfElementLocated(By.cssSelector(".map-loading")));
        } catch (TimeoutException e) {
            LOGGER.warn("geolocation did not complete in time, continuing anyway");
        }
    }

    /**
     * The category goes on sale "now" according to the browser's clock, so the event time zone must match
     * the browser's one. Otherwise, the sale might start in the future.
     */
    private void selectBrowserTimeZone() {
        var timeZone = (String) ((JavascriptExecutor) driver).executeScript("return Intl.DateTimeFormat().resolvedOptions().timeZone;");
        try {
            new Select(driver.findElement(By.id("timeZone"))).selectByVisibleText(timeZone);
        } catch (NoSuchElementException e) {
            LOGGER.warn("time zone {} not available, keeping the default one", timeZone);
        }
    }

    private void fillUrls(EventDefinition event) {
        fillUrl("websiteUrl", event.websiteUrl());
        fillUrl("termsAndConditionsUrl", event.termsAndConditionsUrl());
        fillUrl("privacyPolicyUrl", event.privacyPolicyUrl());
    }

    /**
     * URL fields are pre-filled with "https://" on focus.
     */
    private void fillUrl(String id, String url) {
        var element = scrollToCenter(driver.findElement(By.id(id)));
        element.click();
        clearInput(element);
        element.sendKeys(url);
    }

    private void uploadLogo() {
        if (driver.getClass() == RemoteWebDriver.class) {
            // upload the local file to the remote browser (i.e. BrowserStack). Local drivers don't support it
            ((RemoteWebDriver) driver).setFileDetector(new LocalFileDetector());
        }
        var fileInput = driver.findElement(By.cssSelector("input[type=file]"));
        // ng-file-upload hides the input. Make it interactable
        ((JavascriptExecutor) driver).executeScript("arguments[0].style.visibility = 'visible'; arguments[0].style.width = '1px'; arguments[0].style.height = '1px';", fileInput);
        fileInput.sendKeys(logoFile().toAbsolutePath().toString());
        wait.until(presenceOfElementLocated(By.cssSelector("img.event-logo")));
    }

    private static Path logoFile() {
        try (var logo = AdminConsole.class.getResourceAsStream(LOGO_RESOURCE)) {
            var file = Files.createTempFile("e2e-logo", ".png");
            Files.copy(requireNonNull(logo), file, StandardCopyOption.REPLACE_EXISTING);
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void fillPrices(EventDefinition event) {
        // the "Seats and payment info" section is displayed once the organization is selected
        wait.until(elementToBeClickable(By.id("availableSeats"))).sendKeys(Integer.toString(event.maxTickets()));
        driver.findElement(By.id("regularPrice")).sendKeys(event.price());
        var currency = driver.findElement(By.id("currency"));
        currency.sendKeys(event.currency());
        // close the currency suggestions
        currency.sendKeys(Keys.ESCAPE);
        driver.findElement(By.id("vatPercentage")).sendKeys(event.taxPercentage());
        var vatIncluded = driver.findElement(By.id("vatIncluded"));
        if (vatIncluded.isSelected() != event.taxIncludedInPrice()) {
            selectElement(vatIncluded, browserWebDriver);
        }
        for (var paymentMethod : event.paymentMethods()) {
            var checkbox = driver.findElement(By.xpath("//form[@name='editEvent']//div[contains(@class, 'checkbox')]/label[contains(normalize-space(.), '" + paymentMethod + "')]/input"));
            if (!checkbox.isSelected()) {
                selectElement(scrollTo(driver, checkbox), browserWebDriver);
            }
        }
    }

    private void addCategory(EventDefinition event) {
        clickWithJs(driver, driver.findElement(By.xpath("//form[@name='editEvent']//button[contains(., 'Add new')]")));
        var name = wait.until(elementToBeClickable(By.cssSelector(".modal-dialog #name")));
        name.sendKeys(event.categoryName());
        // price is pre-filled with the event's regular price, and the category is on sale until the event starts
        clickWithJs(driver, driver.findElement(By.cssSelector(".modal-footer control-buttons button.btn-warning")));
        wait.until(invisibilityOfElementLocated(By.cssSelector(".modal-dialog")));
    }

    private WebElement scrollToCenter(WebElement element) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
        return element;
    }

    private void clearInput(WebElement element) {
        // WebElement.clear() doesn't notify AngularJS, so we delete the content key by key
        int length = element.getAttribute("value").length();
        element.sendKeys(Keys.END);
        for (int i = 0; i < length; i++) {
            element.sendKeys(Keys.BACK_SPACE);
        }
    }

    record EventDefinition(String slug,
                           String name,
                           String location,
                           String description,
                           String websiteUrl,
                           String termsAndConditionsUrl,
                           String privacyPolicyUrl,
                           int maxTickets,
                           String price,
                           String currency,
                           String taxPercentage,
                           boolean taxIncludedInPrice,
                           List<String> paymentMethods,
                           String categoryName) {
    }
}
