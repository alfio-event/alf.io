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
package alfio.config.support.auth;

import alfio.manager.system.OpenIdAuthenticationManager;
import lombok.extern.log4j.Log4j2;
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
@Log4j2
public class OpenIdAuthenticationFilter extends GenericFilterBean {
    private final RequestMatcher requestMatcher;
    private final OpenIdAuthenticationManager openIdAuthenticationManager;

    public OpenIdAuthenticationFilter(String loginURL, OpenIdAuthenticationManager openIdAuthenticationManager) {
        this.requestMatcher = new AntPathRequestMatcher(loginURL, "GET");
        this.openIdAuthenticationManager = openIdAuthenticationManager;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        if (requestMatcher.matches(req)) {
            if (SecurityContextHolder.getContext().getAuthentication() != null || req.getParameterMap().containsKey("logout")) {
                res.sendRedirect("/admin/");
                return;
            }
            log.trace("calling buildAuthorizeUrl");
            res.sendRedirect(openIdAuthenticationManager.buildAuthorizeUrl());
            return;
        }

        chain.doFilter(request, response);
    }
}
