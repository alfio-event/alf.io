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

import alfio.manager.openid.OpenIdAuthenticationManager;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Log4j2
public class OpenIdCallbackLoginFilter extends AbstractAuthenticationProcessingFilter {

    private final RequestMatcher requestMatcher;
    private final OpenIdAuthenticationManager openIdAuthenticationManager;

    public OpenIdCallbackLoginFilter(OpenIdAuthenticationManager openIdAuthenticationManager,
                                     AntPathRequestMatcher requestMatcher,
                                     AuthenticationManager authenticationManager) {
        super(requestMatcher);
        this.setAuthenticationManager(authenticationManager);
        this.requestMatcher = requestMatcher;
        this.openIdAuthenticationManager = openIdAuthenticationManager;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        if (requestMatcher.matches(req)) {
            log.info("request URL matches {}", req.getRequestURI());
            super.doFilter(req, res, chain);
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
        String code = request.getParameter("code");
        if (code == null) {
            throw new SessionAuthenticationException("authorization code cannot be null");
        }
        logger.trace("Received code. Attempting to exchange it with an access Token");
        var user = openIdAuthenticationManager.authenticateUser(code, request.getSession());
        return getAuthenticationManager().authenticate(user);
    }
}
