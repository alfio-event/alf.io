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
package alfio.config.support;

import alfio.manager.system.ConfigurationManager;
import alfio.manager.system.ConfigurationManager.MaybeConfiguration;
import alfio.model.system.ConfigurationKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.boot.web.server.Cookie;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import static alfio.config.Initializer.PROFILE_LIVE;
import static alfio.config.support.CustomCookieSameSiteSupplier.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomCookieSameSiteSupplierTest {

    private CustomCookieSameSiteSupplier sameSiteSupplier;
    private Environment environment;
    private MaybeConfiguration embedConfiguration;
    private HttpServletRequest request;
    private HttpSession session;

    @BeforeEach
    void setUp() {
        ConfigurationManager configurationManager = mock(ConfigurationManager.class);
        environment = mock(Environment.class);
        embedConfiguration = mock(MaybeConfiguration.class);
        request = mock(HttpServletRequest.class);
        session = mock(HttpSession.class);
        // default: everything is enabled
        when(environment.acceptsProfiles(Profiles.of(PROFILE_LIVE))).thenReturn(true);
        when(configurationManager.getForSystem(ConfigurationKeys.EMBED_ALLOWED_ORIGINS)).thenReturn(embedConfiguration);
        when(embedConfiguration.isPresent()).thenReturn(true);
        when(embedConfiguration.getRequiredValue()).thenReturn("demo.example.org");
        when(request.getHeader(HttpHeaders.REFERER)).thenReturn("demo.example.org");
        when(request.getHeader("Sec-Fetch-Dest")).thenReturn(IFRAME);
        when(request.getHeader("Sec-Fetch-Site")).thenReturn(CROSS_SITE);
        when(request.getRequestURI()).thenReturn("/api/v2/public/bla");
        when(request.getSession(false)).thenReturn(session);
        when(request.getSession()).thenReturn(session);
        when(session.getAttribute(EMBEDDED_SESSION_ATTRIBUTE)).thenReturn(IFRAME);
        sameSiteSupplier = new CustomCookieSameSiteSupplier(configurationManager, environment);
    }

    @ParameterizedTest
    @CsvSource({
        "https://alf.io,,same-site",
        "https://demo.example.org/demo,'example.org\ndemo.example.org',cross-site",
        "https://demo.example.org,'example.org\nhttps://demo.example.org',cross-site"
    })
    void referrerMatches(String referrer, String allowedOrigins, String secFetchSite) {
        var configuration = mock(MaybeConfiguration.class);
        when(configuration.getRequiredValue()).thenReturn(allowedOrigins);
        assertTrue(CustomCookieSameSiteSupplier.referrerMatches(referrer, configuration, secFetchSite));
    }

    @ParameterizedTest
    @CsvSource({
        "https://alf.io,,cross-site",
        "https://demo.example.org/demo,'example.org',cross-site",
        "https://demo.example.org,'example.org\nhttps://example.org',cross-site"
    })
    void referrerDoesNotMatch(String referrer, String allowedOrigins, String secFetchSite) {
        var configuration = mock(MaybeConfiguration.class);
        when(configuration.getRequiredValue()).thenReturn(allowedOrigins);
        assertFalse(CustomCookieSameSiteSupplier.referrerMatches(referrer, configuration, secFetchSite));
    }

    @Test
    void sameSiteNone() {
        var sessionCookie = new javax.servlet.http.Cookie("SESSION", "123");
        assertEquals(Cookie.SameSite.NONE, sameSiteSupplier.getSameSite(sessionCookie, () -> request));
        var xsrfCookie = new javax.servlet.http.Cookie(XSRF_TOKEN, "123");
        assertEquals(Cookie.SameSite.NONE, sameSiteSupplier.getSameSite(xsrfCookie, () -> request));
        verify(session).setAttribute(EMBEDDED_SESSION_ATTRIBUTE, IFRAME);
    }

    @Test
    void sameSiteNotApplicable() {
        when(environment.acceptsProfiles(Profiles.of(PROFILE_LIVE))).thenReturn(false);
        var sessionCookie = new javax.servlet.http.Cookie("SESSION", "123");
        assertNull(sameSiteSupplier.getSameSite(sessionCookie, () -> request));
    }

    @Nested
    @DisplayName("SameSite STRICT")
    class SameSiteStrict {

        @Test
        void embeddingNotEnabled() {
            var sessionCookie = new javax.servlet.http.Cookie("SESSION", "123");
            when(embedConfiguration.isPresent()).thenReturn(false);
            assertEquals(Cookie.SameSite.STRICT, sameSiteSupplier.getSameSite(sessionCookie, () -> request));
            var xsrfCookie = new javax.servlet.http.Cookie(XSRF_TOKEN, "123");
            assertEquals(Cookie.SameSite.STRICT, sameSiteSupplier.getSameSite(xsrfCookie, () -> request));
        }

        @Test
        void referrerDoesNotMatch() {
            var sessionCookie = new javax.servlet.http.Cookie("SESSION", "123");
            when(request.getHeader(HttpHeaders.REFERER)).thenReturn("https://demo2.example.org");
            assertEquals(Cookie.SameSite.STRICT, sameSiteSupplier.getSameSite(sessionCookie, () -> request));
        }

        @Test
        void destinationIsNotIframe() {
            var sessionCookie = new javax.servlet.http.Cookie("SESSION", "123");
            when(request.getHeader("Sec-Fetch-Dest")).thenReturn("none");
            assertEquals(Cookie.SameSite.STRICT, sameSiteSupplier.getSameSite(sessionCookie, () -> request));
            verify(session, never()).setAttribute(EMBEDDED_SESSION_ATTRIBUTE, IFRAME);
        }

        @Test
        void requestURIIsAdmin() {
            when(request.getRequestURI()).thenReturn("/api/admin");
            var xsrfCookie = new javax.servlet.http.Cookie(XSRF_TOKEN, "123");
            assertEquals(Cookie.SameSite.STRICT, sameSiteSupplier.getSameSite(xsrfCookie, () -> request));
        }

        @ParameterizedTest
        @NullAndEmptySource
        void sessionIsNotEmbedded(String embeddedSessionAttribute) {
            when(session.getAttribute(EMBEDDED_SESSION_ATTRIBUTE)).thenReturn(embeddedSessionAttribute);
            var xsrfCookie = new javax.servlet.http.Cookie(XSRF_TOKEN, "123");
            assertEquals(Cookie.SameSite.STRICT, sameSiteSupplier.getSameSite(xsrfCookie, () -> request));
        }
    }

}