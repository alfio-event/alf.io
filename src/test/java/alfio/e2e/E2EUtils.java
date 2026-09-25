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

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

final class E2EUtils {

    private E2EUtils() {
    }

    static void clickWithJs(WebDriver driver, WebElement element) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
    }

    static WebElement scrollTo(WebDriver driver, WebElement element) {
        if (driver instanceof JavascriptExecutor js) {
            js.executeScript("arguments[0].scrollIntoView();", element);
        }
        return element;
    }

    static void selectElement(WebElement element, NormalFlowE2ETest.BrowserWebDriver driver) {
        selectElement(element, driver, Keys.SPACE);
    }

    static void selectElement(WebElement element, NormalFlowE2ETest.BrowserWebDriver driver, Keys keyToSend) {
        if(driver.browser == NormalFlowE2ETest.BrowserWebDriver.Browser.SAFARI) {
            element.sendKeys(keyToSend);
        } else {
            // click with js...
            clickWithJs(driver.driver, element);
        }
    }
}
