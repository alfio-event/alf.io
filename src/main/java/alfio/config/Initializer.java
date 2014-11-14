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
package alfio.config;

import alfio.filter.RedirectToHttpsFilter;
import lombok.extern.log4j.Log4j2;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;

import javax.servlet.FilterRegistration.Dynamic;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.SessionCookieConfig;

@Log4j2
public class Initializer extends AbstractAnnotationConfigDispatcherServletInitializer {

	@Override
	public void onStartup(ServletContext servletContext) throws ServletException {
		super.onStartup(servletContext);

		configureSessionCookie(servletContext);
		
		CharacterEncodingFilter cef = new CharacterEncodingFilter();
		cef.setEncoding("UTF-8");
		cef.setForceEncoding(true);
		
		Dynamic characterEncodingFilter = servletContext.addFilter("CharacterEncodingFilter", cef);
		characterEncodingFilter.setAsyncSupported(true);
		characterEncodingFilter.addMappingForUrlPatterns(null, false, "/*");

		Dynamic redirectFilter = servletContext.addFilter("RedirectToHttpsFilter", RedirectToHttpsFilter.class);
		redirectFilter.setAsyncSupported(true);
		redirectFilter.addMappingForUrlPatterns(null, false, "/*");

		if (System.getProperty("startDBManager") != null) {
			Class<?> cls;
			try {
				cls = ClassUtils.getClass("org.hsqldb.util.DatabaseManagerSwing");
				MethodUtils.invokeStaticMethod(cls, "main", new Object[] { new String[] { "--url", "jdbc:hsqldb:mem:alfio", "--noexit" } });
			} catch (ReflectiveOperationException e) {
				log.warn("error starting db manager", e);
			}
		}
	}

	private void configureSessionCookie(ServletContext servletContext) {
		SessionCookieConfig config = servletContext.getSessionCookieConfig();

		config.setHttpOnly(true);
		
		// set cookie https only too :D (TODO: check: I'm not able to get the Environment :( )
		config.setSecure(!StringUtils.contains(System.getProperty("spring.profiles.active"), "dev"));
		//
		

		// FIXME and CHECKME what a mess, ouch: https://issues.jboss.org/browse/WFLY-3448 ?
		config.setPath(servletContext.getContextPath() + "/");
		//
	}

	@Override
	protected Class<?>[] getRootConfigClasses() {
		return new Class<?>[] { DataSourceConfiguration.class, WebSecurityConfig.class };
	}

	@Override
	protected Class<?>[] getServletConfigClasses() {
		return new Class<?>[] { MvcConfiguration.class };
	}

	@Override
	protected String[] getServletMappings() {
		return new String[] { "/" };
	}
}
