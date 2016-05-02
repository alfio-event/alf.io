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
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

@Log4j2
public class SpringBootLauncher {

    /**
     * Entry point for spring boot
     * @param args original arguments
     */
    public static void main(String[] args) {

        Thread.setDefaultUncaughtExceptionHandler(new DefaultExceptionHandler());

        String profiles = System.getProperty("spring.profiles.active", "");
        log.info("requested profiles {}", profiles);

        SpringApplication application = new SpringApplication(SpringBootInitializer.class, DataSourceConfiguration.class, WebSecurityConfig.class, MvcConfiguration.class);
        application.setAdditionalProfiles(Initializer.PROFILE_SPRING_BOOT);
        ConfigurableApplicationContext applicationContext = application.run(args);
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        log.info("active profiles: {}", String.join(", ", environment.getActiveProfiles()));

        if (System.getProperty("startDBManager") != null) {
            Class<?> cls;
            try {
                cls = ClassUtils.getClass("org.hsqldb.util.DatabaseManagerSwing");
                MethodUtils.invokeStaticMethod(cls, "main", new Object[]{new String[]{"--url", "jdbc:hsqldb:mem:alfio", "--noexit"}});
            } catch (ReflectiveOperationException e) {
                log.warn("error starting db manager", e);
            }
        }
    }
}
