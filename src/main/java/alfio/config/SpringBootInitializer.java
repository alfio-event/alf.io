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
import org.eclipse.jetty.server.session.DefaultSessionIdManager;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer;
import org.springframework.boot.context.embedded.MimeMappings;
import org.springframework.boot.context.embedded.jetty.JettyEmbeddedServletContainerFactory;
import org.springframework.boot.web.servlet.ErrorPage;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.CharacterEncodingFilter;

import javax.servlet.Filter;
import javax.servlet.SessionCookieConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static org.springframework.web.context.support.WebApplicationContextUtils.getRequiredWebApplicationContext;

@EnableAutoConfiguration(exclude = {org.springframework.boot.autoconfigure.mustache.MustacheAutoConfiguration.class,
        org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration.class,
        org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration.class,
        org.springframework.boot.autoconfigure.web.ErrorMvcAutoConfiguration.class,
        org.springframework.boot.autoconfigure.session.SessionAutoConfiguration.class})
@Configuration
@Profile(Initializer.PROFILE_SPRING_BOOT)
@Log4j2
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
        //force log initialization, then disable it
        XRLog.setLevel(XRLog.EXCEPTION, Level.WARNING);
        XRLog.setLoggingEnabled(false);
    };

    @Bean
    public Filter characterEncodingFilter() {
        CharacterEncodingFilter cef = new CharacterEncodingFilter();
        cef.setEncoding("UTF-8");
        cef.setForceEncoding(true);
        return cef;
    }

    @Bean
    public EmbeddedServletContainerCustomizer embeddedServletContainerCustomizer(Environment environment) {
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
            if(!environment.acceptsProfiles("dev")) {
                container.addErrorPages(new ErrorPage(HttpStatus.NOT_FOUND, "/404-not-found"), new ErrorPage(HttpStatus.INTERNAL_SERVER_ERROR, "/500-internal-server-error"), new ErrorPage("/session-expired"));
            }

            Optional.ofNullable(System.getProperty("alfio.worker.name")).ifPresent(workerName -> {
                ((JettyEmbeddedServletContainerFactory)container).addServerCustomizers(server -> {
                    log.info("Configuring session manager using worker name {}", workerName);
                    DefaultSessionIdManager sessionIdManager = new DefaultSessionIdManager(server);
                    sessionIdManager.setWorkerName(workerName);
                    server.setSessionIdManager(sessionIdManager);
                });
            });


        };
    }
}
