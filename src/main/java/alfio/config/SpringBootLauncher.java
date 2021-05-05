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
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.ArrayList;
import java.util.List;

@Log4j2
public class SpringBootLauncher {

    /**
     * Entry point for spring boot
     * @param args original arguments
     */
    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler(new DefaultExceptionHandler());
        String profiles = System.getProperty("spring.profiles.active", "");

        SpringApplication application = new SpringApplication(
            SpringBootInitializer.class,
            BaseConfiguration.class,
            DataSourceConfiguration.class,
            WebSecurityConfig.class,
            MvcConfiguration.class);
        List<String> additionalProfiles = new ArrayList<>();
        additionalProfiles.add(Initializer.PROFILE_SPRING_BOOT);
        if("true".equals(System.getenv("ALFIO_LOG_STDOUT_ONLY"))) {
            // -> will load application-stdout.properties on top to override the logger configuration
            additionalProfiles.add("stdout");
        }
        if("true".equals(System.getenv("ALFIO_DEMO_ENABLED"))) {
            additionalProfiles.add(Initializer.PROFILE_DEMO);
        }
        application.setAdditionalProfiles(additionalProfiles.toArray(new String[0]));
        ConfigurableApplicationContext applicationContext = application.run(args);
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        log.info("profiles: requested {}, active {}", profiles, String.join(", ", environment.getActiveProfiles()));
    }
}
