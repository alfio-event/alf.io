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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.GenericFilterBean;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

public class OpenIdAuthenticationFilter extends GenericFilterBean {

    private static final Logger log = LoggerFactory.getLogger(OpenIdAuthenticationFilter.class);

    public static final String RESERVATION_KEY = "RESERVATION";
    public static final String CONTEXT_TYPE_KEY = "CONTEXT_TYPE";
    public static final String CONTEXT_ID_KEY = "CONTEXT_ID";
    private final RequestMatcher requestMatcher;
    private final OpenIdAuthenticationManager openIdAuthenticationManager;
    private final String redirectURL;
    private final boolean publicAuthentication;

    public OpenIdAuthenticationFilter(String loginURL,
                                      OpenIdAuthenticationManager openIdAuthenticationManager,
                                      String redirectURL,
                                      boolean publicAuthentication) {
        this.requestMatcher = new AntPathRequestMatcher(loginURL, "GET");
        this.openIdAuthenticationManager = openIdAuthenticationManager;
        this.redirectURL = redirectURL;
        this.publicAuthentication = publicAuthentication;
    }
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        if (checkRedirect(req)) {
            redirectToAuthorization(res, req);
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean checkRedirect(HttpServletRequest req) {
        return requestMatcher.matches(req) && openIdAuthenticationManager.isEnabled() && isUserPresent(req) && isPublicAuthenticationRequest(req);
    }

    private boolean isUserPresent(HttpServletRequest req) {
        return SecurityContextHolder.getContext().getAuthentication() == null || req.getParameterMap().containsKey("logout");
    }

    private boolean isPublicAuthenticationRequest(HttpServletRequest req) {
        var parameterMap = req.getParameterMap();
        return publicAuthentication && parameterMap.containsKey("reservation") && parameterMap.containsKey("contextType") && parameterMap.containsKey("id");
    }

    private void redirectToAuthorization(HttpServletResponse res, HttpServletRequest req) throws IOException {
        if (isPublicAuthenticationRequest(req)) {
            storePublicAuthenticationRequestInSession(req.getSession(), req.getParameter("reservation"), req.getParameter("contextType"), req.getParameter("id"));
        }

        log.trace("calling buildAuthorizeUrl");
        res.sendRedirect(openIdAuthenticationManager.buildAuthorizeUrl(UUID.randomUUID().toString()));
    }

    private void storePublicAuthenticationRequestInSession(HttpSession session, String reservation, String contextType, String id) {
        session.setAttribute(RESERVATION_KEY, reservation);
        session.setAttribute(CONTEXT_TYPE_KEY, contextType);
        session.setAttribute(CONTEXT_ID_KEY, id);
    }

}
