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
package alfio.filter;

import org.springframework.core.env.Environment;
import org.springframework.web.context.WebApplicationContext;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

import static org.springframework.web.context.support.WebApplicationContextUtils.getRequiredWebApplicationContext;

public class RedirectToHttpsFilter implements Filter {

    private Environment environment;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        WebApplicationContext ctx = getRequiredWebApplicationContext(filterConfig.getServletContext());
        environment = ctx.getBean(Environment.class);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException,
            ServletException {

//        HttpServletRequest req = (HttpServletRequest) request;
//
//        // redirecting if the request is not over https: note: this will work _only_ with the default port for https
//        if (environment.acceptsProfiles("!" + Initializer.PROFILE_HTTP) && !isOverHttps(req)) {
//            HttpServletResponse resp = (HttpServletResponse) response;
//            resp.sendRedirect(req.getRequestURL()
//                    .append(ofNullable(req.getQueryString()).map((x) -> "?" + x).orElse("")).toString()
//                    .replaceFirst("http", "https"));
//            return;
//        }

        //
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }

    private static boolean isOverHttps(HttpServletRequest req) {
        return req.isSecure() || req.getRequestURL().toString().startsWith("https://")
                || "https".equals(req.getHeader("X-Forwarded-Proto"));
    }

}
