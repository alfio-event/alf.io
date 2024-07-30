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

import alfio.config.support.ContextAwareCookieSerializer;
import alfio.util.ClockProvider;
import com.openhtmltopdf.util.XRLog;
import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.web.filter.CharacterEncodingFilter;

import java.time.Clock;
import java.util.logging.Level;

@EnableAutoConfiguration(exclude = {org.springframework.boot.autoconfigure.mustache.MustacheAutoConfiguration.class,
    org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration.class,
    org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration.class,
    org.springframework.boot.autoconfigure.web.servlet.HttpEncodingAutoConfiguration.class,
    org.springframework.boot.autoconfigure.session.SessionAutoConfiguration.class,
    org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration.class,
    org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration.class,
    org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class
})
@Configuration(proxyBeanMethods = false)
public class SpringBootInitializer {

    @Bean
    public ServletContextInitializer servletContextInitializer() {
        return servletContext -> {
            // force log initialization, then disable it
            XRLog.setLevel(XRLog.EXCEPTION, Level.WARNING);
            XRLog.setLoggingEnabled(false);
        };
    }

    @Bean
    public CookieSerializer cookieSerializer(Environment environment) {
        return new ContextAwareCookieSerializer(environment);
    }

    @Bean
    public Filter characterEncodingFilter() {
        CharacterEncodingFilter cef = new CharacterEncodingFilter();
        cef.setEncoding("UTF-8");
        cef.setForceEncoding(true);
        return cef;
    }

    @Bean
    public ClockProvider clockProvider() {
        return ClockProvider.init(Clock.systemUTC());
    }
}
