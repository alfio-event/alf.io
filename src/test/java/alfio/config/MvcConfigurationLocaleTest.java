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
package alfio.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The language of the admin UI: the one chosen with the language picker, then the browser's, then English.
 */
class MvcConfigurationLocaleTest {

    private LocaleResolver localeResolver;
    private List<MappedInterceptor> interceptors;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        var configuration = new MvcConfiguration(new MockEnvironment(), JsonMapper.builder().build());
        localeResolver = configuration.localeResolver();
        var registry = new ExposedInterceptorRegistry();
        configuration.addInterceptors(registry);
        interceptors = registry.mappedInterceptors();
        session = new MockHttpSession();
    }

    @Test
    void usesTheFirstSupportedLanguageOfTheBrowser() {
        assertEquals("fr", resolve(request("/admin", "ja, fr-CH, de;q=0.8")).getLanguage());
    }

    @Test
    void fallsBackToEnglish() {
        assertEquals(Locale.ENGLISH, resolve(request("/admin", "ja, ko;q=0.8")));
        assertEquals(Locale.ENGLISH, resolve(request("/admin", null)));
    }

    @Test
    void theLanguageChosenWithThePickerStaysForTheSession() throws Exception {
        visit(request("/admin/events?lang=de", "fr"));

        assertEquals(Locale.GERMAN, resolve(request("/admin/events", "fr")));
        assertEquals(Locale.GERMAN, resolve(request("/admin/api/events", "fr")), "the API must answer in the same language");
    }

    @Test
    void anInvalidLanguageIsIgnored() throws Exception {
        visit(request("/admin?lang=de", "fr"));
        visit(request("/admin?lang=not a language!", "fr"));

        assertEquals(Locale.GERMAN, resolve(request("/admin", "fr")));
    }

    @Test
    void theApiCannotChangeTheLanguage() throws Exception {
        visit(request("/admin/api/events?lang=de", "fr"));

        assertEquals("fr", resolve(request("/admin", "fr")).getLanguage());
    }

    private Locale resolve(MockHttpServletRequest request) {
        return localeResolver.resolveLocale(request);
    }

    private void visit(MockHttpServletRequest request) throws Exception {
        var response = new MockHttpServletResponse();
        for (var interceptor : interceptors) {
            if (interceptor.matches(request)) {
                assertTrue(interceptor.preHandle(request, response, new Object()));
            }
        }
    }

    private MockHttpServletRequest request(String uri, String acceptLanguage) {
        var path = uri;
        String query = null;
        if (uri.contains("?")) {
            path = uri.substring(0, uri.indexOf('?'));
            query = uri.substring(uri.indexOf('?') + 1);
        }
        var request = new MockHttpServletRequest("GET", path);
        if (query != null) {
            request.setQueryString(query);
            var parameter = query.split("=", 2);
            request.addParameter(parameter[0], parameter[1]);
        }
        if (acceptLanguage != null) {
            request.addHeader("Accept-Language", acceptLanguage);
        }
        request.setSession(session);
        request.setAttribute(DispatcherServlet.LOCALE_RESOLVER_ATTRIBUTE, localeResolver);
        // as the DispatcherServlet does, for matching the paths of the interceptors
        ServletRequestPathUtils.parseAndCache(request);
        return request;
    }

    private static class ExposedInterceptorRegistry extends InterceptorRegistry {
        List<MappedInterceptor> mappedInterceptors() {
            return getInterceptors().stream().map(MappedInterceptor.class::cast).toList();
        }
    }
}
