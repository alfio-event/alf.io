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
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * This filter is responsible for propagating information to the front-end.
 * The front-end will be able to access those information without polling.
 */
public class HeaderPublisherFilter implements Filter {

    private final ConfigurationManager configurationManager;

    public HeaderPublisherFilter(ConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
    }

    @Override
    public void doFilter(ServletRequest servletRequest,
                         ServletResponse servletResponse,
                         FilterChain filterChain) throws IOException, ServletException {
        var httpServletRequest = (HttpServletRequest) servletRequest;
        var httpServletResponse = (HttpServletResponse) servletResponse;

        if (httpServletRequest.getMethod().equals("GET") && httpServletRequest.getRequestURI().startsWith("/api/v2")) {
            httpServletResponse.setHeader("Alfio-OpenId-Enabled", Boolean.toString(configurationManager.isPublicOpenIdEnabled()));
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }
}
