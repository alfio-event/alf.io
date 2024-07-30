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
package alfio.config.authentication.support;

import alfio.config.support.ContextAwareCookieSerializer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

public class PreAuthCookieWriterFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PreAuthCookieWriterFilter.class);
    private final ContextAwareCookieSerializer cookieSerializer;
    private final RequestMatcher requestMatcher;

    public PreAuthCookieWriterFilter(ContextAwareCookieSerializer cookieSerializer,
                                     RequestMatcher preAuthRequestMatcher) {
        this.cookieSerializer = cookieSerializer;
        this.requestMatcher = preAuthRequestMatcher;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (requestMatcher.matches(request)) {
            log.trace("Request matches. Adding PreAuth cookie.");
            var session = Objects.requireNonNull(request.getSession(false));
            cookieSerializer.writePreAuthCookieValue(new CookieSerializer.CookieValue(request, response, session.getId()));
        }
        filterChain.doFilter(request, response);
    }
}
