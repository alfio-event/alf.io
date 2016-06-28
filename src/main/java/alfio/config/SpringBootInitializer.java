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

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer;
import org.springframework.boot.context.embedded.ErrorPage;
import org.springframework.boot.context.embedded.MimeMappings;
import org.springframework.boot.context.embedded.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.CharacterEncodingFilter;

import javax.servlet.Filter;
import javax.servlet.SessionCookieConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.springframework.web.context.support.WebApplicationContextUtils.getRequiredWebApplicationContext;

@EnableAutoConfiguration(exclude = {org.springframework.boot.autoconfigure.mustache.MustacheAutoConfiguration.class,
        org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration.class,
        org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration.class,
        org.springframework.boot.autoconfigure.web.ErrorMvcAutoConfiguration.class})
@Configuration
@Profile(Initializer.PROFILE_SPRING_BOOT)
public class SpringBootInitializer {

    private static final ServletContextInitializer SERVLET_CONTEXT_INITIALIZER = servletContext -> {
        WebApplicationContext ctx = getRequiredWebApplicationContext(servletContext);
        ConfigurableEnvironment environment = ctx.getBean(ConfigurableEnvironment.class);
        environment.addActiveProfile("spring-boot");
        if(environment.acceptsProfiles(Initializer.PROFILE_DEV)) {
            environment.addActiveProfile(Initializer.PROFILE_HTTP);
        }
        SessionCookieConfig config = servletContext.getSessionCookieConfig();
        config.setHttpOnly(true);
        config.setSecure(!environment.acceptsProfiles(Initializer.PROFILE_HTTP));
    };

    @Bean
    public Filter characterEncodingFilter() {
        CharacterEncodingFilter cef = new CharacterEncodingFilter();
        cef.setEncoding("UTF-8");
        cef.setForceEncoding(true);
        return cef;
    }

    @Bean
    public EmbeddedServletContainerCustomizer embeddedServletContainerCustomizer() {
        return (container) -> {
            container.addInitializers(SERVLET_CONTEXT_INITIALIZER);
            //container.setRegisterJspServlet(false);
            Map<String, String> mimeMappings = new HashMap<>();
            mimeMappings.put("eot", "application/vnd.ms-fontobject");
            mimeMappings.put("otf", "font/opentype");
            mimeMappings.put("ttf", "application/x-font-ttf");
            mimeMappings.put("woff", "application/x-font-woff");
            mimeMappings.put("svg", "image/svg+xml");
            container.setSessionTimeout(2, TimeUnit.HOURS);
            container.setMimeMappings(new MimeMappings(mimeMappings));

            container.addErrorPages(new ErrorPage(HttpStatus.NOT_FOUND, "/404-not-found"), new ErrorPage(HttpStatus.INTERNAL_SERVER_ERROR, "/500-internal-server-error"), new ErrorPage("/session-expired"));
        };
    }
}
