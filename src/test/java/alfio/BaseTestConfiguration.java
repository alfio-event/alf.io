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
package alfio;

import alfio.config.Initializer;
import alfio.config.support.PlatformProvider;
import alfio.manager.FileDownloadManager;
import alfio.manager.system.ExternalConfiguration;
import alfio.model.system.ConfigurationKeys;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.BaseIntegrationTest;
import alfio.util.ClockProvider;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.ByteArrayResource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.net.http.HttpClient;
import java.nio.charset.Charset;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import static alfio.test.util.TestUtil.FIXED_TIME_CLOCK;

@Configuration(proxyBeanMethods = false)
public class BaseTestConfiguration {

    @Bean
    @Profile("!travis")
    public PlatformProvider getCloudProvider() {
        return PlatformProvider.DEFAULT;
    }

    @Bean
    @Profile("!travis")
    public DataSource getDataSource() {
        String POSTGRES_DB = "alfio";
        PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:9.6")
            .withDatabaseName(POSTGRES_DB)
            .withInitScript("init-db-user.sql");
        postgres.start();
        var config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername("alfio_user");
        config.setPassword("password");
        config.setDriverClassName(postgres.getDriverClassName());
        config.setMaximumPoolSize(5);
        return new HikariDataSource(config);
    }

    @Bean
    public static PropertySourcesPlaceholderConfigurer propConfig() {
        Properties properties = new Properties();
        properties.put("alfio.version", "2.0-SNAPSHOT");
        properties.put("alfio.build-ts", ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1).toString());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter pw = new PrintWriter(out, true, Charset.defaultCharset());
        properties.list(pw);
        pw.flush();
        var configurer =  new PropertySourcesPlaceholderConfigurer();
        configurer.setLocation(new ByteArrayResource(out.toByteArray()));
        return configurer;
    }

    @Bean
    public FileDownloadManager fileDownloadManager() {
        return new FileDownloadManager(HttpClient.newHttpClient()) {
            @Override
            public DownloadedFile downloadFile(String url) {
                return new DownloadedFile(BaseIntegrationTest.ONE_PIXEL_BLACK_GIF, "test", "image/gif");
            }
        };
    }

    @Bean
    @Profile(Initializer.PROFILE_INTEGRATION_TEST)
    public Supplier<Executor> getCurrentThreadExecutorSupplier() {
        return () -> Runnable::run;
    }

    @Bean
    @Profile(Initializer.PROFILE_INTEGRATION_TEST)
    public ExternalConfiguration externalConfiguration() {
        ExternalConfiguration externalConfiguration = new ExternalConfiguration();
        externalConfiguration.setSettings(Map.of(ConfigurationKeys.BASE_URL.name(), "http://localhost:8080"));
        return externalConfiguration;
    }

    @Bean
    @Profile(Initializer.PROFILE_INTEGRATION_TEST)
    public ClockProvider clockProvider() {
        return FIXED_TIME_CLOCK;
    }
}
