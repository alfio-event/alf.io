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

import alfio.model.transaction.PaymentProxy;
import org.openqa.selenium.*;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static alfio.e2e.E2EUtils.*;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
            logoutLink = wait.until(_ -> findDisplayed(logoutLinkLocator));
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
        var tabs = wait.until(_ -> {
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
        wait.until(_ -> previewDialog.getDomAttribute("open") != null);
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
        wait.until(_ -> previewDialog.getDomAttribute("open") == null);
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
        wait.until(_ -> search.isDisplayed());
        search.sendKeys("no-match-" + slug);
        // the table is replaced when the results change, so it's looked up again every time
        new WebDriverWait(driver, Duration.ofSeconds(30))
            .ignoring(StaleElementReferenceException.class)
            .withMessage(() -> "expected empty state on the E-mail log")
            .until(_ -> findAllInShadowRoot(emailLogTable(), ".empty-state").stream().anyMatch(e -> e.getText().contains("No e-mails found")));
        clearInput(search);
        search.sendKeys(customerName);
        viewFirstEmail(this::emailLogTable, customerName);
    }

    /**
     * Opens the "E-mails" tab of the customer's reservation, then opens the first e-mail and checks that its content is displayed.
     */
    void viewReservationEmail(String slug, String customerName) {
        verifyConfirmedPayments(slug, 1);
        var reservationDetail = openReservationDetail(driver.findElement(By.tagName("alfio-payments-list")), customerName);
        clickWithJs(driver, findInShadowRoot(reservationDetail, "sl-tab[panel='emails']"));
        viewFirstEmail(() -> findInShadowRoot(reservationDetail, "alfio-email-table"), customerName);
    }

    /**
     * Opens the detail of the customer's (confirmed) reservation, edits the first name of the contact person
     * and waits for the change to be saved.
     * @return the contact information displayed after the update
     */
    String editReservationContact(String slug, String customerName, String newFirstName) {
        verifyConfirmedPayments(slug, 1);
        var reservationDetail = openReservationDetail(driver.findElement(By.tagName("alfio-payments-list")), customerName);
        clickWithJs(driver, findInShadowRoot(reservationDetail, "section.contact .section-actions sl-button"));
        var firstName = findInShadowRoot(findInShadowRoot(reservationDetail, "section.contact sl-input[name='firstName']"), "input");
        wait.until(_ -> firstName.isDisplayed());
        clearInput(firstName);
        firstName.sendKeys(newFirstName);
        LOGGER.info("updating contact of {} on {}", customerName, slug);
        var saveButton = findInShadowRoot(reservationDetail, "sl-button[type='submit']");
        wait.until(_ -> saveButton.getDomAttribute("disabled") == null);
        clickWithJs(driver, saveButton);
        new WebDriverWait(driver, Duration.ofSeconds(30))
            .ignoring(StaleElementReferenceException.class)
            .withMessage(() -> "contact of " + customerName + " has not been updated")
            .until(_ -> findInShadowRoot(reservationDetail, "section.contact dl.detail-list").getText().contains(newFirstName + " " + customerName));
        return findInShadowRoot(reservationDetail, "section.contact dl.detail-list").getText();
    }

    /**
     * Turns the customer into a company and enters a new address, field by field.
     * @return the contact information displayed after saving
     */
    String editReservationBillingDetails(String slug, String customerName, String companyName, String addressLine1, String zip, String city) {
        verifyConfirmedPayments(slug, 1);
        var reservationDetail = openReservationDetail(driver.findElement(By.tagName("alfio-payments-list")), customerName);
        clickWithJs(driver, findInShadowRoot(reservationDetail, "section.contact .section-actions sl-button"));
        clickWithJs(driver, findInShadowRoot(reservationDetail, "section.contact sl-radio-button[value='company']"));
        replaceInShoelaceInput(reservationDetail, "companyName", companyName);
        // a free text address can be converted to a structured one
        var addressSwitch = findAllInShadowRoot(reservationDetail, "section.contact sl-button.address-switch");
        if (!addressSwitch.isEmpty()) {
            clickWithJs(driver, addressSwitch.getFirst());
        }
        replaceInShoelaceInput(reservationDetail, "addressLine1", addressLine1);
        replaceInShoelaceInput(reservationDetail, "zip", zip);
        replaceInShoelaceInput(reservationDetail, "city", city);
        LOGGER.info("updating billing details of {} on {}", customerName, slug);
        var saveButton = findInShadowRoot(reservationDetail, "sl-button[type='submit']");
        wait.until(_ -> saveButton.getDomAttribute("disabled") == null);
        clickWithJs(driver, saveButton);
        new WebDriverWait(driver, Duration.ofSeconds(30))
            .ignoring(StaleElementReferenceException.class)
            .withMessage(() -> "billing details of " + customerName + " have not been updated")
            .until(_ -> findInShadowRoot(reservationDetail, "section.contact dl.detail-list").getText().contains(addressLine1));
        return normalizedText(findInShadowRoot(reservationDetail, "section.contact dl.detail-list"));
    }

    /**
     * Safari returns the text of shadow DOM elements with the template indentation and blank lines:
     * trim each line and drop the empty ones, so that the result is the same on every browser.
     */
    private static String normalizedText(WebElement element) {
        return element.getText().lines()
            .map(String::strip)
            .filter(line -> !line.isEmpty())
            .collect(Collectors.joining("\n"));
    }

    private void replaceInShoelaceInput(WebElement host, String name, String text) {
        var input = findInShadowRoot(findInShadowRoot(host, "section.contact sl-input[name='" + name + "']"), "input");
        wait.until(_ -> input.isDisplayed());
        clearInput(input);
        input.sendKeys(text);
    }

    /**
     * Opens the customer's pending reservation from the pending payments page, and confirms the payment from its detail.
     * @return the status of the reservation after the confirmation
     */
    String confirmPaymentFromReservationDetail(String slug, String customerName) {
        var reservationDetail = openReservationDetail(openPendingPayments(slug), customerName);
        waitForReservationStatus(reservationDetail, "OFFLINE_PAYMENT");
        LOGGER.info("confirming payment of {} from the reservation detail", customerName);
        clickWithJs(driver, findInShadowRoot(reservationDetail, ".filter-right sl-button[variant='success']"));
        submitDialog(reservationDetail, "alfio-edit-payment-dialog", "success");
        return waitForReservationStatus(reservationDetail, "COMPLETE");
    }

    /**
     * Opens the customer's pending reservation from the pending payments page, and cancels it from its detail.
     * @return the status of the reservation after the cancellation
     */
    String cancelReservationFromDetail(String slug, String customerName) {
        var reservationDetail = openReservationDetail(openPendingPayments(slug), customerName);
        waitForReservationStatus(reservationDetail, "OFFLINE_PAYMENT");
        LOGGER.info("cancelling reservation of {} from the reservation detail", customerName);
        clickWithJs(driver, findInShadowRoot(reservationDetail, ".filter-right sl-dropdown sl-icon-button"));
        clickWithJs(driver, findInShadowRoot(reservationDetail, "sl-menu-item[value='cancel']"));
        submitDialog(reservationDetail, "alfio-cancellation-dialog", "danger");
        return waitForReservationStatus(reservationDetail, "CANCELLED");
    }

    /**
     * Follows the link to the customer's reservation in a list (payments, pending payments)
     */
    private WebElement openReservationDetail(WebElement list, String customerName) {
        var reservationLink = findInRow(list, customerName, ".reservation-id a");
        driver.navigate().to(reservationLink.getDomProperty("href"));
        return wait.until(presenceOfElementLocated(By.tagName("alfio-reservation-detail")));
    }

    /**
     * Waits until the reservation has the expected status (the page is reloaded asynchronously after each operation)
     * @return the current status
     */
    private String waitForReservationStatus(WebElement reservationDetail, String expectedStatus) {
        new WebDriverWait(driver, Duration.ofSeconds(30))
            .ignoring(StaleElementReferenceException.class)
            .withMessage(() -> "expected reservation status " + expectedStatus)
            .until(_ -> expectedStatus.equals(reservationStatus(reservationDetail)));
        return reservationStatus(reservationDetail);
    }

    private String reservationStatus(WebElement reservationDetail) {
        return findInShadowRoot(reservationDetail, ".page-title-row .reservation-status").getDomAttribute("data-status");
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
            .until(_ -> {
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
        wait.until(_ -> dialog.getDomAttribute("open") != null);
        var body = findInShadowRoot(dialogHost, ".message-body");
        wait.until(_ -> body.getText().contains(customerName));
        clickWithJs(driver, findInShadowRoot(dialogHost, "div[slot='footer'] sl-button"));
        wait.until(_ -> dialog.getDomAttribute("open") == null);
    }

    /**
     * Checks that the "Confirmed payments" page lists the expected number of payments.
     */
    void verifyConfirmedPayments(String slug, int expectedPayments) {
        driver.navigate().to(serverBaseUrl + "/admin#/events/" + slug + "/transactions/");
        new WebDriverWait(driver, Duration.ofSeconds(30))
            .ignoring(StaleElementReferenceException.class)
            .withMessage(() -> "expected " + expectedPayments + " confirmed payment(s) for " + slug)
            .until(_ -> confirmedPaymentRows().size() == expectedPayments);
    }

    /**
     * Edits the notes of a confirmed payment, then checks that the list has been updated.
     */
    void editConfirmedPaymentNotes(String slug, String customerName, String notes) {
        var paymentsList = wait.until(presenceOfElementLocated(By.tagName("alfio-payments-list")));
        var editButton = wait.until(_ -> confirmedPaymentRows().stream()
            .filter(row -> row.getText().contains(customerName))
            .findFirst()
            .map(row -> row.findElement(By.cssSelector("td.actions-cell sl-button")))
            .orElse(null));
        LOGGER.info("editing payment notes for {} on {}", customerName, slug);
        clickWithJs(driver, editButton);

        var dialogHost = findInShadowRoot(paymentsList, "alfio-edit-payment-dialog");
        var dialog = findInShadowRoot(dialogHost, "sl-dialog");
        wait.until(_ -> dialog.getDomAttribute("open") != null);
        // the form is rendered once the transaction has been loaded
        typeInShoelaceControl(findInShadowRoot(dialogHost, "sl-textarea[name='notes']"), "textarea", notes);
        clickWithJs(driver, findInShadowRoot(dialogHost, "sl-button[variant='warning']"));
        wait.until(_ -> dialog.getDomAttribute("open") == null);

        new WebDriverWait(driver, Duration.ofSeconds(30))
            .ignoring(StaleElementReferenceException.class)
            .withMessage(() -> "notes for " + customerName + " have not been updated")
            .until(_ -> confirmedPaymentRows().stream()
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
        wait.until(_ -> search.isDisplayed());
        search.sendKeys("no-match-" + slug);
        waitForEmptyState(pendingPayments, "No pending payments match your filter");
        clearInput(search);
        search.sendKeys(customerName);
        new WebDriverWait(driver, Duration.ofSeconds(30))
            .ignoring(StaleElementReferenceException.class)
            .withMessage(() -> "expected only the pending payment of " + customerName)
            .until(_ -> {
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
        wait.until(_ -> uploadButton.getDomAttribute("disabled") == null);
        clickWithJs(driver, uploadButton);

        new WebDriverWait(driver, Duration.ofSeconds(30))
            .withMessage(() -> "reservation " + reservationId + " has not been confirmed by the upload")
            .until(_ -> findAllInShadowRoot(bulkConfirmation, ".results-summary sl-badge").stream()
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
        submitDialog(pendingPayments, "alfio-cancellation-dialog", "danger");
        waitForEmptyState(pendingPayments, "No pending payments found");
        waitForPendingPaymentsCount(slug, "badge", 0, badgeText(0));
    }

    /**
     * Filters the attendees on the Check-in page: an unknown term shows the empty state, the attendee name shows only their ticket.
     * Then displays the QR code of the ticket, and checks the attendee in.
     */
    void checkInAttendee(String slug, String attendeeName) {
        var checkIn = openCheckIn(slug);
        var search = findInShadowRoot(findInShadowRoot(checkIn, "sl-input.list-search"), "input");
        wait.until(_ -> search.isDisplayed());
        search.sendKeys("no-match-" + slug);
        waitForEmptyState(checkIn, "No attendees match your filter");
        clearInput(search);
        search.sendKeys(attendeeName);
        waitForCheckInCounts(checkIn, 1, 0);

        clickWithJs(driver, findInRow(checkIn, attendeeName, "sl-button.qr-code-button"));
        var qrCodeDialog = findInShadowRoot(checkIn, "#qr-code-dialog");
        wait.until(_ -> qrCodeDialog.getDomAttribute("open") != null);
        var qrCode = findInShadowRoot(checkIn, "#qr-code-dialog img.qr-code");
        new WebDriverWait(driver, Duration.ofSeconds(30))
            .withMessage(() -> "QR code of " + attendeeName + " not displayed")
            .until(d -> Boolean.TRUE.equals(((JavascriptExecutor) d).executeScript("return arguments[0].complete && arguments[0].naturalWidth > 0;", qrCode)));
        clickWithJs(driver, findInShadowRoot(checkIn, "#qr-code-dialog div[slot='footer'] sl-button"));
        wait.until(_ -> qrCodeDialog.getDomAttribute("open") == null);

        LOGGER.info("checking in {} on {}", attendeeName, slug);
        clickWithJs(driver, findInRow(checkIn, attendeeName, "sl-button.check-in-button"));
        waitForCheckInCounts(checkIn, 0, 1);
        // the filter is kept, so that the operator can go on checking in the rest of a group
        assertEquals(attendeeName, search.getDomProperty("value"));
        clickWithJs(driver, findInShadowRoot(checkIn, "sl-tab[panel='checked-in']"));
        findInRow(checkIn, attendeeName, "sl-button.revert-button");
    }

    /**
     * Reloads the Check-in page, to make sure that the check-in has been saved, then reverts it.
     */
    void revertCheckIn(String slug, String attendeeName) {
        openCheckIn(slug);
        // the attendees are cached by the page, a reload discards them
        driver.navigate().refresh();
        var checkIn = wait.until(presenceOfElementLocated(By.tagName("alfio-check-in")));
        waitForCheckInCounts(checkIn, 0, 1);
        clickWithJs(driver, findInShadowRoot(checkIn, "sl-tab[panel='checked-in']"));
        LOGGER.info("reverting check-in of {} on {}", attendeeName, slug);
        clickWithJs(driver, findInRow(checkIn, attendeeName, "sl-button.revert-button"));
        waitForCheckInCounts(checkIn, 1, 0);
        // rows of the inactive tab are hidden
        clickWithJs(driver, findInShadowRoot(checkIn, "sl-tab[panel='pending']"));
        findInRow(checkIn, attendeeName, "sl-button.check-in-button");
    }

    WebElement openCheckIn(String slug) {
        driver.navigate().to(serverBaseUrl + "/admin#/events/" + slug + "/check-in");
        return wait.until(presenceOfElementLocated(By.tagName("alfio-check-in")));
    }

    private void waitForCheckInCounts(WebElement checkIn, int expectedPending, int expectedCheckedIn) {
        waitForCheckInCounts(checkIn, expectedPending, expectedCheckedIn, Duration.ofSeconds(30));
    }

    /**
     * Polls often, so that the time spent waiting is a good approximation of the time needed to update the page
     */
    void waitForCheckInCounts(WebElement checkIn, int expectedPending, int expectedCheckedIn, Duration timeout) {
        var expected = List.of(Integer.toString(expectedPending), Integer.toString(expectedCheckedIn));
        new WebDriverWait(driver, timeout, Duration.ofMillis(20))
            .withMessage(() -> "expected " + expectedPending + " pending and " + expectedCheckedIn + " checked in attendee(s), got " + checkInCounts(checkIn))
            .until(_ -> checkInCounts(checkIn).equals(expected));
    }

    /**
     * @return the number of pending and checked in attendees, as displayed by the tabs
     */
    @SuppressWarnings("unchecked")
    List<String> checkInCounts(WebElement checkIn) {
        return (List<String>) ((JavascriptExecutor) driver).executeScript("return Array.from(arguments[0].shadowRoot?.querySelectorAll('sl-tab sl-badge') ?? []).map(b => b.textContent.trim());", checkIn);
    }

    void waitForAttendeesDisplayed(WebElement checkIn, Duration timeout) {
        new WebDriverWait(driver, timeout, Duration.ofMillis(20))
            .withMessage(() -> "no attendees displayed")
            .until(_ -> checkInCounts(checkIn).stream().anyMatch(count -> !count.equals("0")));
    }

    void waitForAttendeesDownloaded(WebElement checkIn, Duration timeout) {
        new WebDriverWait(driver, timeout, Duration.ofMillis(20))
            .withMessage(() -> "attendees not downloaded")
            .until(_ -> !isLoadingAttendees(checkIn));
    }

    /**
     * The import completes before the tickets become ACQUIRED: refreshes the list until the displayed attendees can be checked in
     */
    void waitForAcquiredTickets(WebElement checkIn, Duration timeout) {
        new WebDriverWait(driver, timeout, Duration.ofSeconds(5))
            .withMessage(() -> "tickets not ACQUIRED")
            .until(_ -> {
                var acquired = Boolean.TRUE.equals(((JavascriptExecutor) driver).executeScript("""
                    const statuses = Array.from(arguments[0].shadowRoot.querySelectorAll('table.pending-tickets .ticket-status')).map(i => i.dataset.status);
                    return statuses.length > 0 && statuses.every(s => s === 'ACQUIRED');
                    """, checkIn));
                if (!acquired) {
                    clickWithJs(driver, findInShadowRoot(checkIn, "#refresh-button"));
                    new WebDriverWait(driver, Duration.ofMinutes(1)).until(_ -> findInShadowRoot(checkIn, "#refresh-button").getDomAttribute("loading") == null);
                }
                return acquired;
            });
    }

    boolean isLoadingAttendees(WebElement checkIn) {
        return Boolean.TRUE.equals(((JavascriptExecutor) driver).executeScript("return arguments[0].shadowRoot?.querySelector('sl-progress-bar.download-progress') != null;", checkIn));
    }

    /**
     * Replaces the content of the search field of the Check-in page
     */
    void searchAttendees(WebElement checkIn, String text) {
        var search = findInShadowRoot(findInShadowRoot(checkIn, "sl-input.list-search"), "input");
        wait.until(_ -> search.isDisplayed());
        clearInput(search);
        search.sendKeys(text);
    }

    void checkInFirstPendingAttendee(WebElement checkIn) {
        clickWithJs(driver, findInShadowRoot(checkIn, "table.pending-tickets sl-button.check-in-button:not([disabled])"));
    }

    /**
     * Defines an attendee field, using the same API as the "Attendees data to collect" page
     */
    @SuppressWarnings("unchecked")
    void addAttendeeField(String slug, String name, String type, List<String> values) {
        openEventDetail(slug);
        var field = Map.of(
            "name", name,
            "type", type,
            "required", false,
            "readOnly", false,
            "displayAtCheckIn", false,
            "restrictedValues", values.stream().map(v -> Map.of("value", v, "enabled", true)).toList(),
            "description", Map.of("en", Map.of("label", name, "placeholder", name, "restrictedValues", values.stream().collect(Collectors.toMap(v -> v, v -> v)))));
        var result = (Map<String, Object>) ((JavascriptExecutor) driver).executeAsyncScript("""
            const [url, field, done] = arguments;
            const csrfHeader = document.querySelector('meta[name=_csrf_header]').getAttribute('content');
            const csrf = document.querySelector('meta[name=_csrf]').getAttribute('content');
            fetch(url, {
                method: 'POST',
                credentials: 'include',
                headers: {'Content-Type': 'application/json', [csrfHeader]: csrf},
                body: JSON.stringify(field)
            }).then(r => r.json()).then(done, e => done({success: false, error: String(e)}));
            """, serverBaseUrl + "/admin/api/event/" + slug + "/additional-field/new", field);
        assertEquals(Boolean.TRUE, result.get("success"), () -> "cannot add field " + name + ": " + result);
        LOGGER.info("added attendee field {} to {}", name, slug);
    }

    /**
     * Uploads the CSV through the "Import attendees" page, creating one reservation per attendee,
     * then waits for the server to process all of them
     */
    void importAttendees(String slug, String categoryName, Path csv, int expectedAttendees, Duration timeout) {
        importAttendees(slug, categoryName, csv, expectedAttendees, null, timeout);
    }

    /**
     * Imports the attendees of the CSV file in an existing category, and waits for the import to be completed.
     * @param contact the contact person of the reservation including all the attendees, or null to create one reservation per attendee
     */
    void importAttendees(String slug, String categoryName, Path csv, int expectedAttendees, ReservationContact contact, Duration timeout) {
        driver.navigate().to(serverBaseUrl + "/admin#/events/" + slug + "/reservation/import");
        var importPage = wait.until(presenceOfElementLocated(By.tagName("alfio-attendees-import")));
        var start = System.nanoTime();
        uploadFile(findInShadowRoot(findInShadowRoot(importPage, "#attendees-file"), "#file-input"), csv);
        new WebDriverWait(driver, timeout, Duration.ofMillis(200))
            .withMessage(() -> "CSV not parsed")
            .until(_ -> findAllInShadowRoot(importPage, "#attendees-count").stream().anyMatch(badge -> badge.getText().equals(Integer.toString(expectedAttendees))));
        LOGGER.info("{} attendees parsed and displayed in {} ms", expectedAttendees, elapsedMillis(start));

        selectShoelaceOption(findInShadowRoot(importPage, "#existing-category"), categoryName);
        setShoelaceSwitch(findInShadowRoot(importPage, "#one-reservation-per-attendee"), contact == null);
        setShoelaceSwitch(findInShadowRoot(importPage, "#send-tickets"), false);
        if (contact != null) {
            typeInShoelaceControl(findInShadowRoot(importPage, "#contact-first-name"), "input", contact.firstName());
            typeInShoelaceControl(findInShadowRoot(importPage, "#contact-last-name"), "input", contact.lastName());
            typeInShoelaceControl(findInShadowRoot(importPage, "#contact-email"), "input", contact.email());
        }

        var submitted = System.nanoTime();
        clickWithJs(driver, findInShadowRoot(importPage, "#import-button"));
        new WebDriverWait(driver, Duration.ofMinutes(5))
            .withMessage(() -> "import not submitted: " + findAllInShadowRoot(importPage, "#submit-error").stream().map(WebElement::getText).findFirst().orElse("no error displayed"))
            .until(urlMatches("/reservation/import/[^/]+/status"));
        LOGGER.info("import request submitted in {} ms", elapsedMillis(submitted));

        var status = wait.until(presenceOfElementLocated(By.tagName("alfio-attendees-import-status")));
        var lastLog = new long[] { System.nanoTime() };
        new WebDriverWait(driver, timeout, Duration.ofSeconds(1))
            .withMessage(() -> "import not completed: " + importProgress(status))
            .until(_ -> {
                if (System.nanoTime() - lastLog[0] > Duration.ofMinutes(1).toNanos()) {
                    lastLog[0] = System.nanoTime();
                    LOGGER.info("import in progress after {} s: {}", elapsedMillis(submitted) / 1000, importProgress(status));
                }
                return !findAllInShadowRoot(status, "#import-result").isEmpty();
            });
        assertEquals("All the reservations have been created.", findInShadowRoot(status, "#import-result").getText().trim());
        var expectedReservations = contact == null ? expectedAttendees : 1;
        assertEquals(expectedReservations + " created", findInShadowRoot(status, "#imported-count").getText().trim());
        LOGGER.info("{} attendees imported in {} s", expectedAttendees, elapsedMillis(submitted) / 1000);
    }

    /**
     * Clicks "Download template" in the import page and returns the content of the generated file, byte order mark included.
     * The file is not saved: the download is intercepted in the page.
     */
    String downloadAttendeesImportTemplate(String slug) {
        driver.navigate().to(serverBaseUrl + "/admin#/events/" + slug + "/reservation/import");
        var importPage = wait.until(presenceOfElementLocated(By.tagName("alfio-attendees-import")));
        var button = findInShadowRoot(importPage, "#download-template");
        return (String) ((JavascriptExecutor) driver).executeAsyncScript("""
            const [button, done] = arguments;
            const createObjectURL = URL.createObjectURL;
            const click = HTMLAnchorElement.prototype.click;
            let blob;
            URL.createObjectURL = b => { blob = b; return createObjectURL.call(URL, b); };
            HTMLAnchorElement.prototype.click = function () { if (!this.download) { click.call(this); } };
            try {
                button.click();
            } finally {
                URL.createObjectURL = createObjectURL;
                HTMLAnchorElement.prototype.click = click;
            }
            // keeps the byte order mark, which Blob.text() would drop
            blob ? blob.arrayBuffer().then(content => done(new TextDecoder('utf-8', { ignoreBOM: true }).decode(content))) : done(null);
            """, button);
    }

    record ReservationContact(String firstName, String lastName, String email) {}

    private String importProgress(WebElement status) {
        return findAllInShadowRoot(status, "sl-progress-bar").stream().map(WebElement::getText).findFirst().orElse("no progress displayed");
    }

    /**
     * Opens the dropdown of a sl-select and clicks the option. Options react to mouse events, not to a scripted click
     */
    @SuppressWarnings("unchecked")
    private void selectShoelaceOption(WebElement select, String label) {
        scrollToCenter(select).click();
        var option = wait.until(d -> ((List<WebElement>) ((JavascriptExecutor) d).executeScript("return Array.from(arguments[0].querySelectorAll('sl-option'));", select))
            .stream()
            .filter(o -> o.isDisplayed() && o.getText().trim().equals(label))
            .findFirst()
            .orElse(null));
        option.click();
        wait.until(_ -> !String.valueOf(select.getDomProperty("value")).isEmpty());
    }

    private void setShoelaceSwitch(WebElement shoelaceSwitch, boolean checked) {
        wait.until(_ -> shoelaceSwitch.getDomAttribute("disabled") == null);
        if (Boolean.parseBoolean(shoelaceSwitch.getDomProperty("checked")) != checked) {
            clickWithJs(driver, shoelaceSwitch);
            wait.until(_ -> Boolean.parseBoolean(shoelaceSwitch.getDomProperty("checked")) == checked);
        }
    }

    static long elapsedMillis(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

    private WebElement openPendingPayments(String slug) {
        driver.navigate().to(serverBaseUrl + "/admin#/events/" + slug + "/pending-payments/");
        return wait.until(presenceOfElementLocated(By.tagName("alfio-pending-payments")));
    }

    private WebElement findInRow(WebElement host, String customerName, String selector) {
        return wait.until(_ -> shadowRows(host).stream()
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
        wait.until(_ -> dialog.getDomAttribute("open") != null);
        clickWithJs(driver, findInShadowRoot(dialogHost, "sl-button[variant='" + buttonVariant + "']"));
        wait.until(_ -> dialog.getDomAttribute("open") == null);
    }

    private void waitForEmptyState(WebElement host, String message) {
        new WebDriverWait(driver, Duration.ofSeconds(30))
            .ignoring(StaleElementReferenceException.class)
            .withMessage(() -> "expected empty state \"" + message + "\"")
            .until(_ -> findAllInShadowRoot(host, ".empty-state").stream().anyMatch(e -> e.getText().contains(message)));
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
        // browsers without constructable stylesheets (e.g. Safari < 16.4) get the styles as <style> elements in the shadow root:
        // skip them, otherwise the CSS ends up in the text
        let text = '';
        const walker = document.createTreeWalker(counter.shadowRoot, NodeFilter.SHOW_TEXT);
        while (walker.nextNode()) {
            if (walker.currentNode.parentElement?.tagName !== 'STYLE') {
                text += walker.currentNode.textContent;
            }
        }
        return [counter.count, text.trim().replace(/\\s+/g, ' ')];
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
        wait.until(_ -> input.isDisplayed());
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
            new WebDriverWait(driver, Duration.ofSeconds(10)).until(_ -> !shortName.getAttribute("value").isEmpty());
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
        for (var paymentProxy : event.paymentMethods()) {
            var checkbox = driver.findElement(By.xpath("//form[@name='editEvent']//div[contains(@class, 'checkbox')]/label[contains(normalize-space(.), '" + paymentMethodLabel(paymentProxy) + "')]/input"));
            if (!checkbox.isSelected()) {
                selectElement(scrollTo(driver, checkbox), browserWebDriver);
            }
        }
    }

    private static String paymentMethodLabel(PaymentProxy paymentProxy) {
        return switch (paymentProxy) {
            case STRIPE -> "Stripe: Credit cards";
            case ON_SITE -> "On site (cash) payment";
            case OFFLINE -> "Offline payment";
            default -> throw new IllegalArgumentException(paymentProxy + " is not supported in E2E tests");
        };
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
                           EnumSet<PaymentProxy> paymentMethods,
                           String categoryName) {
    }
}
