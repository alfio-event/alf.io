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
import org.apache.commons.lang3.Validate;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.embedded.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.CharacterEncodingFilter;

import javax.servlet.Filter;
import javax.servlet.SessionCookieConfig;

import static org.springframework.web.context.support.WebApplicationContextUtils.getRequiredWebApplicationContext;

@Log4j2
@EnableAutoConfiguration(exclude = {org.springframework.boot.autoconfigure.mustache.MustacheAutoConfiguration.class,
        org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.SpringBootWebSecurityConfiguration.class})
@Configuration
public class SpringBootInitializer {

    private static final String PROFILE_HTTP = "http";


    @Bean
    public Filter characterEncodingFilter() {
        CharacterEncodingFilter cef = new CharacterEncodingFilter();
        cef.setEncoding("UTF-8");
        cef.setForceEncoding(true);
        return cef;
    }

    @Bean
    public Filter redirectToHttpsFilter() {
        return new RedirectToHttpsFilter();
    }

    @Bean
    public ServletContextInitializer servletContextInitializer() {
        return servletContext -> {
            WebApplicationContext ctx = getRequiredWebApplicationContext(servletContext);
            ConfigurableEnvironment environment = ctx.getBean(ConfigurableEnvironment.class);
            environment.addActiveProfile("spring-boot");
            SessionCookieConfig config = servletContext.getSessionCookieConfig();
            config.setHttpOnly(true);
            Validate.notNull(environment, "environment cannot be null!");
            // set secure cookie only if current environment doesn't strictly need HTTP
            config.setSecure(!environment.acceptsProfiles(PROFILE_HTTP));
        };
    }
}
