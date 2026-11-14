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
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static alfio.e2e.E2EUtils.*;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        createEvent(event, false);
    }

    /**
     * Creates the event, optionally adding a second, access-restricted category ("Restricted", 5 seats,
     * fixed allocation): the restricted category gets one token per seat, which the invitations are sent for.
     */
    void createEvent(EventDefinition event, boolean withRestrictedCategory) {
        driver.navigate().to(serverBaseUrl + "/admin#/events/new");
        var page = wait.until(presenceOfElementLocated(By.tagName("alfio-event-create")));
        // waits for the form to be rendered after the initial data has loaded
        findInShadowRoot(page, "sl-select#organizationId");

        fillBasicInfo(page, event);
        fillUrls(page, event);
        uploadLogo(page);
        fillPrices(page, event);
        addCategory(page, event, false);
        if (withRestrictedCategory) {
            addCategory(page, event, true);
        }

        LOGGER.info("saving event {}", event.slug());
        clickWithJs(driver, findInShadowRoot(page, "#save-button"));
        new WebDriverWait(driver, Duration.ofSeconds(30))
            .withMessage(() -> "Event creation did not complete: " + ((JavascriptExecutor) driver).executeScript(
                "return Array.from(arguments[0].shadowRoot.querySelectorAll('#submit-error, .form-error-text, .error, .categories-error')).map(element => element.getAttribute('help-text') || element.textContent).join(' | ');", page))
            .until(urlContains("/events/" + event.slug() + "/detail"));
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
     * Creates an extension with the given name and script, from the extensions list.
     */
    void createExtension(String name, String script) {
        var list = openExtensionList();
        clickWithJs(driver, findInShadowRoot(list, "sl-button[variant='success']"));
        var edit = wait.until(presenceOfElementLocated(By.tagName("alfio-extension-edit")));
        waitForExtensionEditorReady(edit);
        typeInShoelaceControl(findInShadowRoot(edit, "sl-input[name='name']"), "input", name);
        // the editor is pre-filled with the sample script: replace it
        replaceScript(edit, script);
        LOGGER.info("creating extension {}", name);
        clickWithJs(driver, findInShadowRoot(edit, "sl-button[type='submit']"));
        wait.until(urlContains("/extension/list"));
    }

    /**
     * Checks that the given extension is displayed in the list with the expected enabled state.
     */
    void verifyExtension(String name, boolean enabled) {
        var list = openExtensionList();
        new WebDriverWait(driver, Duration.ofSeconds(30))
            .ignoring(StaleElementReferenceException.class)
            .withMessage(() -> "extension " + name + " is not displayed with the expected state")
            .until(_ -> {
                var row = extensionRow(list, name);
                if (row == null) {
                    return false;
                }
                // an enabled extension has an "enabled" badge, a disabled one has none
                var badge = (WebElement) ((JavascriptExecutor) driver).executeScript(
                    "return arguments[0].querySelector('sl-badge');", row);
                var displayedAsEnabled = badge != null && "enabled".equals(badge.getText().strip());
                return displayedAsEnabled == enabled;
            });
    }

    /**
     * Opens the editor of the given extension, checks the script content, sets the enabled state and saves.
     */
    void editExtension(String name, boolean enabled, String expectedScriptFragment) {
        var list = openExtensionList();
        var row = wait.until(_ -> extensionRow(list, name));
        clickWithJs(driver, rowButton(row, "Edit"));
        var edit = wait.until(presenceOfElementLocated(By.tagName("alfio-extension-edit")));
        waitForExtensionEditorReady(edit);
        var code = findInShadowRoot(findInShadowRoot(edit, "alfio-code-editor"), ".cm-content");
        new WebDriverWait(driver, Duration.ofSeconds(30))
            .withMessage(() -> "the script of extension " + name + " does not contain the expected content")
            .until(_ -> code.getText().contains(expectedScriptFragment));
        setShoelaceSwitch(findInShadowRoot(edit, "sl-switch"), enabled);
        LOGGER.info("saving extension {} as {}", name, enabled ? "enabled" : "disabled");
        clickWithJs(driver, findInShadowRoot(edit, "sl-button[type='submit']"));
        wait.until(urlContains("/extension/list"));
    }

    /**
     * Deletes the given extension from the list, after confirming the dialog.
     */
    void deleteExtension(String name) {
        var list = openExtensionList();
        var row = wait.until(_ -> extensionRow(list, name));
        clickWithJs(driver, rowButton(row, "Delete"));
        var dialog = findInShadowRoot(list, "sl-dialog#deleteDialog");
        wait.until(_ -> dialog.getDomAttribute("open") != null);
        // the footer buttons are slotted content of the dialog: they belong to the list, not to the dialog's shadow root
        clickWithJs(driver, findInShadowRoot(list, "sl-dialog#deleteDialog sl-button[variant='warning']"));
        new WebDriverWait(driver, Duration.ofSeconds(30))
            .ignoring(StaleElementReferenceException.class)
            .until(_ -> extensionRow(list, name) == null && dialog.getDomAttribute("open") == null);
        LOGGER.info("extension {} deleted", name);
    }

    /**
     * Best effort cleanup: deletes the extension if it is still present (e.g. after a failed test)
     */
    void deleteExtensionIfPresent(String name) {
        var list = openExtensionList();
        if (extensionRow(list, name) != null) {
            deleteExtension(name);
        }
    }

    /**
     * Opens the extensions log page and waits for the table (or the empty state) to be displayed.
     */
    void verifyExtensionLogs() {
        driver.navigate().to(serverBaseUrl + "/admin#/extension/log");
        var log = wait.until(presenceOfElementLocated(By.tagName("alfio-extension-log")));
        new WebDriverWait(driver, Duration.ofSeconds(30))
            .ignoring(StaleElementReferenceException.class)
            .withMessage(() -> "the extensions log page has not been displayed")
            .until(_ -> !findAllInShadowRoot(log, "tbody tr").isEmpty()
                || findAllInShadowRoot(log, ".empty-state").stream().anyMatch(e -> e.getText().contains("No log entries found")));
    }

    /**
     * Opens the "customize templates" page of the event and returns the list component.
     */
    WebElement openTemplateList(String slug) {
        driver.navigate().to(serverBaseUrl + "/admin#/events/" + slug + "/show-resources");
        return wait.until(presenceOfElementLocated(By.tagName("alfio-template-list")));
    }

    /**
     * Opens the editor of the given template of the event, by clicking its "Edit" button in the templates list.
     */
    WebElement openTemplateEdit(String slug, String template) {
        var list = openTemplateList(slug);
        var editButton = wait.until(_ -> findAllInShadowRoot(list, "sl-button.edit-template").stream()
            .filter(b -> template.equals(b.getDomAttribute("data-template")))
            .findFirst()
            .orElse(null));
        clickWithJs(driver, editButton);
        return wait.until(presenceOfElementLocated(By.tagName("alfio-template-edit")));
    }

    /**
     * In the template editor, appends a marker to the given locale, previews the rendered template
     * and saves the override. Waits until the locale tab shows the "customized" badge.
     */
    void editTemplateAndSave(String template, String locale, String marker) {
        var edit = wait.until(presenceOfElementLocated(By.tagName("alfio-template-edit")));
        var panel = "sl-tab-panel[name='" + locale + "']";
        var codeEditor = wait.until(_ -> findInShadowRoot(edit, panel + " alfio-code-editor"));
        var code = findInShadowRoot(codeEditor, ".cm-content");
        wait.until(_ -> !code.getText().isBlank());
        // set the content like pasting it: typing it would be changed by the editor (auto-indent, closing brackets).
        // The marker goes on the first line: CodeMirror only renders the visible part of the document
        ((JavascriptExecutor) driver).executeScript("arguments[0].value = arguments[1] + '\\n' + arguments[0].value;", codeEditor, marker);
        wait.until(_ -> code.getText().contains(marker));
        // for event templates, Save is enabled only after a successful preview
        clickWithJs(driver, findInShadowRoot(edit, panel + " sl-button[data-action='preview']"));
        var preview = wait.until(_ -> findInShadowRoot(edit, panel + " pre.preview"));
        wait.until(_ -> preview.getText().contains(marker));
        LOGGER.info("saving an override of {} ({}) with marker {}", template, locale, marker);
        clickWithJs(driver, findInShadowRoot(edit, panel + " sl-button[data-action='save']"));
        // after the save the data is reloaded: the tab shows that the locale has a customized version
        wait.until(_ -> findInShadowRoot(edit, "sl-tab[panel='" + locale + "'] .override-badge") != null);
    }

    /**
     * Checks that the editor of the given locale still contains the marker, e.g. after a reload.
     */
    void verifyTemplateContent(String locale, String marker) {
        var edit = wait.until(presenceOfElementLocated(By.tagName("alfio-template-edit")));
        var codeEditor = wait.until(_ -> findInShadowRoot(edit, "sl-tab-panel[name='" + locale + "'] alfio-code-editor"));
        var code = findInShadowRoot(codeEditor, ".cm-content");
        new WebDriverWait(driver, Duration.ofSeconds(30))
            .withMessage(() -> "the editor of locale " + locale + " does not contain " + marker)
            .until(_ -> code.getText().contains(marker));
    }

    /**
     * Deletes the override of the given locale, after confirming the dialog,
     * and waits until the "customized" badge of the locale tab disappears.
     */
    void deleteTemplateOverride(String locale) {
        var edit = wait.until(presenceOfElementLocated(By.tagName("alfio-template-edit")));
        var panel = "sl-tab-panel[name='" + locale + "']";
        clickWithJs(driver, findInShadowRoot(edit, panel + " sl-button[data-action='delete']"));
        // the confirmation dialog is appended to the document body and removed once closed; its
        // buttons get their click listeners only after the show animation (sl-after-show),
        // so click until the dialog is gone
        new WebDriverWait(driver, Duration.ofSeconds(30))
            .ignoring(StaleElementReferenceException.class)
            .withMessage(() -> "the confirmation dialog did not close")
            .until(_ -> {
                var dialog = driver.findElements(By.cssSelector("sl-dialog[label='Delete template override']")).stream().findFirst().orElse(null);
                if (dialog == null || dialog.getDomAttribute("open") == null) {
                    return true;
                }
                clickWithJs(driver, dialog.findElement(By.cssSelector("sl-button#confirm")));
                return false;
            });
        wait.until(_ -> (Boolean) ((JavascriptExecutor) driver).executeScript(
            "return arguments[0].shadowRoot.querySelector(arguments[1]) == null;", edit, "sl-tab[panel='" + locale + "'] .override-badge"));
        LOGGER.info("override of locale {} deleted", locale);
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
        uploadFile(findInShadowRoot(findInShadowRoot(importPage, ".attendees-file"), "#file-input"), csv);
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
        return downloadViaButton(findInShadowRoot(importPage, ".download-template"));
    }

    /**
     * Downloads the invitations template of the send-invitations page and returns its content.
     */
    String downloadSendInvitationsTemplate(String slug) {
        var page = openSendInvitations(slug);
        // the template download is in the "Upload CSV" panel
        clickWithJs(driver, findInShadowRoot(page, ".choice-card[data-value='csv']"));
        return downloadViaButton(findInShadowRoot(page, ".download-template"));
    }

    private String downloadViaButton(WebElement button) {
        var content = (String) ((JavascriptExecutor) driver).executeAsyncScript("""
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
            // returns the raw bytes: a decoded string may lose the byte order mark on the way back (e.g. on Safari)
            if (blob) {
                blob.arrayBuffer().then(content => done(btoa(String.fromCharCode(...new Uint8Array(content)))));
            } else {
                done(null);
            }
            """, button);
        return new String(Base64.getDecoder().decode(requireNonNull(content, "the template has not been downloaded")), StandardCharsets.UTF_8);
    }

    //
    // send invitations (Lit component, see frontend/admin/src/event/send-invitations)
    //

    /**
     * Opens the "send invitations" page of the first access-restricted category of the event,
     * through the link on the event detail page.
     */
    WebElement openSendInvitations(String slug) {
        openEventDetail(slug);
        var link = wait.until(presenceOfElementLocated(By.xpath("//a[contains(@data-ui-sref, 'sendInvitations')]")));
        clickWithJs(driver, link);
        return wait.until(presenceOfElementLocated(By.tagName("alfio-send-invitations")));
    }

    /**
     * Uploads the CSV file with the invitations on the "Invitations" tab, waits for the preview
     * and sends them.
     * @param csv the content of the file, with header: code, assignee, email, language
     */
    void sendInvitations(WebElement page, String csv, int expectedInvitations) {
        // switch to the "Upload CSV" panel
        clickWithJs(driver, findInShadowRoot(page, ".choice-card[data-value='csv']"));
        // the tokens of a restricted category become FREE when a periodic job generates their codes:
        // if the upload happens before that, the backend answers "available: 0" — wait and upload again.
        // a fresh temp file per attempt: the file input ignores a path it has already seen
        var deadline = System.currentTimeMillis() + Duration.ofSeconds(90).toMillis();
        while (true) {
            var file = tempFile("e2e-invitations", ".csv", csv.getBytes(StandardCharsets.UTF_8));
            var upload = findInShadowRoot(page, "alfio-file-upload");
            uploadFile(findInShadowRoot(upload, "#file-input"), file);
            try {
                new WebDriverWait(driver, Duration.ofSeconds(10))
                    .withMessage(() -> "CSV not parsed: " + findAllInShadowRoot(page, ".csv-error").stream().map(WebElement::getText).findFirst().orElse("no error displayed"))
                    .until(_ -> previewShows(page, expectedInvitations));
                break;
            } catch (org.openqa.selenium.TimeoutException e) {
                var error = findAllInShadowRoot(page, ".csv-error").stream().map(WebElement::getText).findFirst().orElse("");
                if (error.contains("available: 0") && System.currentTimeMillis() < deadline) {
                    LOGGER.info("tokens not generated yet, retrying the upload");
                    try {
                        Thread.sleep(Duration.ofSeconds(5).toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(ie);
                    }
                    continue;
                }
                throw e;
            }
        }
        LOGGER.info("{} invitations parsed, sending them", expectedInvitations);
        sendFromPreview(page);
    }

    record ManualInvitations(WebElement page, String pickedCode) {}

    /**
     * Types the invitees one by one in the "Insert manually" panel of the "Invitations" tab, picks one of
     * the free codes offered for the first invitee, clicks Review and sends the invitations from the preview.
     * @param invitees each entry is {assignee, email}
     * @return the page (reloaded, if the codes were still being generated when it first loaded) and the picked code
     */
    ManualInvitations addInviteesManually(WebElement page, List<String[]> invitees) {
        // the free codes are read when the page loads; the tokens of a restricted category become FREE when
        // a periodic job generates their codes, so reload until the select offers them
        var deadline = System.currentTimeMillis() + Duration.ofSeconds(90).toMillis();
        String pickedCode = null;
        while (pickedCode == null) {
            var codeSelect = findAllInShadowRoot(page, "sl-select#invitee-code-0").stream().findFirst().orElse(null);
            @SuppressWarnings("unchecked")
            var options = codeSelect == null ? List.<String>of() : (List<String>) ((JavascriptExecutor) driver).executeScript(
                "return Array.from(arguments[0].querySelectorAll('sl-option')).map(o => o.value).filter(v => v !== '');", codeSelect);
            if (!options.isEmpty()) {
                pickedCode = options.getFirst();
                selectShoelaceOptionByValue(codeSelect, pickedCode);
            } else {
                if (System.currentTimeMillis() >= deadline) {
                    throw new IllegalStateException("the form does not offer any free code");
                }
                LOGGER.info("free codes not generated yet, reloading the page");
                driver.navigate().refresh();
                page = wait.until(presenceOfElementLocated(By.tagName("alfio-send-invitations")));
                try {
                    Thread.sleep(Duration.ofSeconds(5).toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ie);
                }
            }
        }
        final var formPage = page;
        final var code = pickedCode;
        for (var i = 0; i < invitees.size(); i++) {
            if (i > 0) {
                clickWithJs(driver, findInShadowRoot(formPage, ".add-invitee"));
            }
            var invitee = invitees.get(i);
            typeInShoelaceControl(findInShadowRoot(formPage, "sl-input#invitee-assignee-" + i), "input", invitee[0]);
            typeInShoelaceControl(findInShadowRoot(formPage, "sl-input#invitee-email-" + i), "input", invitee[1]);
        }
        // the tokens of a restricted category become FREE when a periodic job generates their codes:
        // if Review happens before that, the backend answers "available: 0" — wait and click Review again
        while (true) {
            clickWithJs(driver, findInShadowRoot(formPage, "#review-button"));
            try {
                new WebDriverWait(driver, Duration.ofSeconds(10))
                    .withMessage(() -> "the manual form did not reach the preview: " + findAllInShadowRoot(formPage, ".review-error").stream().map(WebElement::getText).findFirst().orElse("no error displayed"))
                    .until(_ -> previewShows(formPage, invitees.size()));
                break;
            } catch (org.openqa.selenium.TimeoutException e) {
                var error = findAllInShadowRoot(formPage, ".review-error").stream().map(WebElement::getText).findFirst().orElse("");
                if (error.contains("available: 0") && System.currentTimeMillis() < deadline) {
                    LOGGER.info("tokens not generated yet, clicking Review again");
                    try {
                        Thread.sleep(Duration.ofSeconds(5).toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(ie);
                    }
                    continue;
                }
                throw e;
            }
        }
        LOGGER.info("{} invitations prepared from the form, sending them", invitees.size());
        sendFromPreview(formPage);
        return new ManualInvitations(formPage, code);
    }

    /**
     * Types two invitees in the "Insert manually" panel, picking the SAME free code in both code selects,
     * fills assignee and email of both rows, and clicks Review.
     * @return the page (reloaded, if the codes were still being generated when it first loaded)
     */
    WebElement addInviteesWithSameCode(WebElement page) {
        // the free codes are read when the page loads; the tokens of a restricted category become FREE when
        // a periodic job generates their codes, so reload until the select offers them
        var deadline = System.currentTimeMillis() + Duration.ofSeconds(90).toMillis();
        String code;
        while (true) {
            var codeSelect = findAllInShadowRoot(page, "sl-select#invitee-code-0").stream().findFirst().orElse(null);
            @SuppressWarnings("unchecked")
            var options = codeSelect == null ? List.<String>of() : (List<String>) ((JavascriptExecutor) driver).executeScript(
                "return Array.from(arguments[0].querySelectorAll('sl-option')).map(o => o.value).filter(v => v !== '');", codeSelect);
            if (!options.isEmpty()) {
                code = options.getFirst();
                break;
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new IllegalStateException("the form does not offer any free code");
            }
            LOGGER.info("free codes not generated yet, reloading the page");
            driver.navigate().refresh();
            page = wait.until(presenceOfElementLocated(By.tagName("alfio-send-invitations")));
            try {
                Thread.sleep(Duration.ofSeconds(5).toMillis());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ie);
            }
        }
        clickWithJs(driver, findInShadowRoot(page, ".add-invitee"));
        selectShoelaceOptionByValue(findInShadowRoot(page, "sl-select#invitee-code-0"), code);
        selectShoelaceOptionByValue(findInShadowRoot(page, "sl-select#invitee-code-1"), code);
        typeInShoelaceControl(findInShadowRoot(page, "sl-input#invitee-assignee-0"), "input", "Doe John");
        typeInShoelaceControl(findInShadowRoot(page, "sl-input#invitee-email-0"), "input", "john.doe@example.org");
        typeInShoelaceControl(findInShadowRoot(page, "sl-input#invitee-assignee-1"), "input", "Roe Jane");
        typeInShoelaceControl(findInShadowRoot(page, "sl-input#invitee-email-1"), "input", "jane.roe@example.org");
        clickWithJs(driver, findInShadowRoot(page, "#review-button"));
        return page;
    }

    /**
     * Waits until the given number of code selects of the manual form show a validation error,
     * and returns the errors by field id, with their help text.
     */
    Map<String, String> awaitCodeSelectErrors(WebElement page, int expectedCount) {
        return new WebDriverWait(driver, Duration.ofSeconds(15))
            .withMessage(() -> "expected " + expectedCount + " code field(s) with an error, got: " + codeSelectErrors(page))
            .until(_ -> {
                var errors = codeSelectErrors(page);
                return errors.size() == expectedCount ? errors : null;
            });
    }

    private Map<String, String> codeSelectErrors(WebElement page) {
        var errors = new LinkedHashMap<String, String>();
        for (var select : findAllInShadowRoot(page, "sl-select.invitee-code")) {
            if (String.valueOf(select.getDomAttribute("class")).contains("error")) {
                errors.put(String.valueOf(select.getDomAttribute("id")), String.valueOf(select.getDomAttribute("help-text")));
            }
        }
        return errors;
    }

    /**
     * Whether the review preview of the invitations is currently shown.
     */
    boolean previewIsShown(WebElement page) {
        return !findAllInShadowRoot(page, "#preview-count").isEmpty();
    }

    private boolean previewShows(WebElement page, int expectedInvitations) {
        return findAllInShadowRoot(page, "#preview-count").stream()
            .anyMatch(badge -> badge.getText().equals(Integer.toString(expectedInvitations)));
    }

    /**
     * Sends the invitations shown in the preview, and waits for the preview to be replaced by the input again.
     */
    private void sendFromPreview(WebElement page) {
        clickWithJs(driver, findInShadowRoot(page, "#send-button"));
        new WebDriverWait(driver, Duration.ofSeconds(30))
            .withMessage(() -> "invitations not sent: " + findAllInShadowRoot(page, "#send-error").stream().map(WebElement::getText).findFirst().orElse("no error displayed"))
            .until(_ -> findAllInShadowRoot(page, "#preview-count").isEmpty());
    }

    /**
     * Switches to the "History" tab and returns the codes of the invitations already sent.
     */
    List<String> sentInvitationCodes(WebElement page, int expected) {
        clickWithJs(driver, findInShadowRoot(page, "sl-tab[panel='history']"));
        return new WebDriverWait(driver, Duration.ofSeconds(30))
            .withMessage(() -> "history does not show " + expected + " sent codes")
            .until(_ -> {
                var rows = findAllInShadowRoot(page, ".sent-code");
                return rows.size() == expected
                    ? rows.stream().map(row -> row.findElement(By.cssSelector("td")).getText()).toList()
                    : null;
            });
    }

    /**
     * Clears the recipient of the first sent code of the "History" tab, confirming the dialog,
     * and waits for the row to disappear.
     * @param sentBefore how many codes are listed before the clear
     */
    void clearFirstRecipient(WebElement page, int sentBefore) {
        var row = wait.until(_ -> findAllInShadowRoot(page, ".sent-code").stream().findFirst().orElse(null));
        clickWithJs(driver, row.findElement(By.cssSelector(".clear-recipient")));
        // the confirm listener is attached only once the dialog has finished opening: click until it closes
        var confirmSelector = By.xpath("//sl-dialog[@label='Clear the recipient' and @open]//sl-button[@id='confirm']");
        new WebDriverWait(driver, Duration.ofSeconds(30))
            .withMessage(() -> "confirmation dialog did not close")
            .until(_ -> {
                var confirm = driver.findElements(confirmSelector);
                if (confirm.isEmpty()) {
                    return true;
                }
                clickWithJs(driver, confirm.getFirst());
                return driver.findElements(By.cssSelector("sl-dialog[open]")).isEmpty();
            });
        new WebDriverWait(driver, Duration.ofSeconds(30))
            .withMessage(() -> "recipient not cleared: rows=" + findAllInShadowRoot(page, ".sent-code").size()
                + ", open dialogs=" + driver.findElements(By.cssSelector("sl-dialog[open]")).size()
                + ", url=" + driver.getCurrentUrl())
            .until(_ -> findAllInShadowRoot(page, ".sent-code").size() == sentBefore - 1);
    }

    record ReservationContact(String firstName, String lastName, String email) {}

    record NewAttendee(String firstName, String lastName, String email) {
        String fullName() {
            return firstName + " " + lastName;
        }
    }

    /**
     * Tickets of a reservation, grouped by category
     */
    record ReservationTickets(String status, Map<String, List<String>> attendeesByCategory) {}

    /**
     * Creates a reservation from the "Create reservation" page, with two blocks of tickets:
     * the attendee in an existing category, and empty seats in a new restricted category.
     * @return the ID of the new reservation, once the page has moved to its detail
     */
    String createReservation(String slug,
                             ReservationContact contact,
                             NewAttendee attendee,
                             String existingCategoryName,
                             String newCategoryName,
                             int emptySeats) {
        driver.navigate().to(serverBaseUrl + "/admin#/events/" + slug + "/reservation/new");
        var page = wait.until(presenceOfElementLocated(By.tagName("alfio-reservation-create")));
        typeInShoelaceControl(findInShadowRoot(page, "#contact-first-name"), "input", contact.firstName());
        typeInShoelaceControl(findInShadowRoot(page, "#contact-last-name"), "input", contact.lastName());
        typeInShoelaceControl(findInShadowRoot(page, "#contact-email"), "input", contact.email());
        selectLanguageIfRequested(page);
        //
        // first block: the attendee, in an existing category
        fillNewAttendee(page, ".tickets-block[data-index='0']", attendee);
        selectShoelaceOption(findInShadowRoot(page, ".tickets-block[data-index='0'] .existing-category"), existingCategoryName);
        //
        // second block: empty seats, in a new restricted category
        clickWithJs(driver, findInShadowRoot(page, "#add-category"));
        var secondBlock = ".tickets-block[data-index='1']";
        chooseCard(page, secondBlock + " .attendee-strategy", "empty");
        var seats = findInShadowRoot(findInShadowRoot(page, secondBlock + " sl-input.seats"), "input");
        wait.until(_ -> seats.isDisplayed());
        clearInput(seats);
        seats.sendKeys(Integer.toString(emptySeats));
        chooseCard(page, secondBlock + " .category-type", "new");
        typeInShoelaceControl(findInShadowRoot(page, secondBlock + " .new-category-name"), "input", newCategoryName);
        typeInShoelaceControl(findInShadowRoot(page, secondBlock + " .new-category-price"), "input", "5");
        // the restricted category doesn't depend on the seats left unallocated by the existing ones
        setShoelaceSwitch(findInShadowRoot(page, secondBlock + " .add-seats"), true);

        LOGGER.info("creating a reservation for {} on {}", contact.lastName(), slug);
        return saveNewReservation(page);
    }

    /**
     * Creates a reservation from the "Create reservation" page, for an attendee of an existing category,
     * filling in the additional fields "company" (text) and "diet" (select)
     * @return the ID of the new reservation, once the page has moved to its detail
     */
    String createReservationWithAttendeeFields(String slug,
                                               ReservationContact contact,
                                               NewAttendee attendee,
                                               String categoryName,
                                               String company,
                                               String diet) {
        driver.navigate().to(serverBaseUrl + "/admin#/events/" + slug + "/reservation/new");
        var page = wait.until(presenceOfElementLocated(By.tagName("alfio-reservation-create")));
        typeInShoelaceControl(findInShadowRoot(page, "#contact-first-name"), "input", contact.firstName());
        typeInShoelaceControl(findInShadowRoot(page, "#contact-last-name"), "input", contact.lastName());
        typeInShoelaceControl(findInShadowRoot(page, "#contact-email"), "input", contact.email());
        selectLanguageIfRequested(page);
        var block = ".tickets-block[data-index='0']";
        fillNewAttendee(page, block, attendee);
        selectShoelaceOption(findInShadowRoot(page, block + " .existing-category"), categoryName);
        setShoelaceSwitch(findInShadowRoot(page, block + " .show-fields"), true);
        typeInShoelaceControl(findInShadowRoot(page, block + " .attendee-fields sl-input[data-field='company']"), "input", company);
        selectShoelaceOption(findInShadowRoot(page, block + " .attendee-fields sl-select[data-field='diet']"), diet);
        LOGGER.info("creating a reservation with additional fields for {} on {}", contact.lastName(), slug);
        return saveNewReservation(page);
    }

    /**
     * Creates a reservation from the "Create reservation" page, uploading the attendees and their additional fields
     * with the CSV template
     * @param csv the content of the file, header included
     * @param expectedAttendees the attendees displayed for review once the file has been read
     * @return the ID of the new reservation, once the page has moved to its detail
     */
    String createReservationFromCsv(String slug, ReservationContact contact, String categoryName, String csv, int expectedAttendees) {
        driver.navigate().to(serverBaseUrl + "/admin#/events/" + slug + "/reservation/new");
        var page = wait.until(presenceOfElementLocated(By.tagName("alfio-reservation-create")));
        typeInShoelaceControl(findInShadowRoot(page, "#contact-first-name"), "input", contact.firstName());
        typeInShoelaceControl(findInShadowRoot(page, "#contact-last-name"), "input", contact.lastName());
        typeInShoelaceControl(findInShadowRoot(page, "#contact-email"), "input", contact.email());
        selectLanguageIfRequested(page);
        var block = ".tickets-block[data-index='0']";
        selectShoelaceOption(findInShadowRoot(page, block + " .existing-category"), categoryName);
        chooseCard(page, block + " .attendee-strategy", "csv");
        var upload = wait.until(_ -> findAllInShadowRoot(page, block + " .attendees-file").stream().findFirst().orElse(null));
        uploadFile(findInShadowRoot(upload, "#file-input"), tempFile("e2e-reservation-attendees", ".csv", csv.getBytes(StandardCharsets.UTF_8)));
        // once read, the attendees can be reviewed as if they had been inserted manually
        new WebDriverWait(driver, Duration.ofSeconds(30))
            .withMessage(() -> "CSV not imported: " + findAllInShadowRoot(page, block + " .csv-error").stream().map(WebElement::getText).findFirst().orElse("no error displayed"))
            .until(_ -> findAllInShadowRoot(page, block + " .attendee-first-name").size() == expectedAttendees);
        LOGGER.info("creating a reservation from CSV for {} on {}", contact.lastName(), slug);
        return saveNewReservation(page);
    }

    /**
     * Adds a percentage discount code to the event, valid from yesterday to the day after tomorrow, through the API
     * used by the promo codes page
     */
    @SuppressWarnings("unchecked")
    void addDiscountCode(String slug, String code, int percentage) {
        openEventDetail(slug);
        var today = LocalDate.now();
        var result = (Map<String, Object>) ((JavascriptExecutor) driver).executeAsyncScript("""
            const [eventUrl, url, code, percentage, start, end, done] = arguments;
            const csrfHeader = document.querySelector('meta[name=_csrf_header]').getAttribute('content');
            const csrf = document.querySelector('meta[name=_csrf]').getAttribute('content');
            fetch(eventUrl, { credentials: 'include' })
                .then(r => r.json())
                .then(({ event }) => fetch(url, {
                    method: 'POST',
                    credentials: 'include',
                    headers: {'Content-Type': 'application/json', [csrfHeader]: csrf},
                    body: JSON.stringify({
                        promoCode: code,
                        // a code belongs either to the event or to the organization, as on the promo codes page
                        eventId: event.id,
                        organizationId: null,
                        currencyCode: event.currency,
                        start: { date: start, time: '00:00' },
                        end: { date: end, time: '23:59' },
                        discountAmount: percentage,
                        discountType: 'PERCENTAGE',
                        categories: [],
                        codeType: 'DISCOUNT',
                        description: code,
                        emailReference: ''
                    })
                }))
                .then(r => done({ status: r.status, body: r.ok ? '' : 'error' }), e => done({ status: 0, body: String(e) }));
            """, serverBaseUrl + "/admin/api/events/" + slug, serverBaseUrl + "/admin/api/promo-code", code, percentage,
            today.minusDays(1).toString(), today.plusDays(2).toString());
        assertEquals(200L, result.get("status"), () -> "cannot add discount code " + code + ": " + result);
        LOGGER.info("added discount code {} to {}", code, slug);
    }

    /**
     * Creates a reservation from the "Create reservation" page, for an attendee of an existing category, choosing
     * a discount code
     * @param discountOption the label of the discount code in the select
     * @return the ID of the new reservation, once the page has moved to its detail
     */
    String createReservationWithDiscount(String slug, ReservationContact contact, NewAttendee attendee, String categoryName, String discountOption) {
        driver.navigate().to(serverBaseUrl + "/admin#/events/" + slug + "/reservation/new");
        var page = wait.until(presenceOfElementLocated(By.tagName("alfio-reservation-create")));
        typeInShoelaceControl(findInShadowRoot(page, "#contact-first-name"), "input", contact.firstName());
        typeInShoelaceControl(findInShadowRoot(page, "#contact-last-name"), "input", contact.lastName());
        typeInShoelaceControl(findInShadowRoot(page, "#contact-email"), "input", contact.email());
        selectLanguageIfRequested(page);
        var block = ".tickets-block[data-index='0']";
        fillNewAttendee(page, block, attendee);
        selectShoelaceOption(findInShadowRoot(page, block + " .existing-category"), categoryName);
        selectShoelaceOption(findInShadowRoot(page, "#discount-code"), discountOption);
        LOGGER.info("creating a reservation with discount for {} on {}", contact.lastName(), slug);
        return saveNewReservation(page);
    }

    /**
     * The order summary of the reservation, as returned by the API used by the reservation detail
     * @return the total price, and the names of the summary rows by type
     */
    @SuppressWarnings("unchecked")
    Map<String, Object> orderSummary(String slug, String reservationId) {
        var result = (Map<String, Object>) ((JavascriptExecutor) driver).executeAsyncScript("""
            const [url, done] = arguments;
            fetch(url, { credentials: 'include' })
                .then(r => r.json())
                .then(r => done({
                    totalPrice: r.data.orderSummary.totalPrice,
                    promoCodeRows: r.data.orderSummary.summary.filter(row => row.type === 'PROMOTION_CODE').map(row => row.name)
                }), e => done({ error: String(e) }));
            """, serverBaseUrl + "/admin/api/reservation/event/" + slug + "/" + reservationId);
        assertEquals(null, result.get("error"), () -> "cannot load reservation " + reservationId);
        return result;
    }

    /**
     * Changes the additional fields "company" and "diet" of the only attendee of the reservation,
     * from the "Full data" dialog of the reservation detail
     */
    void editAttendeeFields(String slug, String reservationId, String company, String diet) {
        driver.navigate().to(serverBaseUrl + "/admin#/events/" + slug + "/reservation/" + reservationId);
        var reservationDetail = wait.until(presenceOfElementLocated(By.tagName("alfio-reservation-detail")));
        clickWithJs(driver, findInShadowRoot(reservationDetail, "tr[data-ticket-id] sl-button.full-data"));
        var dataDialog = findInShadowRoot(reservationDetail, "alfio-attendee-data-dialog");
        var slDialog = findInShadowRoot(dataDialog, "sl-dialog");
        wait.until(_ -> slDialog.getDomAttribute("open") != null);
        var companyInput = findInShadowRoot(findInShadowRoot(dataDialog, "sl-input[data-field='company']"), "input");
        wait.until(_ -> companyInput.isDisplayed());
        clearInput(companyInput);
        companyInput.sendKeys(company);
        selectShoelaceOption(findInShadowRoot(dataDialog, "sl-select[data-field='diet']"), diet);
        LOGGER.info("updating the additional fields of reservation {} on {}", reservationId, slug);
        clickWithJs(driver, findInShadowRoot(dataDialog, "#save-attendee-data"));
        new WebDriverWait(driver, Duration.ofSeconds(30))
            .withMessage(() -> "attendee data not saved: " + findAllInShadowRoot(dataDialog, ".save-error").stream().map(WebElement::getText).findFirst().orElse("dialog still open"))
            .until(_ -> slDialog.getDomAttribute("open") == null);
        var values = attendeeFieldValues(slug, reservationId);
        assertEquals(List.of(company), values.get("company"), () -> "unexpected values: " + values);
        assertEquals(List.of(diet), values.get("diet"), () -> "unexpected values: " + values);
    }

    /**
     * Reads the values of the additional fields of the first ticket of the reservation, through the API used by
     * the reservation detail
     * @return field name -> values
     */
    @SuppressWarnings("unchecked")
    Map<String, List<String>> attendeeFieldValues(String slug, String reservationId) {
        var reservationUrl = serverBaseUrl + "/admin/api/reservation/event/" + slug + "/" + reservationId;
        var result = (Map<String, Object>) ((JavascriptExecutor) driver).executeAsyncScript("""
            const [url, done] = arguments;
            fetch(url, { credentials: 'include' })
                .then(r => r.json())
                .then(r => fetch(url + '/ticket/' + r.data.ticketsByCategory[0].value[0].uuid + '/additional-fields', { credentials: 'include' }))
                .then(r => r.json())
                .then(values => done({ values }), e => done({ error: String(e) }));
            """, reservationUrl);
        assertEquals(null, result.get("error"), () -> "cannot load the additional fields of reservation " + reservationId);
        return (Map<String, List<String>>) result.get("values");
    }

    /**
     * Clicks Save in the empty "Create reservation" form
     * @return the validation errors displayed by the form, by control, once the form has refused to save
     */
    Map<String, String> saveEmptyReservation(String slug) {
        driver.navigate().to(serverBaseUrl + "/admin#/events/" + slug + "/reservation/new");
        var page = wait.until(presenceOfElementLocated(By.tagName("alfio-reservation-create")));
        clickWithJs(driver, findInShadowRoot(page, "#save-button"));
        var submitError = wait.until(_ -> findAllInShadowRoot(page, "#submit-error").stream().findFirst().orElse(null));
        assertEquals("Please fill in the fields highlighted in red.", submitError.getText().trim());
        // still on the form: nothing has been created
        assertTrue(requireNonNull(driver.getCurrentUrl()).endsWith("/reservation/new"), () -> "unexpected page: " + driver.getCurrentUrl());
        var errors = new LinkedHashMap<String, String>();
        findAllInShadowRoot(page, ".error").forEach(control -> {
            var name = Objects.requireNonNullElse(control.getDomAttribute("id"), "");
            if (name.isEmpty()) {
                name = Objects.requireNonNullElse(control.getDomAttribute("class"), "").replace("error", "").trim();
            }
            errors.put(name, Objects.requireNonNullElse(control.getDomAttribute("help-text"), ""));
        });
        return errors;
    }

    /**
     * Saves the "Create reservation" form and waits for the detail of the new reservation
     * @return the ID of the new reservation
     */
    private String saveNewReservation(WebElement page) {
        clickWithJs(driver, findInShadowRoot(page, "#save-button"));
        new WebDriverWait(driver, Duration.ofSeconds(30))
            .withMessage(() -> "reservation not created: " + findAllInShadowRoot(page, "#submit-error").stream().map(WebElement::getText).findFirst().orElse("no error displayed"))
            .until(urlMatches("/reservation/[^/?]+\\?fromCreation=true"));
        var reservationDetail = wait.until(presenceOfElementLocated(By.tagName("alfio-reservation-detail")));
        // the new reservation waits for the payment
        waitForReservationStatus(reservationDetail, "PENDING");
        var url = requireNonNull(driver.getCurrentUrl());
        return url.substring(url.lastIndexOf('/') + 1, url.indexOf('?', url.lastIndexOf('/')));
    }

    /**
     * Adds an attendee from the "Add new attendees" button of the Check-in page: the reservation is created
     * in a dialog (the attendee is also the contact person) and confirmed right away
     */
    void addAttendeeFromCheckIn(String slug, NewAttendee attendee, String categoryName) {
        var checkIn = openCheckIn(slug);
        clickWithJs(driver, findInShadowRoot(checkIn, "#add-attendees-button"));
        var dialog = findInShadowRoot(checkIn, "alfio-add-attendees-dialog");
        var form = findInShadowRoot(dialog, "alfio-reservation-create");
        selectLanguageIfRequested(form);
        fillNewAttendee(form, ".tickets-block[data-index='0']", attendee);
        selectShoelaceOption(findInShadowRoot(form, ".tickets-block[data-index='0'] .existing-category"), categoryName);
        LOGGER.info("adding {} from the Check-in page of {}", attendee.fullName(), slug);
        clickWithJs(driver, findInShadowRoot(form, "#save-button"));
        // the dialog closes once the reservation has been confirmed
        var slDialog = findInShadowRoot(dialog, "sl-dialog");
        new WebDriverWait(driver, Duration.ofSeconds(30))
            .withMessage(() -> "attendee not added: " + findAllInShadowRoot(form, "#submit-error").stream().map(WebElement::getText).findFirst().orElse("dialog still open"))
            .until(_ -> !Boolean.TRUE.equals(slDialog.getDomProperty("open")));
    }

    /**
     * Reads a reservation through the same API used by the reservation detail
     */
    @SuppressWarnings("unchecked")
    ReservationTickets reservationTickets(String slug, String reservationId) {
        var result = (Map<String, Object>) ((JavascriptExecutor) driver).executeAsyncScript("""
            const [url, done] = arguments;
            fetch(url, { credentials: 'include' })
                .then(r => r.json())
                .then(r => done({
                    status: r.data.reservation.status,
                    attendeesByCategory: Object.fromEntries(r.data.ticketsByCategory.map(entry => [
                        entry.key.name,
                        entry.value.map(t => [t.firstName, t.lastName].filter(Boolean).join(' '))
                    ]))
                }), e => done({ error: String(e) }));
            """, serverBaseUrl + "/admin/api/reservation/event/" + slug + "/" + reservationId);
        assertEquals(null, result.get("error"), () -> "cannot load reservation " + reservationId);
        return new ReservationTickets((String) result.get("status"), (Map<String, List<String>>) result.get("attendeesByCategory"));
    }

    private void fillNewAttendee(WebElement host, String block, NewAttendee attendee) {
        typeInShoelaceControl(findInShadowRoot(host, block + " .attendee-first-name"), "input", attendee.firstName());
        typeInShoelaceControl(findInShadowRoot(host, block + " .attendee-last-name"), "input", attendee.lastName());
        typeInShoelaceControl(findInShadowRoot(host, block + " .attendee-email"), "input", attendee.email());
    }

    /**
     * The language is asked only if the event has more than one
     */
    private void selectLanguageIfRequested(WebElement host) {
        var language = findAllInShadowRoot(host, "#language");
        if (!language.isEmpty()) {
            var firstOption = (String) ((JavascriptExecutor) driver).executeScript("return arguments[0].querySelector('sl-option').textContent.trim();", language.getFirst());
            selectShoelaceOption(language.getFirst(), firstOption);
        }
    }

    /**
     * Selects one of the cards rendered by choiceCards() (components/choice-cards.ts)
     */
    private void chooseCard(WebElement host, String group, String value) {
        clickWithJs(driver, findInShadowRoot(host, group + " .choice-card[data-value='" + value + "']"));
        wait.until(_ -> Boolean.parseBoolean(findInShadowRoot(host, group + " input[value='" + value + "']").getDomProperty("checked")));
    }

    private String importProgress(WebElement status) {
        return findAllInShadowRoot(status, "sl-progress-bar").stream().map(WebElement::getText).findFirst().orElse("no progress displayed");
    }

    /**
     * Resolves an option by label, then uses the same value-change event as the select.
     * Hoisted option text is not consistently exposed by Selenium's getText().
     */
    @SuppressWarnings("unchecked")
    private void selectShoelaceOption(WebElement select, String label) {
        var option = wait.until(d -> ((List<WebElement>) ((JavascriptExecutor) d).executeScript(
                "return Array.from(arguments[0].querySelectorAll('sl-option'));", select))
            .stream()
            .filter(o -> o.getDomProperty("textContent").trim().equals(label))
            .findFirst()
            .orElse(null));
        selectShoelaceOptionByValue(select, option.getDomProperty("value"));
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

    //
    // subscriptions (Lit components, see frontend/admin/src/subscriptions)
    //

    WebElement openSubscriptions() {
        driver.navigate().to(serverBaseUrl + "/admin#/subscriptions");
        // the page redirects to the list of the first organization
        return wait.until(presenceOfElementLocated(By.tagName("alfio-subscriptions-list")));
    }

    /**
     * Creates a multi-access subscription through the admin console. Returns its id, read from the
     * URL of the edit page the console lands on after the creation.
     */
    String createSubscription(String title) {
        var list = driver.findElement(By.tagName("alfio-subscriptions-list"));
        clickWithJs(driver, findInShadowRoot(list, "#add-subscription"));
        var edit = wait.until(presenceOfElementLocated(By.tagName("alfio-subscription-edit")));
        // wait for the form to be loaded
        wait.until(_ -> findInShadowRoot(edit, "alfio-multi-language-text[label='Title']") != null);

        typeInShoelaceControl(findMultiLanguageControl(edit, "Title", "sl-input"), "input", title);
        // the description is a markdown (WYSIWYG) editor
        var descriptionEditor = findMultiLanguageControl(edit, "Description", "alfio-markdown-editor");
        wait.until(_ -> findInShadowRoot(descriptionEditor, ".editor-host .ProseMirror")).sendKeys("Description of " + title);
        typeInShoelaceControl(findInShadowRoot(edit, "sl-input#termsAndConditionsUrl"), "input", "https://alf.io/terms");
        // the pricing and payment sections appear once a subscription type has been chosen
        chooseCard(edit, ".subscription-type", "multipleEntries");
        typeInShoelaceControl(findInShadowRoot(edit, "sl-input#maxEntries"), "input", "3");
        typeInShoelaceControl(findInShadowRoot(edit, "sl-input#price"), "input", "100");
        selectShoelaceOptionByValue(findInShadowRoot(edit, "sl-select#currency"), "CHF");
        selectShoelaceOption(findInShadowRoot(edit, "sl-select#vatStatus"), "Taxes included in the price");
        typeInShoelaceControl(findInShadowRoot(edit, "sl-input#vat"), "input", "7.7");
        // on sale from yesterday, so that the subscription is on sale now
        setDateTimeLocal(findInShadowRoot(edit, "sl-input#onSaleFrom"), LocalDate.now().minusDays(1) + "T10:00");
        // the first active online payment provider (bank transfer and on-site are not selectable)
        clickWithJs(driver, findInShadowRoot(edit, "sl-checkbox.payment-proxy"));
        uploadSubscriptionImage(edit);

        clickWithJs(driver, findInShadowRoot(edit, "#save-subscription"));
        wait.until(urlContains("/edit"));
        return driver.getCurrentUrl().replaceAll(".*#/subscriptions/\\d+/([0-9a-f-]{36})/edit.*", "$1");
    }

    boolean subscriptionListed(String title) {
        var list = wait.until(presenceOfElementLocated(By.tagName("alfio-subscriptions-list")));
        return wait.until(d -> (Boolean) ((JavascriptExecutor) d).executeScript(
            "return arguments[0].shadowRoot.textContent.includes(arguments[1]);", list, title));
    }

    /**
     * Opens the edit page of the subscription with the given title (the row links to the edit
     * page), and renames it
     */
    void renameSubscription(String title, String subscriptionId, String newTitle) {
        var list = driver.findElement(By.tagName("alfio-subscriptions-list"));
        var row = wait.until(d -> (WebElement) ((JavascriptExecutor) d).executeScript(
            "return Array.from(arguments[0].shadowRoot.querySelectorAll('.subscription-row'))" +
                ".find(r => r.textContent.includes(arguments[1]));", list, title));
        clickWithJs(driver, row);
        var edit = wait.until(presenceOfElementLocated(By.tagName("alfio-subscription-edit")));
        assertEquals(subscriptionId, edit.getDomAttribute("data-subscription-id"));
        var titleField = findMultiLanguageControl(edit, "Title", "sl-input");
        wait.until(_ -> !String.valueOf(findInShadowRoot(titleField, "input").getDomProperty("value")).isBlank());
        var input = findInShadowRoot(titleField, "input");
        input.clear();
        input.sendKeys(newTitle);
        clickWithJs(driver, findInShadowRoot(edit, "#save-subscription"));
        // the console stays on the edit page after an update
        wait.until(urlContains("/subscriptions/"));
    }

    /**
     * Publishes a subscription (visibility toggle on the edit page) and waits for the badge to change
     */
    void publishSubscription(String subscriptionId) {
        var edit = wait.until(presenceOfElementLocated(By.tagName("alfio-subscription-edit")));
        assertEquals(subscriptionId, edit.getDomAttribute("data-subscription-id"));
        clickWithJs(driver, findInShadowRoot(edit, ".toggle-visibility"));
        wait.until(d -> {
            var badge = findInShadowRoot(edit, "sl-badge.subscription-visibility");
            return badge != null && badge.getText().contains("Visible");
        });
    }

    private void uploadSubscriptionImage(WebElement edit) {
        try (var logo = AdminConsole.class.getResourceAsStream(LOGO_RESOURCE)) {
            var imagePicker = findInShadowRoot(edit, "alfio-purchase-context-image");
            var fileInput = findInShadowRoot(findInShadowRoot(imagePicker, "alfio-file-upload"), "#file-input");
            uploadFile(fileInput, tempFile("e2e-subscription-logo", ".png", requireNonNull(logo).readAllBytes()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        wait.until(_ -> findInShadowRoot(findInShadowRoot(edit, "alfio-purchase-context-image"), ".preview-box img") != null);
    }

    /**
     * Sets the value of a datetime-local sl-input: sendKeys doesn't work reliably on the native control
     */
    private void setDateTimeLocal(WebElement control, String value) {
        var input = findInShadowRoot(control, "input");
        ((JavascriptExecutor) driver).executeScript(
            "arguments[0].value = arguments[1]; arguments[0].dispatchEvent(new Event('input', { bubbles: true }));", input, value);
    }

    /**
     * Selects an option of a sl-select by its value: sets the value and emits sl-change,
     * since the options of a hoisted select don't react reliably to a scripted click
     */
    private void selectShoelaceOptionByValue(WebElement select, String value) {
        ((JavascriptExecutor) driver).executeScript(
            "arguments[0].value = arguments[1]; arguments[0].dispatchEvent(new Event('sl-change', { bubbles: true, composed: true }));",
            select, value);
        wait.until(_ -> value.equals(String.valueOf(select.getDomProperty("value"))));
    }

    /**
     * Matches the rows on their textContent: ChromeDriver's getText() of a row containing a component with a shadow root
     * (e.g. alfio-payment-method) returns only the text of that shadow root.
     * Since textContent includes the rows of hidden tabs too, the row must also contain the element we are looking for.
     */
    private WebElement findInRow(WebElement host, String customerName, String selector) {
        return new WebDriverWait(driver, Duration.ofSeconds(30))
            .withMessage(() -> "no row containing \"" + customerName + "\" and " + selector)
            .until(_ -> shadowRows(host).stream()
                .filter(row -> String.valueOf(row.getDomProperty("textContent")).contains(customerName))
                .map(row -> row.findElements(By.cssSelector(selector)))
                .filter(elements -> !elements.isEmpty())
                .findFirst()
                .map(List::getFirst)
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

    /**
     * The editor control (sl-input or sl-textarea) of the alfio-multi-language-text section with
     * the given label (e.g. "Title"), nested one shadow root deeper than the page
     */
    private WebElement findMultiLanguageControl(WebElement edit, String label, String control) {
        var field = findInShadowRoot(edit, "alfio-multi-language-text[label='" + label + "']");
        return findInShadowRoot(field, control);
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

    private WebElement openExtensionList() {
        driver.navigate().to(serverBaseUrl + "/admin#/extension/list");
        return wait.until(presenceOfElementLocated(By.tagName("alfio-extension-list")));
    }

    /**
     * The table row of the given extension, or null if it is not displayed
     */
    private WebElement extensionRow(WebElement list, String name) {
        return findAllInShadowRoot(list, "tbody tr").stream()
            .filter(row -> row.getText().contains(name))
            .findFirst()
            .orElse(null);
    }

    /**
     * Replaces the content of the script editor, like pasting the script.
     * Typing it is not an option: the editor closes the brackets and indents the lines, which would change the script
     */
    private void replaceScript(WebElement edit, String script) {
        var codeEditor = findInShadowRoot(edit, "alfio-code-editor");
        ((JavascriptExecutor) driver).executeScript("arguments[0].value = arguments[1];", codeEditor, script);
        var code = findInShadowRoot(codeEditor, ".cm-content");
        var firstLine = script.strip().lines().findFirst().orElseThrow();
        new WebDriverWait(driver, Duration.ofSeconds(30))
            .withMessage(() -> "the script has not been set in the editor")
            .until(_ -> code.getText().strip().startsWith(firstLine));
    }

    /**
     * The button of the given row with the given label, e.g. "Edit".
     * The text also contains the item name, hidden for screen readers ("Edit myExtension")
     */
    private WebElement rowButton(WebElement row, String label) {
        return row.findElements(By.cssSelector("sl-button")).stream()
            .filter(button -> button.getText().strip().startsWith(label))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("button " + label + " not found in row " + row.getText()));
    }

    private void waitForExtensionEditorReady(WebElement edit) {
        // the form is displayed once the extension (or the sample script) has been loaded
        wait.until(elementToBeClickable(findInShadowRoot(edit, "sl-input[name='name']")));
    }

    private void selectEventOrganizer(WebElement page) {
        var organizationSelect = findInShadowRoot(page, "sl-select#organizationId");
        if (Boolean.parseBoolean(organizationSelect.getDomProperty("disabled"))) {
            return;
        }
        if (organizationName != null && !organizationName.isBlank()) {
            selectShoelaceOption(organizationSelect, organizationName);
        } else {
            var firstOrganization = ((List<WebElement>) ((JavascriptExecutor) driver).executeScript(
                "return Array.from(arguments[0].querySelectorAll('sl-option'));", organizationSelect))
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No organization available for user " + username));
            selectShoelaceOptionByValue(organizationSelect, firstOrganization.getDomProperty("value"));
        }

    }

    void verifyEventCreationDraft() {
        driver.navigate().to(serverBaseUrl + "/admin#/events/new");
        var page = wait.until(presenceOfElementLocated(By.tagName("alfio-event-create")));
        selectEventOrganizer(page);
        typeInShoelaceControl(findInShadowRoot(page, "sl-input#displayName"), "input", "Draft recovery check");
        fillUrl(page, "privacyPolicyUrl", "https://example.org/privacy");
        var organizer = findInShadowRoot(page, "#organizationId").getDomProperty("value");
        driver.navigate().refresh();
        var restored = wait.until(presenceOfElementLocated(By.tagName("alfio-event-create")));
        assertEquals("Draft recovery check", findInShadowRoot(restored, "#displayName").getDomProperty("value"));
        assertEquals(organizer, findInShadowRoot(restored, "#organizationId").getDomProperty("value"));
        var privacy = findInShadowRoot(restored, "#privacyPolicyUrl");
        assertEquals("https://example.org/privacy", privacy.getDomProperty("value"));
        assertTrue(privacy.isDisplayed(), "The recommended privacy link must remain visible");
        assertTrue(findInShadowRoot(restored, "#event-logo").isDisplayed(), "The required event logo must remain visible");
        clickWithJs(driver, findInShadowRoot(restored, "#save-button"));
        wait.until(_ -> findInShadowRoot(restored, ".logo-error").isDisplayed());
        assertEquals("Please upload an event logo.", findInShadowRoot(restored, ".logo-error").getText().strip());
        assertEquals(3L, ((Number) ((JavascriptExecutor) driver).executeScript(
            "return arguments[0].shadowRoot.querySelectorAll('.form-section').length;", restored)).longValue());
    }

    private void fillBasicInfo(WebElement page, EventDefinition event) {
        selectEventOrganizer(page);
        var begin = LocalDate.now().plusDays(1).atTime(10, 0).toString();
        var end = LocalDate.now().plusDays(1).atTime(18, 0).toString();
        setDateTimeLocal(findInShadowRoot(page, "sl-input#begin"), begin);
        setDateTimeLocal(findInShadowRoot(page, "sl-input#end"), end);

        typeInShoelaceControl(findInShadowRoot(page, "sl-input#displayName"), "input", event.name());
        var location = findInShadowRoot(findInShadowRoot(page, "sl-textarea#location"), "textarea");
        location.sendKeys(event.location());
        // location is geocoded on blur, which can overwrite the time zone
        location.sendKeys(Keys.TAB);
        waitForGeolocation(page);
        selectBrowserTimeZone(page);

        // the description is a markdown editor inside the multi-language block
        var markdownEditor = findInShadowRoot(findInShadowRoot(page, "alfio-multi-language-text"), "alfio-markdown-editor");
        var description = wait.until(_ -> findInShadowRoot(markdownEditor, ".editor-host .ProseMirror"));
        description.sendKeys(event.description());

        // the event URL is generated from the name. Wait for it, then replace it with our slug
        var shortName = findInShadowRoot(page, "sl-input#shortName");
        try {
            new WebDriverWait(driver, Duration.ofSeconds(10)).until(_ -> !String.valueOf(shortName.getDomProperty("value")).isEmpty());
        } catch (TimeoutException _) {
            LOGGER.warn("event URL was not generated automatically");
        }
        var shortNameInput = findInShadowRoot(shortName, "input");
        clearInput(shortNameInput);
        shortNameInput.sendKeys(event.slug());
        shortNameInput.sendKeys(Keys.TAB);
    }

    private void waitForGeolocation(WebElement page) {
        try {
            // the loading indicator can be removed between the lookup and isDisplayed(): retry in that case
            new WebDriverWait(driver, Duration.ofSeconds(15))
                .ignoring(StaleElementReferenceException.class)
                .until(_ ->
                ((List<WebElement>) ((JavascriptExecutor) driver).executeScript(
                    "return Array.from(arguments[0].shadowRoot.querySelectorAll('.map-loading'));", page))
                    .stream().noneMatch(WebElement::isDisplayed));
        } catch (TimeoutException _) {
            LOGGER.warn("geolocation did not complete in time, continuing anyway");
        }
    }

    /**
     * The category goes on sale "now" according to the browser's clock, so the event time zone must match
     * the browser's one. Otherwise, the sale might start in the future.
     */
    private void selectBrowserTimeZone(WebElement page) {
        var timeZone = (String) ((JavascriptExecutor) driver).executeScript("return Intl.DateTimeFormat().resolvedOptions().timeZone;");
        try {
            selectShoelaceOption(findInShadowRoot(page, "sl-select#timeZone"), timeZone);
        } catch (NoSuchElementException | TimeoutException _) {
            LOGGER.warn("time zone {} not available, keeping the default one", timeZone);
        }
    }

    private void fillUrls(WebElement page, EventDefinition event) {
        fillUrl(page, "websiteUrl", event.websiteUrl());
        fillUrl(page, "termsAndConditionsUrl", event.termsAndConditionsUrl());
        fillUrl(page, "privacyPolicyUrl", event.privacyPolicyUrl());
    }

    /**
     * URL inputs normalize to https:// on blur.
     */
    private void fillUrl(WebElement page, String id, String url) {
        var element = findInShadowRoot(findInShadowRoot(page, "sl-input#" + id), "input");
        scrollToCenter(element).click();
        clearInput(element);
        element.sendKeys(url);
        element.sendKeys(Keys.TAB);
    }

    private void uploadLogo(WebElement page) {
        try (var logo = AdminConsole.class.getResourceAsStream(LOGO_RESOURCE)) {
            var fileInput = findInShadowRoot(findInShadowRoot(findInShadowRoot(page, "alfio-purchase-context-image"), "alfio-file-upload"), "input[type=file]");
            uploadFile(fileInput, tempFile("e2e-logo", ".png", requireNonNull(logo).readAllBytes()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // the preview appears in the image component once the upload completes
        wait.until(_ -> findInShadowRoot(findInShadowRoot(page, "alfio-purchase-context-image"), ".preview-box img") != null);
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

    private void fillPrices(WebElement page, EventDefinition event) {
        // the "Seats and payment info" section is displayed once the organization is selected
        typeInShoelaceControl(wait.until(_ -> findInShadowRoot(page, "sl-input#availableSeats")), "input", Integer.toString(event.maxTickets()));
        typeInShoelaceControl(findInShadowRoot(page, "sl-input#regularPrice"), "input", event.price());
        selectShoelaceOptionByValue(findInShadowRoot(page, "sl-select#currency"), event.currency());
        typeInShoelaceControl(findInShadowRoot(page, "sl-input#vatPercentage"), "input", event.taxPercentage());
        var vatIncluded = findInShadowRoot(page, "sl-switch#vatIncluded");
        if (Boolean.parseBoolean(String.valueOf(vatIncluded.getDomProperty("checked"))) != event.taxIncludedInPrice()) {
            setShoelaceSwitch(vatIncluded, event.taxIncludedInPrice());
        }
        for (var paymentProxy : event.paymentMethods()) {
            var checkbox = wait.until(_ -> ((List<WebElement>) ((JavascriptExecutor) driver).executeScript(
                    "return Array.from(arguments[0].shadowRoot.querySelectorAll('sl-checkbox.payment-proxy'));", page))
                .stream()
                .filter(c -> c.getText().contains(paymentMethodLabel(paymentProxy)))
                .findFirst()
                .orElse(null));
            if (!Boolean.parseBoolean(String.valueOf(checkbox.getDomProperty("checked")))) {
                clickWithJs(driver, scrollTo(driver, checkbox));
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

    private void addCategory(WebElement page, EventDefinition event, boolean restricted) {
        clickWithJs(driver, findInShadowRoot(page, "#add-category"));
        var categoryDialog = wait.until(_ -> findInShadowRoot(page, "alfio-event-category-dialog"));
        var dialog = wait.until(_ -> findInShadowRoot(categoryDialog, "sl-dialog"));
        // price is pre-filled with the event's regular price, and the category is on sale until the event starts
        typeInShoelaceControl(wait.until(_ -> findInShadowRoot(categoryDialog, "sl-input#name")), "input", restricted ? "Restricted" : event.categoryName());
        if (restricted) {
            // restricted access requires a fixed allocation: one token is generated per seat
            selectShoelaceOptionByValue(findInShadowRoot(categoryDialog, "#tokenGenerationRequested"), "hidden");
            selectShoelaceOptionByValue(findInShadowRoot(categoryDialog, "#bounded"), "fixed");
            typeInShoelaceControl(wait.until(_ -> findInShadowRoot(categoryDialog, "sl-input#maxTickets")), "input", "5");
        }
        clickWithJs(driver, findInShadowRoot(categoryDialog, "#category-save"));
        wait.until(_ -> !Boolean.parseBoolean(String.valueOf(dialog.getDomProperty("open"))));
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
