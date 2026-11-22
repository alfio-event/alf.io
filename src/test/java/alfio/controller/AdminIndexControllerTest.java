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
package alfio.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdminIndexControllerTest {

    @ParameterizedTest
    @ValueSource(strings = {"/admin", "/admin/", "/admin/events/test/detail", "/admin/api-keys/", "/admin/configuration/system"})
    void rendersTheShellForAdminPages(String uri) {
        assertFalse(AdminIndexController.isNotAnAdminPage(new MockHttpServletRequest("GET", uri)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/api", "/admin/api/unknown", "/admin/i18n", "/admin/i18n/unknown"})
    void answersNotFoundForUnknownApiCalls(String uri) {
        assertTrue(AdminIndexController.isNotAnAdminPage(new MockHttpServletRequest("GET", uri)));
    }

    @Test
    void considersTheContextPath() {
        var request = new MockHttpServletRequest("GET", "/ctx/admin/api/unknown");
        request.setContextPath("/ctx");
        assertTrue(AdminIndexController.isNotAnAdminPage(request));
        var page = new MockHttpServletRequest("GET", "/ctx/admin/events/test/detail");
        page.setContextPath("/ctx");
        assertFalse(AdminIndexController.isNotAnAdminPage(page));
    }

    @Test
    void configExposesOnlyWhatTheShellNeeds() {
        var model = new HashMap<String, Object>();
        model.put("contextPath", "/ctx");
        model.put("username", "admin");
        model.put("isOwner", true);
        model.put("supportedLanguages", List.of());
        model.put("unrelated", "not exposed");

        var config = AdminIndexController.adminConfig(model);

        assertEquals("/ctx", config.get("contextPath"));
        assertEquals("admin", config.get("username"));
        assertEquals(true, config.get("isOwner"));
        assertFalse(config.containsKey("unrelated"));
    }
}
