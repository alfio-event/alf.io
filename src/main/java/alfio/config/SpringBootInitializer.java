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

import com.openhtmltopdf.util.XRLog;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.server.ErrorPageRegistrar;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.CharacterEncodingFilter;

import javax.servlet.Filter;
import javax.servlet.SessionCookieConfig;
import java.util.Objects;
import java.util.logging.Level;

import static org.springframework.web.context.support.WebApplicationContextUtils.getRequiredWebApplicationContext;

@EnableAutoConfiguration(exclude = {org.springframework.boot.autoconfigure.mustache.MustacheAutoConfiguration.class,
        org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration.class,
        org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration.class,
        org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration.class,
        org.springframework.boot.autoconfigure.web.servlet.HttpEncodingAutoConfiguration.class,
        org.springframework.boot.autoconfigure.session.SessionAutoConfiguration.class,
        org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration.class})
@Configuration
@Profile(Initializer.PROFILE_SPRING_BOOT)
@Log4j2
public class SpringBootInitializer {


    @Bean
    public ServletContextInitializer servletContextInitializer() {
        return servletContext -> {
            WebApplicationContext ctx = getRequiredWebApplicationContext(servletContext);
            ConfigurableEnvironment environment = ctx.getBean(ConfigurableEnvironment.class);
            SessionCookieConfig config = servletContext.getSessionCookieConfig();
            config.setHttpOnly(true);
            config.setSecure(environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_LIVE)));
            // force log initialization, then disable it
            XRLog.setLevel(XRLog.EXCEPTION, Level.WARNING);
            XRLog.setLoggingEnabled(false);
        };
    }

    @Bean
    public Filter characterEncodingFilter() {
        CharacterEncodingFilter cef = new CharacterEncodingFilter();
        cef.setEncoding("UTF-8");
        cef.setForceEncoding(true);
        return cef;
    }

    @Bean
    @Profile("!"+Initializer.PROFILE_DEV)
    public ErrorPageRegistrar errorPages() {
        return registry -> registry.addErrorPages(new ErrorPage(HttpStatus.NOT_FOUND, "/404-not-found"),
            new ErrorPage(HttpStatus.INTERNAL_SERVER_ERROR, "/500-internal-server-error"),
            new ErrorPage("/session-expired"));
    }
}
