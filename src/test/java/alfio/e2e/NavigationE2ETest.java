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
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the navigation of the admin, a single page application below /admin (frontend/admin/src/shell,
 * admin-router.ts): links of the AngularJS admin, leave confirmation, Back/Forward, links outside the admin,
 * expired session, the navbar on a phone.
 */
class NavigationE2ETest extends BaseE2ETest {

    private static final String REPLY_TO = "MAIL_REPLY_TO";

    @Override
    protected EnumSet<PaymentProxy> requiredPaymentProxies() {
        return EnumSet.of(PaymentProxy.OFFLINE);
    }

    @Test
    void linkOfTheAngularJsAdminOpensThePage() {
        adminConsole.login();
        var url = adminConsole.openLegacyLink("/events/" + slug + "/detail");
        assertTrue(url.endsWith("/admin/events/" + slug + "/detail"), () -> "unexpected URL: " + url);
        adminConsole.openEventDetailPage(slug);
    }

    @Test
    void unsavedChangesAskBeforeLeaving() {
        adminConsole.login();
        adminConsole.changeEventSettingWithoutSaving(slug, REPLY_TO, "reply-" + uniqueId() + "@example.com");
        var settingsUrl = adminConsole.currentUrl();

        // stay: the page and its URL don't change
        adminConsole.clickEventMenuLink("reservations", "alfio-settings-page");
        adminConsole.answerLeaveConfirmation(false);
        assertEquals(settingsUrl, adminConsole.currentUrl(), "the URL should be the one of the settings");

        // leave
        adminConsole.clickEventMenuLink("reservations", "alfio-settings-page");
        adminConsole.answerLeaveConfirmation(true);
        adminConsole.waitForUrlEnding("/events/" + slug + "/reservations/");
    }

    @Test
    void backAndForwardMoveBetweenPages() {
        adminConsole.login();
        adminConsole.openEventDetailPage(slug);
        adminConsole.clickEventMenuLink("reservations", "alfio-reservations-list");
        adminConsole.waitForUrlEnding("/events/" + slug + "/reservations/");

        adminConsole.back();
        adminConsole.waitForUrlEnding("/events/" + slug + "/detail");
        assertTrue(adminConsole.isPageDisplayed("alfio-event-detail"), "Back should show the event detail again");

        adminConsole.forward();
        adminConsole.waitForUrlEnding("/events/" + slug + "/reservations/");
        assertTrue(adminConsole.isPageDisplayed("alfio-reservations-list"), "Forward should show the reservations again");
    }

    @Test
    void linkOutsideTheAdminIsLoadedByTheBrowser() {
        adminConsole.login();
        adminConsole.openEventDetailPage(slug);
        adminConsole.clickLinkOutsideTheAdmin("/event/" + slug);
        adminConsole.waitForUrlEnding("/event/" + slug);
        assertFalse(adminConsole.isAdminShellDisplayed(), "the public page should have been loaded");
    }

    @Test
    void expiredSessionLeadsToTheLogin() {
        adminConsole.login();
        adminConsole.openEventDetailPage(slug);
        adminConsole.expireSession();
        // the next page loads its data: the server refuses it, and the admin reloads to the login page
        adminConsole.clickEventMenuLink("reservations", "alfio-reservations-list");
        adminConsole.waitForLoginPage();
        assertFalse(adminConsole.isAdminShellDisplayed(), "the admin should have been replaced by the login page");
    }

    @Test
    void navbarOnAPhone() {
        adminConsole.login();
        var size = adminConsole.windowSize();
        try {
            adminConsole.resizeWindow(400, 900);
            adminConsole.openEventDetailPage(slug);
            adminConsole.clickNavbarLink("Events");
            adminConsole.waitForUrlEnding("/admin/");
            assertFalse(adminConsole.isNavbarDrawerOpen(), "the drawer should close after the navigation");
        } finally {
            adminConsole.resizeWindow(size.getWidth(), size.getHeight());
        }
    }
}
