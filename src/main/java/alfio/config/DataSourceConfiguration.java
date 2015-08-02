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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Profile;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.servlet.view.mustache.jmustache.JMustacheTemplateLoader;

import javax.sql.DataSource;
import java.net.URISyntaxException;
import java.util.EnumSet;
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
        log.info("Detected {} cloud provider", current);
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
	public MailChimpPlugin getMailChimpPlugin(PluginConfigurationRepository pluginConfigurationRepository, PluginLogRepository pluginLogRepository) {
		return new MailChimpPlugin(pluginDataStorageProvider(pluginConfigurationRepository, pluginLogRepository));
	}

	@Bean
	public PluginDataStorageProvider pluginDataStorageProvider(PluginConfigurationRepository pluginConfigurationRepository, PluginLogRepository pluginLogRepository) {
		return new PluginDataStorageProvider(pluginConfigurationRepository, pluginLogRepository);
	}

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}
}
