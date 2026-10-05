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

import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.Assertions;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static alfio.e2e.E2EUtils.*;
import static org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated;
import static org.openqa.selenium.support.ui.ExpectedConditions.urlContains;

/**
 * Drives the public event page the way an attendee would, in order to buy a ticket.
 */
class ReservationFlow {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReservationFlow.class);

    private final BrowserWebDriver browserWebDriver;
    private final WebDriver driver;
    private final WebDriverWait wait;
    private final String eventUrl;
    private final String email;

    ReservationFlow(BrowserWebDriver browserWebDriver, String eventUrl, String email) {
        this.browserWebDriver = browserWebDriver;
        this.driver = browserWebDriver.driver;
        this.wait = new WebDriverWait(driver, Duration.of(30, ChronoUnit.SECONDS));
        this.eventUrl = eventUrl;
        this.email = email;
    }

    /**
     * Buys a ticket and pays it by credit card, then waits until the payment has been confirmed.
     */
    void buyTicketWithCreditCard(String lastName) throws InterruptedException {
        reserveTicket(lastName);
        page3CreditCardPayment();
        Assertions.assertNotNull(waitForPaymentConfirmation());
    }

    /**
     * Buys a ticket and chooses to pay by bank transfer. The reservation is left waiting for the payment.
     */
    void buyTicketWithBankTransfer(String lastName) {
        reserveTicket(lastName);
        page3BankTransferPayment();
    }

    private void reserveTicket(String lastName) {
        driver.navigate().to(eventUrl);
        wait.until(presenceOfElementLocated(By.cssSelector("div.markdown-content")));
        page1TicketSelection();
        //wait until page is loaded
        wait.until(presenceOfElementLocated(By.cssSelector("h2[translate='reservation-page.your-details']")));
        //
        page2ContactDetails(lastName);
        //wait until page is loaded
        wait.until(presenceOfElementLocated(By.cssSelector("h2[translate='reservation-page.title']")));
    }

    private void page1TicketSelection() {
        // select 1 ticket
        WebElement dropdown = driver.findElement(By.cssSelector("select[formcontrolname=amount]"));
        dropdown.findElement(By.xpath("//option[. = '1']")).click();
        //
        // click continue button, submit form
        driver.findElement(By.id("show-event-continue")).sendKeys(Keys.RETURN);

    }

    private void page2ContactDetails(String lastName) {
        driver.findElement(By.id("first-name")).sendKeys("Test");
        driver.findElement(By.id("last-name")).sendKeys(lastName);
        driver.findElement(By.id("email")).sendKeys(email);

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


    private void page3CreditCardPayment() throws InterruptedException {
        selectElement(driver.findElement(By.id("CREDIT_CARD-label")), browserWebDriver);
        wait.until(presenceOfElementLocated(By.cssSelector("#card-element iframe")));
        driver.findElement(By.id("card-name")).sendKeys("Test McTest");
        driver.switchTo().frame(By.cssSelector("#card-element iframe").findElement(driver));
        wait.until(presenceOfElementLocated(By.name("cardnumber")));
        var cardNumberElement = driver.findElement(By.name("cardnumber"));
        sendSlowInput(cardNumberElement, "4000000400000008".chars().mapToObj(Character::toString).toArray(String[]::new));
        sendSlowInput(driver.findElement(By.name("exp-date")), "12", "30");
        driver.findElement(By.name("cvc")).sendKeys("123");
        driver.switchTo().defaultContent();
        acceptTermsAndSubmit();
    }

    /**
     * Payment providers confirm the payment through a webhook, which might not reach the server
     * (e.g. when running locally). In that case, we ask the server to check the payment status.
     */
    private WebElement waitForPaymentConfirmation() {
        var forceCheckLocator = By.cssSelector("button[translate='reservation.payment-processing.force-check']");
        return new WebDriverWait(driver, Duration.of(2, ChronoUnit.MINUTES), Duration.of(2, ChronoUnit.SECONDS))
            .ignoring(StaleElementReferenceException.class)
            .withMessage("payment has not been confirmed")
            .until(d -> {
                var confirmation = d.findElements(By.cssSelector("div.attendees-data"));
                if (!confirmation.isEmpty()) {
                    return confirmation.getFirst();
                }
                d.findElements(forceCheckLocator).stream()
                    .filter(WebElement::isDisplayed)
                    .findFirst()
                    .ifPresent(button -> {
                        LOGGER.info("payment confirmation is taking longer than expected, forcing check");
                        clickWithJs(d, button);
                    });
                return null;
            });
    }

    private void page3BankTransferPayment() {
        selectElement(driver.findElement(By.id("BANK_TRANSFER-label")), browserWebDriver);
        acceptTermsAndSubmit();
        // the reservation is now waiting for the payment
        wait.until(urlContains("/waiting-payment"));
        wait.until(presenceOfElementLocated(By.cssSelector(".alert-warning h2")));
    }

    private void acceptTermsAndSubmit() {
        driver.findElements(By.id("privacy-policy-label")).forEach(e -> selectElement(e, browserWebDriver));
        selectElement(driver.findElement(By.id("terms-conditions-label")), browserWebDriver);

        //submit
        driver.findElement(By.cssSelector(".btn-success")).sendKeys(Keys.RETURN);
    }


    private void sendSlowInput(WebElement element, CharSequence... strings) throws InterruptedException {
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
}
