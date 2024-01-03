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
import alfio.util.BaseIntegrationTest;
import alfio.util.ClockProvider;
import alfio.util.RefreshableDataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.Stripe;
import com.zaxxer.hikari.HikariConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.ByteArrayResource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.net.http.HttpClient;
import java.nio.charset.Charset;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import static alfio.test.util.TestUtil.FIXED_TIME_CLOCK;

@Configuration(proxyBeanMethods = false)
public class BaseTestConfiguration {

    public static final int MAX_POOL_SIZE = 5;
    private static final Logger log = LoggerFactory.getLogger(BaseTestConfiguration.class);

    @Bean
    @Profile("!travis")
    public PlatformProvider getCloudProvider() {
        return PlatformProvider.DEFAULT;
    }

    private static PostgreSQLContainer<?> postgres;
    private static GenericContainer<?> stripeMock;

    @Bean
    @Profile("!travis")
    public RefreshableDataSource dataSource() {
        String POSTGRES_DB = "alfio";
        String postgresVersion = Objects.requireNonNullElse(System.getProperty("pgsql.version"), "10");
        log.debug("Running tests using PostgreSQL v.{}", postgresVersion);
        if (postgres == null) {
            postgres = new PostgreSQLContainer<>("postgres:"+postgresVersion)
                .withDatabaseName(POSTGRES_DB)
                .withInitScript("init-db-user.sql");
            postgres.start();
            if ("true".equals(System.getenv().get("TESTCONTAINERS_RYUK_DISABLED"))) {
                Runtime.getRuntime().addShutdownHook(new Thread(postgres::stop));
            }
        }
        var config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername("alfio_user");
        config.setPassword("password");
        config.setDriverClassName(postgres.getDriverClassName());
        config.setMaximumPoolSize(MAX_POOL_SIZE);
        return new RefreshableDataSource(config);
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

    @PostConstruct
    public void initStripeMock() {
        if (stripeMock == null) {
            stripeMock = new GenericContainer<>("stripe/stripe-mock:latest")
                .withExposedPorts(12111, 12112);
            stripeMock.start();
            if ("true".equals(System.getenv().get("TESTCONTAINERS_RYUK_DISABLED"))) {
                Runtime.getRuntime().addShutdownHook(new Thread(stripeMock::stop));
            }
        }
        var httpPort = stripeMock.getMappedPort(12111);
        Stripe.overrideApiBase("http://localhost:" + httpPort);
        Stripe.overrideUploadBase("http://localhost:" + httpPort);
        Stripe.enableTelemetry = false;
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
