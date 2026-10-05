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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

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

    private final BrowserWebDriver browserWebDriver;
    private final WebDriver driver;
    private final WebDriverWait wait;
    private final String serverBaseUrl;
    private final String username;
    private final String password;
    private final String organizationName;

    AdminConsole(BrowserWebDriver browserWebDriver,
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
        new WebDriverWait(driver, Duration.ofSeconds(30))
            .withMessage(() -> "login page not displayed after logout. Current URL: " + driver.getCurrentUrl())
            .until(presenceOfElementLocated(By.id("username")));
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

    /**
     * Composes a message for all the attendees and sends it, after checking the preview.
     */
    void sendMessageToAttendees(String slug, String subject, String message, int expectedRecipients) {
        driver.navigate().to(serverBaseUrl + "/admin#/events/" + slug + "/compose-custom-message");
        var composer = wait.until(presenceOfElementLocated(By.tagName("alfio-compose-message")));
        // the editor has one tab for each language of the event
        var tabs = wait.until(d -> {
            var found = findAllInShadowRoot(composer, "#messages-editor sl-tab");
            return found.isEmpty() ? null : found;
        });
        for (var tab : tabs) {
            clickWithJs(driver, tab);
            var locale = tab.getDomAttribute("panel");
            typeInShoelaceControl(findInShadowRoot(composer, "sl-input[name='subject-" + locale + "']"), "input", subject);
            typeInShoelaceControl(findInShadowRoot(composer, "sl-textarea[name='message-" + locale + "']"), "textarea", message);
        }
        clickWithJs(driver, findInShadowRoot(composer, "#preview-button"));

        var previewDialog = findInShadowRoot(composer, "#preview-dialog");
        wait.until(d -> previewDialog.getDomAttribute("open") != null);
        var expectedText = "Potentially affected users: " + expectedRecipients;
        wait.until(d -> String.valueOf(((JavascriptExecutor) d).executeScript("return arguments[0].textContent;", previewDialog)).contains(expectedText));
        // HTML e-mails are enabled by default: the preview must be rendered in a sandboxed iframe, without scripts nor same-origin access
        var htmlPreview = findInShadowRoot(composer, "#preview-dialog iframe.preview-html");
        var sandbox = htmlPreview.getDomAttribute("sandbox");
        if (sandbox == null || sandbox.contains("allow-scripts") || sandbox.contains("allow-same-origin")) {
            throw new IllegalStateException("HTML preview is not properly sandboxed: " + sandbox);
        }
        if (!String.valueOf(htmlPreview.getDomProperty("srcdoc")).contains("Content-Security-Policy")) {
            throw new IllegalStateException("HTML preview does not declare a Content-Security-Policy");
        }
        LOGGER.info("sending message \"{}\" to the attendees of {}", subject, slug);
        clickWithJs(driver, findInShadowRoot(composer, "#send-button"));
        // the dialog is closed once the messages have been enqueued
        wait.until(d -> previewDialog.getDomAttribute("open") == null);
    }

    /**
     * Checks on the E-mail log page that the message has been successfully sent to all recipients.
     * Messages are sent asynchronously, so we reload the page until they have been processed.
     */
    void verifyMessageSent(String slug, String subject) {
        driver.navigate().to(serverBaseUrl + "/admin#/events/" + slug + "/email-log");
        new WebDriverWait(driver, Duration.ofMinutes(3), Duration.ofSeconds(5))
            .ignoring(StaleElementReferenceException.class)
            .ignoring(NoSuchElementException.class)
            .withMessage(() -> "message \"" + subject + "\" has not been sent")
            .until(d -> {
                var statuses = emailLogRows().stream()
                    .filter(row -> row.findElement(By.cssSelector(".subject")).getText().contains(subject))
                    .map(row -> row.findElement(By.cssSelector(".email-status")).getDomAttribute("data-status"))
                    .toList();
                if (statuses.contains("ERROR")) {
                    throw new IllegalStateException("message \"" + subject + "\" could not be sent");
                }
                if (!statuses.isEmpty() && statuses.stream().allMatch("SENT"::equals)) {
                    LOGGER.info("message \"{}\" has been sent to {} recipient(s)", subject, statuses.size());
                    return true;
                }
                d.navigate().refresh();
                return false;
            });
    }

    /**
     * Filters the E-mail log: an unknown term shows the empty state, the customer name shows their e-mails.
     * Then opens the first e-mail and checks that its content is displayed.
     */
    void viewEmail(String slug, String customerName) {
        driver.navigate().to(serverBaseUrl + "/admin#/events/" + slug + "/email-log");
        var emailLog = wait.until(presenceOfElementLocated(By.tagName("alfio-email-log")));
        var search = findInShadowRoot(findInShadowRoot(emailLog, "sl-input.list-search"), "input");
        wait.until(d -> search.isDisplayed());
        search.sendKeys("no-match-" + slug);
        // the table is replaced when the results change, so it's looked up again every time
        new WebDriverWait(driver, Duration.ofSeconds(30))
            .ignoring(StaleElementReferenceException.class)
            .withMessage(() -> "expected empty state on the E-mail log")
            .until(d -> findAllInShadowRoot(emailLogTable(), ".empty-state").stream().anyMatch(e -> e.getText().contains("No e-mails found")));
        clearInput(search);
        search.sendKeys(customerName);
        viewFirstEmail(this::emailLogTable, customerName);
    }

    /**
     * Opens the "Emails sent" tab of the customer's reservation, then opens the first e-mail and checks that its content is displayed.
     */
    void viewReservationEmail(String slug, String customerName) {
        verifyConfirmedPayments(slug, 1);
        var reservationLink = findInRow(driver.findElement(By.tagName("alfio-payments-list")), customerName, ".reservation-id a");
        driver.navigate().to(reservationLink.getDomProperty("href"));
        var emailsTab = wait.until(elementToBeClickable(By.xpath("//ul[contains(@class, 'nav-tabs')]//a[contains(normalize-space(.), 'Emails sent')]")));
        clickWithJs(driver, emailsTab);
        viewFirstEmail(() -> wait.until(visibilityOfElementLocated(By.tagName("alfio-email-table"))), customerName);
    }

    private WebElement emailLogTable() {
        return findInShadowRoot(driver.findElement(By.tagName("alfio-email-log")), "alfio-email-table");
    }

    private List<WebElement> emailLogRows() {
        return shadowRows(emailLogTable());
    }

    private void viewFirstEmail(Supplier<WebElement> emailTable, String customerName) {
        var row = new WebDriverWait(driver, Duration.ofSeconds(30))
            .ignoring(StaleElementReferenceException.class)
            .withMessage(() -> "expected only the e-mails of " + customerName)
            .until(d -> {
                var rows = shadowRows(emailTable.get());
                if (!rows.isEmpty() && rows.stream().allMatch(r -> r.getText().contains(customerName))) {
                    return rows.getFirst();
                }
                return null;
            });
        LOGGER.info("viewing e-mail \"{}\"", row.findElement(By.cssSelector(".subject")).getText());
        clickWithJs(driver, row.findElement(By.cssSelector(".actions-cell sl-button")));

        var dialogHost = findInShadowRoot(emailTable.get(), "alfio-email-message-dialog");
        var dialog = findInShadowRoot(dialogHost, "sl-dialog");
        wait.until(d -> dialog.getDomAttribute("open") != null);
        var body = findInShadowRoot(dialogHost, ".message-body");
        wait.until(d -> body.getText().contains(customerName));
        clickWithJs(driver, findInShadowRoot(dialogHost, "div[slot='footer'] sl-button"));
        wait.until(d -> dialog.getDomAttribute("open") == null);
    }

    /**
     * Checks that the "Confirmed payments" page lists the expected number of payments.
     */
    void verifyConfirmedPayments(String slug, int expectedPayments) {
        driver.navigate().to(serverBaseUrl + "/admin#/events/" + slug + "/transactions/");
        new WebDriverWait(driver, Duration.ofSeconds(30))
            .ignoring(StaleElementReferenceException.class)
            .withMessage(() -> "expected " + expectedPayments + " confirmed payment(s) for " + slug)
            .until(d -> confirmedPaymentRows().size() == expectedPayments);
    }

    /**
     * Edits the notes of a confirmed payment, then checks that the list has been updated.
     */
    void editConfirmedPaymentNotes(String slug, String customerName, String notes) {
        var paymentsList = wait.until(presenceOfElementLocated(By.tagName("alfio-payments-list")));
        var editButton = wait.until(d -> confirmedPaymentRows().stream()
            .filter(row -> row.getText().contains(customerName))
            .findFirst()
            .map(row -> row.findElement(By.cssSelector("td.actions-cell sl-button")))
            .orElse(null));
        LOGGER.info("editing payment notes for {} on {}", customerName, slug);
        clickWithJs(driver, editButton);

        var dialogHost = findInShadowRoot(paymentsList, "alfio-edit-payment-dialog");
        var dialog = findInShadowRoot(dialogHost, "sl-dialog");
        wait.until(d -> dialog.getDomAttribute("open") != null);
        // the form is rendered once the transaction has been loaded
        typeInShoelaceControl(findInShadowRoot(dialogHost, "sl-textarea[name='notes']"), "textarea", notes);
        clickWithJs(driver, findInShadowRoot(dialogHost, "sl-button[variant='warning']"));
        wait.until(d -> dialog.getDomAttribute("open") == null);

        new WebDriverWait(driver, Duration.ofSeconds(30))
            .ignoring(StaleElementReferenceException.class)
            .withMessage(() -> "notes for " + customerName + " have not been updated")
            .until(d -> confirmedPaymentRows().stream()
                .filter(row -> row.getText().contains(customerName))
                .anyMatch(row -> row.findElement(By.cssSelector("td.notes")).getText().contains(notes)));
    }

    /**
     * Checks the pending payments count, both on the events list and on the event detail.
     * Nothing is displayed when there are no pending payments.
     */
    void verifyPendingPaymentsCount(String slug, int expected) {
        driver.navigate().to(serverBaseUrl + "/admin#/");
        waitForPendingPaymentsCount(slug, "summary", expected, summaryText(expected));
        openEventDetail(slug);
        waitForPendingPaymentsCount(slug, "badge", expected, badgeText(expected));
    }

    /**
     * Filters the pending payments: an unknown term shows the empty state, the customer name shows only their payment.
     */
    void filterPendingPayments(String slug, String customerName) {
        var pendingPayments = openPendingPayments(slug);
        var search = findInShadowRoot(findInShadowRoot(pendingPayments, "sl-input.list-search"), "input");
        wait.until(d -> search.isDisplayed());
        search.sendKeys("no-match-" + slug);
        waitForEmptyState(pendingPayments, "No pending payments match your filter");
        clearInput(search);
        search.sendKeys(customerName);
        new WebDriverWait(driver, Duration.ofSeconds(30))
            .ignoring(StaleElementReferenceException.class)
            .withMessage(() -> "expected only the pending payment of " + customerName)
            .until(d -> {
                var rows = shadowRows(pendingPayments);
                return rows.size() == 1 && rows.getFirst().getText().contains(customerName);
            });
        clearInput(search);
    }

    /**
     * Confirms a pending payment, then checks that the counter in the sidebar is updated without leaving the page.
     */
    void confirmPendingPayment(String slug, String customerName) {
        var pendingPayments = openPendingPayments(slug);
        var confirmButton = findInRow(pendingPayments, customerName, ".actions-cell sl-button[variant='success']");
        LOGGER.info("confirming pending payment for {} on {}", customerName, slug);
        clickWithJs(driver, confirmButton);
        // the organizer can specify when the payment has been received
        submitDialog(pendingPayments, "alfio-edit-payment-dialog", "success");
        waitForEmptyState(pendingPayments, "No pending payments found");
        waitForPendingPaymentsCount(slug, "badge", 0, badgeText(0));
    }

    /**
     * Confirms a pending payment by uploading a CSV file with the reservation ID and the paid amount.
     */
    void bulkConfirmPendingPayment(String slug, String customerName) {
        var pendingPayments = openPendingPayments(slug);
        var reservationId = findInRow(pendingPayments, customerName, "td.reservation-id a").getDomAttribute("href").replaceAll(".*/", "");
        // amount is formatted as "<currency> <amount>"
        var amount = findInRow(pendingPayments, customerName, "td.amount").getText().trim().replaceAll(".*\\s", "");
        LOGGER.info("bulk confirming reservation {} ({}) on {}", reservationId, amount, slug);

        var bulkConfirmation = findInShadowRoot(pendingPayments, "alfio-bulk-confirmation");
        var fileInput = findInShadowRoot(findInShadowRoot(bulkConfirmation, "alfio-file-upload"), "#file-input");
        uploadFile(fileInput, tempFile("e2e-payments", ".csv", (reservationId + "," + amount + "\n").getBytes(StandardCharsets.UTF_8)));
        var uploadButton = findInShadowRoot(bulkConfirmation, "sl-button[variant='success']");
        wait.until(d -> uploadButton.getDomAttribute("disabled") == null);
        clickWithJs(driver, uploadButton);

        new WebDriverWait(driver, Duration.ofSeconds(30))
            .withMessage(() -> "reservation " + reservationId + " has not been confirmed by the upload")
            .until(d -> findAllInShadowRoot(bulkConfirmation, ".results-summary sl-badge").stream()
                .map(WebElement::getText)
                .toList()
                .equals(List.of("1 confirmed")));
        waitForEmptyState(pendingPayments, "No pending payments found");
        waitForPendingPaymentsCount(slug, "badge", 0, badgeText(0));
    }

    /**
     * Deletes a pending payment, together with its reservation.
     */
    void deletePendingPayment(String slug, String customerName) {
        var pendingPayments = openPendingPayments(slug);
        clickWithJs(driver, findInRow(pendingPayments, customerName, ".actions-cell sl-dropdown sl-icon-button"));
        LOGGER.info("deleting pending payment for {} on {}", customerName, slug);
        clickWithJs(driver, findInRow(pendingPayments, customerName, "sl-menu-item[value='delete']"));
        submitDialog(pendingPayments, "alfio-cancel-payment-dialog", "danger");
        waitForEmptyState(pendingPayments, "No pending payments found");
        waitForPendingPaymentsCount(slug, "badge", 0, badgeText(0));
    }

    private WebElement openPendingPayments(String slug) {
        driver.navigate().to(serverBaseUrl + "/admin#/events/" + slug + "/pending-payments/");
        return wait.until(presenceOfElementLocated(By.tagName("alfio-pending-payments")));
    }

    private WebElement findInRow(WebElement host, String customerName, String selector) {
        return wait.until(d -> shadowRows(host).stream()
            .filter(row -> row.getText().contains(customerName))
            .findFirst()
            .map(row -> row.findElement(By.cssSelector(selector)))
            .orElse(null));
    }

    /**
     * Waits for the dialog to be open, clicks its main button and waits for the dialog to be closed
     */
    private void submitDialog(WebElement host, String dialogTagName, String buttonVariant) {
        var dialogHost = findInShadowRoot(host, dialogTagName);
        var dialog = findInShadowRoot(dialogHost, "sl-dialog");
        wait.until(d -> dialog.getDomAttribute("open") != null);
        clickWithJs(driver, findInShadowRoot(dialogHost, "sl-button[variant='" + buttonVariant + "']"));
        wait.until(d -> dialog.getDomAttribute("open") == null);
    }

    private void waitForEmptyState(WebElement host, String message) {
        new WebDriverWait(driver, Duration.ofSeconds(30))
            .ignoring(StaleElementReferenceException.class)
            .withMessage(() -> "expected empty state \"" + message + "\"")
            .until(d -> findAllInShadowRoot(host, ".empty-state").stream().anyMatch(e -> e.getText().contains(message)));
    }

    private List<WebElement> confirmedPaymentRows() {
        return shadowRows(driver.findElement(By.tagName("alfio-payments-list")));
    }

    @SuppressWarnings("unchecked")
    private List<WebElement> shadowRows(WebElement host) {
        return (List<WebElement>) ((JavascriptExecutor) driver).executeScript("return Array.from(arguments[0].shadowRoot?.querySelectorAll('tbody tr') ?? []);", host);
    }

    private void waitForPendingPaymentsCount(String slug, String display, int expectedCount, String expectedText) {
        new WebDriverWait(driver, Duration.ofSeconds(30))
            .withMessage(() -> "expected " + expectedCount + " pending payment(s) (" + display + ") for " + slug)
            .until(d -> {
                var state = (List<?>) ((JavascriptExecutor) d).executeScript(FIND_PENDING_PAYMENTS_COUNT, slug, display);
                // state is [count, text], or null if the component has not been rendered or the count is not yet available
                return state != null
                    && ((Number) state.get(0)).intValue() == expectedCount
                    && expectedText.equals(state.get(1));
            });
    }

    private static String badgeText(int count) {
        if (count == 0) {
            return "";
        }
        return Integer.toString(count);
    }

    private static String summaryText(int count) {
        if (count == 0) {
            return "";
        }
        if (count == 1) {
            return "1 payment pending";
        }
        return count + " payments pending";
    }

    /**
     * Finds the first "alfio-pending-payments-count" for the given event and display, also inside shadow roots.
     */
    private static final String FIND_PENDING_PAYMENTS_COUNT = """
        const [eventName, display] = arguments;
        const find = (root) => {
            for (const el of root.querySelectorAll('*')) {
                if (el.tagName === 'ALFIO-PENDING-PAYMENTS-COUNT' && el.eventName === eventName && el.display === display) {
                    return el;
                }
                if (el.shadowRoot) {
                    const found = find(el.shadowRoot);
                    if (found != null) {
                        return found;
                    }
                }
            }
            return null;
        };
        const counter = find(document);
        if (counter == null || counter.count == null) {
            return null;
        }
        return [counter.count, counter.shadowRoot.textContent.trim().replace(/\\s+/g, ' ')];
        """;

    /**
     * Shoelace controls render the native input inside their shadow root, and so does the component hosting them.
     * We resolve the elements with JavaScript, because not all the drivers support WebElement::getShadowRoot
     */
    private WebElement findInShadowRoot(WebElement host, String selector) {
        return wait.until(d -> (WebElement) ((JavascriptExecutor) d).executeScript("return arguments[0].shadowRoot.querySelector(arguments[1]);", host, selector));
    }

    @SuppressWarnings("unchecked")
    private List<WebElement> findAllInShadowRoot(WebElement host, String selector) {
        return (List<WebElement>) ((JavascriptExecutor) driver).executeScript("return Array.from(arguments[0].shadowRoot.querySelectorAll(arguments[1]));", host, selector);
    }

    private void typeInShoelaceControl(WebElement control, String nativeElement, String text) {
        var input = findInShadowRoot(control, nativeElement);
        // the tab panel is displayed after the tab has been selected
        wait.until(d -> input.isDisplayed());
        input.sendKeys(text);
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
        } catch (TimeoutException _) {
            LOGGER.warn("event URL was not generated automatically");
        }
        clearInput(shortName);
        shortName.sendKeys(event.slug());
        shortName.sendKeys(Keys.TAB);
    }

    private void waitForGeolocation() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(15)).until(invisibilityOfElementLocated(By.cssSelector(".map-loading")));
        } catch (TimeoutException _) {
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
        } catch (NoSuchElementException _) {
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
        try (var logo = AdminConsole.class.getResourceAsStream(LOGO_RESOURCE)) {
            uploadFile(driver.findElement(By.cssSelector("input[type=file]")), tempFile("e2e-logo", ".png", requireNonNull(logo).readAllBytes()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        wait.until(presenceOfElementLocated(By.cssSelector("img.event-logo")));
    }

    private void uploadFile(WebElement fileInput, Path file) {
        if (driver.getClass() == RemoteWebDriver.class) {
            // upload the local file to the remote browser (i.e. BrowserStack). Local drivers don't support it
            ((RemoteWebDriver) driver).setFileDetector(new LocalFileDetector());
        }
        // file inputs are hidden by the upload components. Make it interactable
        ((JavascriptExecutor) driver).executeScript("arguments[0].style.display = 'block'; arguments[0].style.visibility = 'visible'; arguments[0].style.width = '1px'; arguments[0].style.height = '1px';", fileInput);
        fileInput.sendKeys(file.toAbsolutePath().toString());
    }

    private static Path tempFile(String prefix, String suffix, byte[] content) {
        try {
            var file = Files.createTempFile(prefix, suffix);
            Files.write(file, content);
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
