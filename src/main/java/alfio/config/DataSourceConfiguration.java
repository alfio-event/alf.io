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

import alfio.config.support.PlatformProvider;
import alfio.manager.Jobs.*;
import alfio.plugin.PluginDataStorageProvider;
import alfio.plugin.mailchimp.MailChimpPlugin;
import alfio.repository.plugin.PluginConfigurationRepository;
import alfio.repository.plugin.PluginLogRepository;
import alfio.util.TemplateManager;
import ch.digitalfondue.npjt.QueryFactory;
import ch.digitalfondue.npjt.QueryRepositoryScanner;
import ch.digitalfondue.npjt.mapper.ZonedDateTimeMapper;
import lombok.extern.log4j.Log4j2;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.quartz.Job;
import org.quartz.Trigger;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.MessageSource;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.scheduling.quartz.SimpleTriggerFactoryBean;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.servlet.view.mustache.jmustache.JMustacheTemplateLoader;

import javax.sql.DataSource;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.EnumSet;
import java.util.Properties;
import java.util.Set;

@EnableTransactionManagement
@EnableScheduling
@EnableAsync
@ComponentScan(basePackages = {"alfio.manager"})
@Log4j2
public class DataSourceConfiguration implements ResourceLoaderAware {

    private static final Set<PlatformProvider> PLATFORM_PROVIDERS = EnumSet.complementOf(EnumSet.of(PlatformProvider.DEFAULT));

    @Autowired
    private ResourceLoader resourceLoader;

    @Bean
    public PlatformProvider getCloudProvider(Environment environment) {
        PlatformProvider current = PLATFORM_PROVIDERS
                                    .stream()
                                    .filter(p -> p.isHosting(environment))
                                    .findFirst()
                                    .orElse(PlatformProvider.DEFAULT);
        log.info("Detected cloud provider: {}, database: {}", current, current.getDialect(environment));
        return current;
    }

    @Bean(destroyMethod = "close")
    public DataSource getDataSource(Environment env, PlatformProvider platform) throws URISyntaxException {
        org.apache.tomcat.jdbc.pool.DataSource dataSource = new org.apache.tomcat.jdbc.pool.DataSource();
        dataSource.setDriverClassName(platform.getDriveClassName(env));
        dataSource.setUrl(platform.getUrl(env));
        dataSource.setUsername(platform.getUsername(env));
        dataSource.setPassword(platform.getPassword(env));
        dataSource.setValidationQuery(platform.getValidationQuery(env));
        dataSource.setTestOnBorrow(true);
        dataSource.setTestOnConnect(true);
        dataSource.setTestWhileIdle(true);
        dataSource.setMaxActive(platform.getMaxConnections(env));
        return dataSource;
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
    public QueryFactory queryFactory(Environment env, PlatformProvider platform, NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        QueryFactory qf = new QueryFactory(platform.getDialect(env), namedParameterJdbcTemplate);
        qf.addColumnMapperFactory(new ZonedDateTimeMapper.Factory());
        qf.addParameterConverters(new ZonedDateTimeMapper.Converter());
        return qf;
    }

    @Bean
    public QueryRepositoryScanner queryRepositoryScanner(QueryFactory queryFactory) {
        return new QueryRepositoryScanner(queryFactory, "alfio.repository");
    }

    @Bean
    public Flyway migrator(Environment env, PlatformProvider platform, DataSource dataSource) {
        String sqlDialect = platform.getDialect(env);
        Flyway migration = new Flyway();
        migration.setDataSource(dataSource);

        migration.setValidateOnMigrate(false);
        migration.setTarget(MigrationVersion.LATEST);
        migration.setOutOfOrder(true);

        migration.setLocations("alfio/db/" + sqlDialect + "/");
        migration.migrate();
        return migration;
    }
    
    @Bean
    public PasswordEncoder getPasswordEncoder() {
         return new BCryptPasswordEncoder();
     }

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("alfio.i18n.public", "alfio.i18n.admin");
        //since we have all the english translations in the default file, we don't need
        //the fallback to the system locale.
        source.setFallbackToSystemLocale(false);
        source.setAlwaysUseMessageFormat(true);
        return source;
    }

    @Bean
    public TemplateManager getTemplateManager(Environment environment) {
        return new TemplateManager(environment, getTemplateLoader(), messageSource());
    }

    @Bean
    public JMustacheTemplateLoader getTemplateLoader() {
        JMustacheTemplateLoader loader = new JMustacheTemplateLoader();
        loader.setResourceLoader(resourceLoader);
        return loader;
    }

    @Bean
    public MailChimpPlugin getMailChimpPlugin(PluginConfigurationRepository pluginConfigurationRepository,
                                              PluginLogRepository pluginLogRepository,
                                              PlatformTransactionManager platformTransactionManager) {
        return new MailChimpPlugin(pluginDataStorageProvider(pluginConfigurationRepository, pluginLogRepository, platformTransactionManager));
    }

    @Bean
    public PluginDataStorageProvider pluginDataStorageProvider(PluginConfigurationRepository pluginConfigurationRepository,
                                                               PluginLogRepository pluginLogRepository,
                                                               PlatformTransactionManager platformTransactionManager) {
        return new PluginDataStorageProvider(pluginConfigurationRepository, pluginLogRepository, platformTransactionManager);
    }

    // ----- scheduler conf ------
    // partially based on
    // http://sloanseaman.com/wordpress/2011/06/06/spring-and-quartz-and-persistence/
    // https://objectpartners.com/2013/07/09/configuring-quartz-2-with-spring-in-clustered-mode/
    // https://gist.github.com/jelies/5085593

    public static class AutowiringSpringBeanJobFactory extends SpringBeanJobFactory implements ApplicationContextAware {

        private transient AutowireCapableBeanFactory beanFactory;

        @Override
        public void setApplicationContext(final ApplicationContext context) {
            beanFactory = context.getAutowireCapableBeanFactory();
        }

        @Override
        protected Object createJobInstance(final TriggerFiredBundle bundle) throws Exception {
            final Object job = super.createJobInstance(bundle);
            beanFactory.autowireBean(job);
            return job;
        }
    }

    /**
     * @param jobClass
     * @param name
     * @param repeatInterval in milliseconds
     * @return
     * @throws ParseException
     */
    private static Trigger buildTrigger(Class<? extends Job> jobClass, String name, long repeatInterval) throws ParseException {
        JobDetailFactoryBean jobDetailFactory = new JobDetailFactoryBean();
        jobDetailFactory.setJobClass(jobClass);
        jobDetailFactory.setName(name);
        jobDetailFactory.setDurability(true);

        jobDetailFactory.afterPropertiesSet();

        SimpleTriggerFactoryBean triggerFactoryBean = new SimpleTriggerFactoryBean();
        triggerFactoryBean.setJobDetail(jobDetailFactory.getObject());
        triggerFactoryBean.setRepeatInterval(repeatInterval);
        triggerFactoryBean.setName(name);
        triggerFactoryBean.afterPropertiesSet();

        return triggerFactoryBean.getObject();
    }

    public Trigger[] getTriggers() throws ParseException {
        return new Trigger[]{
            buildTrigger(CleanupExpiredPendingReservation.class, "CleanupExpiredPendingReservation", CleanupExpiredPendingReservation.INTERVAL),
            buildTrigger(SendOfflinePaymentReminder.class, "SendOfflinePaymentReminder", SendOfflinePaymentReminder.INTERVAL),
            buildTrigger(SendTicketAssignmentReminder.class, "SendTicketAssignmentReminder", SendTicketAssignmentReminder.INTERVAL),
            buildTrigger(GenerateSpecialPriceCodes.class, "GenerateSpecialPriceCodes", GenerateSpecialPriceCodes.INTERVAL),
            buildTrigger(SendEmails.class, "SendEmails", SendEmails.INTERVAL),
            buildTrigger(ProcessReleasedTickets.class, "ProcessReleasedTickets", ProcessReleasedTickets.INTERVAL),
            buildTrigger(CleanupUnreferencedBlobFiles.class, "CleanupUnreferencedBlobFiles", CleanupUnreferencedBlobFiles.INTERVAL)
        };
    }

    @Bean
    @DependsOn("migrator")
    @Profile("!"+ Initializer.PROFILE_DISABLE_JOBS)
    public SchedulerFactoryBean schedulerFactory(Environment env, PlatformProvider platform, DataSource dataSource, PlatformTransactionManager platformTransactionManager, ApplicationContext applicationContext) throws ParseException {

        String dialect = platform.getDialect(env);
        String quartzDriverDelegateClass;
        switch (dialect) {
            case "PGSQL":
                quartzDriverDelegateClass = "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate";
                break;
            case "HSQLDB":
                quartzDriverDelegateClass = "org.quartz.impl.jdbcjobstore.HSQLDBDelegate";
                break;
            case "MYSQL":
                quartzDriverDelegateClass = "org.quartz.impl.jdbcjobstore.StdJDBCDelegate";
                break;
            default:
                throw new IllegalArgumentException("Unsupported dialect: " + dialect);
        }

        Properties properties = new Properties();
        properties.setProperty("org.quartz.jobStore.isClustered", "true");
        properties.setProperty("org.quartz.scheduler.instanceId", "AUTO");
        properties.setProperty("org.quartz.jobStore.driverDelegateClass", quartzDriverDelegateClass);

        SchedulerFactoryBean sfb = new SchedulerFactoryBean();
        sfb.setAutoStartup(true);
        sfb.setWaitForJobsToCompleteOnShutdown(true);
        sfb.setOverwriteExistingJobs(true);
        sfb.setDataSource(dataSource);
        sfb.setTransactionManager(platformTransactionManager);
        sfb.setBeanName("QuartzScheduler");
        sfb.setQuartzProperties(properties);
        AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory();
        jobFactory.setApplicationContext(applicationContext);
        sfb.setJobFactory(jobFactory);
        sfb.setTriggers(getTriggers());

        log.info("Quartz scheduler configured to run!");
        return sfb;
    }

    // ----- end scheduler conf ------

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
}
