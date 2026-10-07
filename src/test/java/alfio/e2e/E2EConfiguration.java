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

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.LocalFileDetector;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import static java.util.Map.entry;

@Configuration(proxyBeanMethods = false)
class E2EConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(E2EConfiguration.class);

    private static WebDriver buildRemoteDriver(URL url,
                                               String os,
                                               String osVersion,
                                               String browser,
                                               String browserVersion,
                                               String profileName,
                                               String sessionName) {
        DesiredCapabilities caps = new DesiredCapabilities();
        caps.setCapability("browserName", browser);
        caps.setCapability("bstack:options", Map.ofEntries(
            entry("os", os),
            entry("osVersion", osVersion),
            entry("browserVersion", browserVersion),
            entry("buildName", profileName),
            entry("sessionName", sessionName),
            entry("consoleLogs", "errors"),
            entry("networkLogs", "true"),
            entry("seleniumVersion", "4.16.1"),
            entry("idleTimeout", "180")
        ));
        var driver = new RemoteWebDriver(url, caps);
        // upload local files to the remote browser. Must be set before looking up elements, since they copy the detector
        driver.setFileDetector(new LocalFileDetector());
        return driver;
    }

    private static String browserStackUrl(Environment env) {
        return "https://"
            + env.getRequiredProperty("browserstack.username")
            + ":"
            + env.getRequiredProperty("browserstack.access.key")
            + "@hub-cloud.browserstack.com/wd/hub";
    }

    private static BrowserWebDriver build(String browser, URL url, String githubBuildNumber, String sessionName) {
        return switch (browser) {
            case "chrome" ->
                    new BrowserWebDriver(BrowserWebDriver.Browser.CHROME, buildRemoteDriver(url, "Windows", "10", "Chrome", "latest", "testFlowChrome" + githubBuildNumber, sessionName));
            case "firefox" ->
                    new BrowserWebDriver(BrowserWebDriver.Browser.FIREFOX, buildRemoteDriver(url, "Windows", "10", "Firefox", "latest", "testFlowFirefox" + githubBuildNumber, sessionName));
            case "safari" ->
                    new BrowserWebDriver(BrowserWebDriver.Browser.SAFARI, buildRemoteDriver(url, "OS X", "Big Sur", "Safari", "14.1", "testFlowSafari" + githubBuildNumber, sessionName));
            default -> throw new IllegalStateException("unknown browser" + browser);
        };
    }

    @Bean
    BrowserWebDriver.Factory browserWebDriverFactory(Environment env) throws MalformedURLException {
        if(BaseE2ETest.CI_RUN) {
            var browser = env.getRequiredProperty("e2e.browser");
            LOGGER.info("e2e profile detected, CI profile detected. Running full suite on BrowserStack");
            var url = new URL(browserStackUrl(env));
            var githubBuildNumber = "-" + env.getProperty("github.run.number", "NA");
            return sessionName -> build(browser, url, githubBuildNumber, sessionName);
        } else {
            LOGGER.info("e2e profile detected, outside of CI. Using local ChromeDriver");
            return _ -> new BrowserWebDriver(BrowserWebDriver.Browser.CHROME, new ChromeDriver());
        }
    }
}
