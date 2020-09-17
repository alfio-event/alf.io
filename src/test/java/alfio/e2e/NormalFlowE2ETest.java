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

import org.junit.Assert;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import static org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated;

import java.net.MalformedURLException;
import java.net.URL;

public class NormalFlowE2ETest {

    public static final String USERNAME = "";
    public static final String AUTOMATE_KEY = "";
    public static final String URL = "https://" + USERNAME + ":" + AUTOMATE_KEY + "@hub-cloud.browserstack.com/wd/hub";
    public static final String EVENT_URL = "https://test.alf.io/event/event-demo";

    private static final String EMAIL = "";

    private static WebDriver remoteDriver() throws MalformedURLException {
        DesiredCapabilities caps = new DesiredCapabilities();
        caps.setCapability("os", "Windows");
        caps.setCapability("os_version", "10");
        caps.setCapability("browser", "IE");
        caps.setCapability("browser_version", "11");
        caps.setCapability("name", "testFlowIE11");
        WebDriver driver = new RemoteWebDriver(new URL(URL), caps);
        return driver;
    }

    private static WebDriver localDriver() throws MalformedURLException {
        return new ChromeDriver();
    }

    //@Test
    public void testFlow() throws MalformedURLException, InterruptedException {


        WebDriver driver = remoteDriver();
        try {

            driver.navigate().to(EVENT_URL);

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

            WebElement fourthPageElem = wait.until(presenceOfElementLocated(By.cssSelector("div.attendees-data")));

            Assert.assertNotNull(fourthPageElem);
        } finally {
            driver.quit();
        }
    }

    private void page1TicketSelection(WebDriver driver) {
        // select 1 ticket
        WebElement dropdown = driver.findElement(By.cssSelector("select[formcontrolname=amount]"));
        dropdown.findElement(By.xpath("//option[. = '1']")).click();
        //
        // click continue button, submit form
        driver.findElement(By.cssSelector("button.btn-success[translate='show-event.continue']")).click();
    }

    private void page2ContactDetails(WebDriver driver, WebDriverWait wait) {
        driver.findElement(By.id("first-name")).sendKeys("Test");
        driver.findElement(By.id("last-name")).sendKeys("McTest");
        driver.findElement(By.id("email")).sendKeys(EMAIL);


        driver.findElement(By.cssSelector("label[for=invoiceTypePrivate]")).click();
        driver.findElement(By.id("billingAddressLine1")).sendKeys("via Fabrizia 10.3");
        driver.findElement(By.id("billingAddressZip")).sendKeys("6512");
        driver.findElement(By.id("billingAddressCity")).sendKeys("Giubiasco");


        driver.findElement(By.cssSelector("ng-select[formcontrolname='vatCountryCode']")).click();
        driver.findElement(By.cssSelector("ng-select[formcontrolname='vatCountryCode'] input[id=vatCountry]")).sendKeys("switzerland");
        wait.until(presenceOfElementLocated(By.cssSelector("ng-select[formcontrolname='vatCountryCode'] ng-dropdown-panel div[role=option]")));
        driver.findElement(By.cssSelector("ng-select[formcontrolname='vatCountryCode'] ng-dropdown-panel div[role=option]")).click();

        Assert.assertTrue(driver.findElement(By.cssSelector("ng-select[formcontrolname='vatCountryCode'] div.ng-value")).getText().contains("(CH)"));

        // insert data for attendee:
        // first&last name + email are already filled
        //we set the value only for the mandatory elements
        driver.findElements(By.cssSelector("app-additional-field label")).stream().filter(e -> e.getText().endsWith("*")).forEach(e -> {
            driver.findElement(By.id(e.getAttribute("for"))).sendKeys("A");
        });


        // submit
        driver.findElement(By.cssSelector("button[type=submit][translate='reservation-page.continue']")).click();
    }

    private void page3Payment(WebDriver driver, WebDriverWait wait) {
        driver.findElement(By.id("CREDIT_CARD-label")).click();
        wait.until(presenceOfElementLocated(By.cssSelector("iframe[allowpaymentrequest=true]")));
        driver.findElement(By.id("card-name")).sendKeys("Test McTest");
        driver.switchTo().frame(By.cssSelector("iframe[allowpaymentrequest=true]").findElement(driver));
        driver.findElement(By.name("cardnumber")).sendKeys("4242 4242 4242 4242");
        driver.findElement(By.name("exp-date")).sendKeys("12 / 20");
        driver.findElement(By.name("cvc")).sendKeys("123");
        driver.findElement(By.name("postal")).sendKeys("65000");
        driver.switchTo().defaultContent();
        driver.findElement(By.id("privacy-policy-label")).click();
        driver.findElement(By.id("terms-conditions-label")).click();

        //submit
        driver.findElement(By.cssSelector(".btn-success")).click();
    }
}
