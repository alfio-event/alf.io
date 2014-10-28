/**
 * This file is part of bagarino.
 *
 * bagarino is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bagarino is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.bagarino.filter;

import static java.util.Optional.ofNullable;
import static org.springframework.web.context.support.WebApplicationContextUtils.getRequiredWebApplicationContext;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;
import org.springframework.web.context.WebApplicationContext;

public class RedirectToHttpsFilter implements Filter {

	private boolean isProduction;

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		WebApplicationContext ctx = getRequiredWebApplicationContext(filterConfig.getServletContext());
		Environment env = ctx.getBean(Environment.class);
		isProduction = env.acceptsProfiles("!dev");
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException,
			ServletException {

		HttpServletRequest req = (HttpServletRequest) request;

		// redirecting if the request is not over https: note: this will work _only_ with the default port for https
		if (isProduction && !isOverHttps(req)) {
			HttpServletResponse resp = (HttpServletResponse) response;
			resp.sendRedirect(req.getRequestURL()
					.append(ofNullable(req.getQueryString()).map((x) -> "?" + x).orElse("")).toString()
					.replaceFirst("http", "https"));
			return;
		}

		//
		chain.doFilter(request, response);
	}

	@Override
	public void destroy() {
	}

	private static boolean isOverHttps(HttpServletRequest req) {
		return req.isSecure() || req.getRequestURL().toString().startsWith("https://")
				|| StringUtils.equals("https", req.getHeader("X-Forwarded-Proto"));
	}

}
