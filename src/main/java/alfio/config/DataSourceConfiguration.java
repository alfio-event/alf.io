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

import alfio.config.support.ArrayColumnMapper;
import alfio.config.support.JSONColumnMapper;
import alfio.config.support.PlatformProvider;
import alfio.job.Jobs;
import alfio.job.executor.ReservationJobExecutor;
import alfio.manager.*;
import alfio.manager.i18n.MessageSourceManager;
import alfio.manager.system.AdminJobManager;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.repository.system.AdminJobQueueRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.CustomResourceBundleMessageSource;
import alfio.util.Json;
import alfio.util.TemplateManager;
import ch.digitalfondue.npjt.EnableNpjt;
import ch.digitalfondue.npjt.mapper.ColumnMapperFactory;
import ch.digitalfondue.npjt.mapper.ParameterConverter;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.log4j.Log4j2;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Configuration
@EnableTransactionManagement
@EnableScheduling
@EnableAsync
@ComponentScan(basePackages = {"alfio.manager", "alfio.extension"})
@Log4j2
@EnableNpjt(basePackages = "alfio.repository")
public class DataSourceConfiguration {

    private static final Set<PlatformProvider> PLATFORM_PROVIDERS = EnumSet.complementOf(EnumSet.of(PlatformProvider.DEFAULT));

    @Bean
    @Profile({"!"+Initializer.PROFILE_INTEGRATION_TEST, "travis"})
    public PlatformProvider getCloudProvider(Environment environment) {
        return PLATFORM_PROVIDERS.stream()
                                 .filter(p -> p.isHosting(environment))
                                 .findFirst()
                                 .orElse(PlatformProvider.DEFAULT);
    }

    @Bean
    @Profile({"!"+Initializer.PROFILE_INTEGRATION_TEST, "travis"})
    public DataSource getDataSource(Environment env, PlatformProvider platform) {
        if(platform == PlatformProvider.CLOUD_FOUNDRY) {
            return new FakeCFDataSource();
        } else {
            HikariDataSource dataSource = new HikariDataSource();
            dataSource.setJdbcUrl(platform.getUrl(env));
            dataSource.setUsername(platform.getUsername(env));
            dataSource.setPassword(platform.getPassword(env));
            dataSource.setDriverClassName("org.postgresql.Driver");
            dataSource.setMaximumPoolSize(platform.getMaxActive(env));
            dataSource.setMinimumIdle(platform.getMinIdle(env));
            dataSource.setConnectionTimeout(1000L);

            log.debug("Connection pool properties: max active {}, initial size {}", dataSource.getMaximumPoolSize(), dataSource.getMinimumIdle());

            // check
            boolean isSuperAdmin = Boolean.TRUE.equals(new NamedParameterJdbcTemplate(dataSource)
                .queryForObject("select usesuper from pg_user where usename = CURRENT_USER",
                    new EmptySqlParameterSource(),
                    Boolean.class));

            if (isSuperAdmin) {
                log.warn("You're accessing the database using a superuser. This is highly discouraged since it will disable the row security policy checks.");
            }

            //
            return dataSource;
        }
    }

    @Bean
    @Profile("!"+Initializer.PROFILE_SPRING_BOOT)
    public PlatformTransactionManager platformTransactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    public List<ColumnMapperFactory> getAdditionalColumnMappers() {
        return Arrays.asList(new JSONColumnMapper.Factory(), new ArrayColumnMapper.Factory());
    }

    @Bean
    public List<ParameterConverter> getAdditionalParameterConverters() {
        return Arrays.asList(new JSONColumnMapper.Converter(), new ArrayColumnMapper.Converter());
    }

    @Bean
    public Flyway migrator(Environment env, PlatformProvider platform, DataSource dataSource) {
        Flyway migration = new Flyway();
        migration.setDataSource(dataSource);

        migration.setValidateOnMigrate(false);
        migration.setTarget(MigrationVersion.LATEST);
        migration.setOutOfOrder(true);

        migration.setLocations("alfio/db/PGSQL/");
        migration.migrate();
        return migration;
    }
    
    @Bean
    public PasswordEncoder getPasswordEncoder() {
         return new BCryptPasswordEncoder();
     }

    @Bean
    public MessageSourceManager messageSourceManager(ConfigurationRepository configurationRepository) {

        var source = new CustomResourceBundleMessageSource();
        source.setBasenames("alfio.i18n.public", "alfio.i18n.admin");
        source.setDefaultEncoding(StandardCharsets.UTF_8.displayName());
        //since we have all the english translations in the default file, we don't need
        //the fallback to the system locale.
        source.setFallbackToSystemLocale(false);
        source.setAlwaysUseMessageFormat(true);

        return new MessageSourceManager(source, configurationRepository);
    }

    @Bean
    public Json getJson() {
        return new Json();
    }

    @Bean
    public TemplateManager getTemplateManager(MessageSourceManager messageSourceManager, UploadedResourceManager uploadedResourceManager) {
        return new TemplateManager(messageSourceManager, uploadedResourceManager);
    }

    @Bean
    @Profile("!"+Initializer.PROFILE_INTEGRATION_TEST)
    public FileDownloadManager fileDownloadManager() {
        return new FileDownloadManager();
    }

    @Bean
    public RoleAndOrganizationsAspect getRoleAndOrganizationsAspect(NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                                                    OrganizationRepository organizationRepository) {
        return new RoleAndOrganizationsAspect(namedParameterJdbcTemplate, organizationRepository);
    }

    @Bean
    @DependsOn("migrator")
    @Profile("!" + Initializer.PROFILE_DISABLE_JOBS)
    public Jobs jobs(AdminReservationRequestManager adminReservationRequestManager,
                     ConfigurationManager configurationManager,
                     Environment environment,
                     EventManager eventManager,
                     FileUploadManager fileUploadManager,
                     NotificationManager notificationManager,
                     SpecialPriceTokenGenerator specialPriceTokenGenerator,
                     UserManager userManager,
                     WaitingQueueSubscriptionProcessor waitingQueueSubscriptionProcessor,
                     TicketReservationManager ticketReservationManager,
                     AdminJobQueueRepository adminJobQueueRepository,
                     PlatformTransactionManager platformTransactionManager
                     ) {
        return new Jobs(adminReservationRequestManager, configurationManager, environment, eventManager, fileUploadManager,
            notificationManager, specialPriceTokenGenerator, ticketReservationManager, userManager,
            waitingQueueSubscriptionProcessor, adminJobManager(adminJobQueueRepository, platformTransactionManager, ticketReservationManager));

    }

    @Bean
    AdminJobManager adminJobManager(AdminJobQueueRepository adminJobQueueRepository,
                                    PlatformTransactionManager transactionManager,
                                    TicketReservationManager ticketReservationManager) {
        return new AdminJobManager(List.of(reservationJobExecutor(ticketReservationManager)), adminJobQueueRepository, transactionManager);
    }

    @Bean
    ReservationJobExecutor reservationJobExecutor(TicketReservationManager ticketReservationManager) {
        return new ReservationJobExecutor(ticketReservationManager);
    }

    /**
     * Fake DataSource used on Cloud Foundry. Oh yeah.
     */
    private static class FakeCFDataSource extends AbstractDataSource {
        @Override
        public Connection getConnection() {
            return null;
        }

        @Override
        public Connection getConnection(String username, String password) {
            return null;
        }
    }
}
