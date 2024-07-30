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

import alfio.config.Initializer;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.Cookie;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

import java.util.List;

import static alfio.config.authentication.support.UserProvidedClientRegistrationRepository.OPENID_CALLBACK_PATH;

public class ContextAwareCookieSerializer implements CookieSerializer {

    private static final Logger log = LoggerFactory.getLogger(ContextAwareCookieSerializer.class);
    private static final String COOKIE_NAME = "ALFIO_SESSION";
    private static final String PRE_AUTH_COOKIE_NAME = COOKIE_NAME + "_PREAUTH";
    private final DefaultCookieSerializer defaultCookieSerializer;
    private final DefaultCookieSerializer preAuthCookieSerializer;
    private final RequestMatcher authenticationRequestMatcher;

    public ContextAwareCookieSerializer(Environment environment) {
        boolean live = environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_LIVE));
        var serializer = new DefaultCookieSerializer();
        serializer.setCookieName(COOKIE_NAME);
        if (live) {
            serializer.setSameSite(Cookie.SameSite.STRICT.attributeValue());
            serializer.setUseSecureCookie(true);

            // disable SameSite when the application is about to redirect to an (external) idP.
            // this will allow us to read the cookie when we come back with the auth code after a successful
            // authentication, or in case of errors.
            this.preAuthCookieSerializer = new DefaultCookieSerializer();
            this.preAuthCookieSerializer.setCookieName(PRE_AUTH_COOKIE_NAME);
            this.preAuthCookieSerializer.setUseSecureCookie(true);
            this.preAuthCookieSerializer.setSameSite(Cookie.SameSite.NONE.attributeValue());
            this.preAuthCookieSerializer.setCookieMaxAge(60 * 60);
        } else {
            this.preAuthCookieSerializer = null;
        }
        this.defaultCookieSerializer = serializer;
        this.authenticationRequestMatcher = new OrRequestMatcher(
            new AntPathRequestMatcher("/callback"),
            new AntPathRequestMatcher(OPENID_CALLBACK_PATH)
        );
    }

    @Override
    public void writeCookieValue(CookieValue cookieValue) {
        defaultCookieSerializer.writeCookieValue(cookieValue);
    }

    @Override
    public List<String> readCookieValues(HttpServletRequest request) {
        List<String> result = defaultCookieSerializer.readCookieValues(request);
        if (CollectionUtils.isEmpty(result) && preAuthCookieSerializer != null && authenticationRequestMatcher.matches(request)) {
            log.trace("Cannot load session cookie. Trying with PreAuth cookie.");
            result = preAuthCookieSerializer.readCookieValues(request);
            if (log.isTraceEnabled()) {
                log.trace("PreAuth cookie found: {}", CollectionUtils.size(result) > 0);
            }
        }
        return result;
    }

    public void writePreAuthCookieValue(CookieValue cookieValue) {
        if (preAuthCookieSerializer != null) {
            log.trace("Writing PreAuth cookie");
            preAuthCookieSerializer.writeCookieValue(cookieValue);
        }
    }
}
