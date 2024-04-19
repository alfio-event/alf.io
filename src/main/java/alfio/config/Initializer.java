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

import alfio.util.DefaultExceptionHandler;
import com.openhtmltopdf.util.XRLog;
import jakarta.servlet.FilterRegistration.Dynamic;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;

import java.util.Objects;
import java.util.logging.Level;

public class Initializer extends AbstractAnnotationConfigDispatcherServletInitializer {

    public static final String PROFILE_DEV = "dev";
    public static final String PROFILE_INTEGRATION_TEST = "integration-test";
    public static final String PROFILE_LIVE = "!dev";
    static final String PROFILE_SPRING_BOOT = "spring-boot";
    public static final String PROFILE_DEMO = "demo";
    public static final String PROFILE_OPENID = "openid";
    public static final String PROFILE_DISABLE_JOBS = "disable-jobs";
    public static final String API_V2_PUBLIC_PATH = "/api/v2/public/";
    public static final String XSRF_TOKEN = "XSRF-TOKEN";

    @Override
    public void onStartup(ServletContext servletContext) throws ServletException {
        super.onStartup(servletContext);

        Thread.setDefaultUncaughtExceptionHandler(new DefaultExceptionHandler());

        CharacterEncodingFilter cef = new CharacterEncodingFilter();
        cef.setEncoding("UTF-8");
        cef.setForceEncoding(true);

        Dynamic characterEncodingFilter = servletContext.addFilter("CharacterEncodingFilter", cef);
        characterEncodingFilter.setAsyncSupported(true);
        characterEncodingFilter.addMappingForUrlPatterns(null, false, "/*");

        //force log initialization, then disable it
        XRLog.setLevel(XRLog.EXCEPTION, Level.WARNING);
        XRLog.setLoggingEnabled(false);

    }

    @Override
    protected WebApplicationContext createRootApplicationContext() {
        ConfigurableWebApplicationContext ctx = ((ConfigurableWebApplicationContext) super.createRootApplicationContext());
        Objects.requireNonNull(ctx, "Something really bad is happening...");
        return ctx;
    }

    @Override
    protected Class<?>[] getRootConfigClasses() {
        return new Class<?>[] { ApplicationPropertiesConfiguration.class, DataSourceConfiguration.class, WebSecurityConfig.class };
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
