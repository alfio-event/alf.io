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
package alfio.config.authentication;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SavedRequestMatcherTest {

    private static final String BROWSER_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
    private static final String ANGULARJS_ACCEPT = "application/json, text/plain, */*";

    private final RequestMatcher matcher = AbstractFormBasedWebSecurity.savedRequestMatcher();

    @Test
    void savePageNavigation() {
        assertTrue(matcher.matches(request("GET", "/admin/", BROWSER_ACCEPT)));
        assertTrue(matcher.matches(request("GET", "/admin", "*/*")));
    }

    @Test
    void doNotSaveAdminApiCallFiredAfterLogout() {
        // AngularJS does not send X-Requested-With on GET requests
        assertFalse(matcher.matches(request("GET", "/admin/api/configuration/events/test/single/USE_PARTNER_CODE_INSTEAD_OF_PROMOTIONAL", ANGULARJS_ACCEPT)));
        assertFalse(matcher.matches(request("GET", "/admin/api/events/test", "*/*")));
    }

    @Test
    void doNotSaveJsonRequest() {
        assertFalse(matcher.matches(request("GET", "/api/v2/public/event/test", ANGULARJS_ACCEPT)));
        assertFalse(matcher.matches(request("GET", "/export/test", "application/json")));
    }

    @Test
    void doNotSaveXhr() {
        var request = request("GET", "/admin/", BROWSER_ACCEPT);
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        assertFalse(matcher.matches(request));
    }

    @Test
    void doNotSaveNonGetRequest() {
        assertFalse(matcher.matches(request("POST", "/admin/", BROWSER_ACCEPT)));
    }

    private static MockHttpServletRequest request(String method, String uri, String accept) {
        var request = new MockHttpServletRequest(method, uri);
        request.addHeader("Accept", accept);
        return request;
    }
}
