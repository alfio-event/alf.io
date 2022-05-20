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
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.Cookie;
import org.springframework.boot.web.servlet.server.CookieSameSiteSupplier;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

import static alfio.config.Initializer.PROFILE_LIVE;

public class CustomCookieSameSiteSupplier implements CookieSameSiteSupplier {

    public static final String XSRF_TOKEN = "XSRF-TOKEN";
    static final String CROSS_SITE = "cross-site";
    static final String EMBEDDED_SESSION_ATTRIBUTE = "ALFIO_EMBEDDED_SESSION";
    static final String IFRAME = "iframe";
    private static final Logger log = LoggerFactory.getLogger(CustomCookieSameSiteSupplier.class);
    private final Cache<ConfigurationKeys, MaybeConfiguration> configurationCache = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofSeconds(30))
        .build();
    private final ConfigurationManager configurationManager;
    private final Environment environment;

    public CustomCookieSameSiteSupplier(ConfigurationManager configurationManager, Environment environment) {
        this.configurationManager = configurationManager;
        this.environment = environment;
    }

    @Override
    public Cookie.SameSite getSameSite(javax.servlet.http.Cookie cookie) {
        return getSameSite(cookie, () -> {
            HttpServletRequest request = null;
            if(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes requestAttributes) {
                request = requestAttributes.getRequest();
            }
            return request;
        });
    }

    public Cookie.SameSite getSameSite(javax.servlet.http.Cookie cookie,
                                       Supplier<HttpServletRequest> requestSupplier) {
        var request = requestSupplier.get();
        if (!environment.acceptsProfiles(Profiles.of(PROFILE_LIVE)) || request == null) {
            return null;
        }
        var allowedOrigins = configurationCache.get(ConfigurationKeys.EMBED_ALLOWED_ORIGINS,
            k -> configurationManager.getForSystem(ConfigurationKeys.EMBED_ALLOWED_ORIGINS));
        if (allowedOrigins != null && allowedOrigins.isPresent()) {
            var referrer = request.getHeader(HttpHeaders.REFERER);
            var secFetchDest = request.getHeader("Sec-Fetch-Dest");
            var secFetchSite = request.getHeader("Sec-Fetch-Site");
            boolean embeddedSession = cookie.getName().equals("SESSION")
                && referrerMatches(referrer, allowedOrigins, secFetchSite)
                && isDestinationIframe(secFetchDest);
            if (embeddedSession) {
                // store the embedded flag in the session
                request.getSession(false).setAttribute(EMBEDDED_SESSION_ATTRIBUTE, IFRAME);
                if (isCrossSite(secFetchSite)) {
                    // request is marked as cross-site, therefore we enable cookie sharing explicitly
                    return Cookie.SameSite.NONE;
                } else {
                    // request is coming from the same (sub)domain, so LAX should be a sensible value
                    return Cookie.SameSite.LAX;
                }
            } else if(cookie.getName().equals(XSRF_TOKEN)
                && request.getRequestURI().startsWith("/api/v2/public/") // admin cannot be embedded yet
                && request.getSession(false) != null
                && IFRAME.equals(request.getSession(false).getAttribute(EMBEDDED_SESSION_ATTRIBUTE))) {
                return Cookie.SameSite.NONE;
            }
        }
        return Cookie.SameSite.STRICT;
    }

    static boolean referrerMatches(String referrer, MaybeConfiguration allowedOrigins, String secFetchSite) {
        if (!isCrossSite(secFetchSite)) {
            // request is not cross-site. Cookie can be shared if necessary
            return true;
        }
        try {
            var referrerURI = URI.create(referrer);
            var host = Objects.requireNonNullElse(referrerURI.getHost(), "");
            return allowedOrigins.getRequiredValue().contains(host);
        } catch (RuntimeException ex) {
            log.warn("error while parsing referrer", ex);
        }
        return false;
    }

    static boolean isCrossSite(String secFetchSite) {
        return secFetchSite == null || CROSS_SITE.equals(secFetchSite);
    }

    static boolean isDestinationIframe(String secFetchDest) {
        return secFetchDest == null || IFRAME.equals(secFetchDest);
    }
}
