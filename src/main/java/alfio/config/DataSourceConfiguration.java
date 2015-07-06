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

import alfio.util.TemplateManager;
import ch.digitalfondue.npjt.QueryFactory;
import ch.digitalfondue.npjt.QueryRepositoryScanner;
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
import java.util.Objects;
import java.util.Optional;

@EnableTransactionManagement
@EnableScheduling
@EnableAsync
@ComponentScan(basePackages = {"alfio.manager"})
public class DataSourceConfiguration implements ResourceLoaderAware {

	@Autowired
	private ResourceLoader resourceLoader;

	/**
	 * For handling the various differences between the cloud providers.
	 * 
	 * Supported:
	 *  - openshift : pgsql only
	 *  - local use with system properties
	 */
	public enum PlatformProvider {
		DEFAULT, 
		
		/**
		 * See https://developers.openshift.com/en/managing-environment-variables.html
		 * 
		 * **/
		OPENSHIFT {
			@Override
			public String getDriveClassName(Environment env) {
				return "org.postgresql.Driver";
			}
			
			@Override
			public String getUrl(Environment env) {
				String dbHost = Objects.requireNonNull(System.getenv("OPENSHIFT_POSTGRESQL_DB_HOST"), "OPENSHIFT_POSTGRESQL_DB_HOST env variable is missing");
				String port = Objects.requireNonNull(System.getenv("OPENSHIFT_POSTGRESQL_DB_PORT"), "OPENSHIFT_POSTGRESQL_DB_PORT env variable is missing");
				String dbName = Objects.requireNonNull(System.getenv("OPENSHIFT_APP_NAME"), "OPENSHIFT_APP_NAME env variable is missing");
				
				return "jdbc:postgresql://" + dbHost + ":" + port + "/" + dbName;
			}
			
			@Override
			public String getUsername(Environment env) {
				return Objects.requireNonNull(System.getenv("OPENSHIFT_POSTGRESQL_DB_USERNAME"), "OPENSHIFT_POSTGRESQL_DB_USERNAME env variable is missing");
			}
			
			
			@Override
			public String getPassword(Environment env) {
				return Objects.requireNonNull(System.getenv("OPENSHIFT_POSTGRESQL_DB_PASSWORD"), "OPENSHIFT_POSTGRESQL_DB_PASSWORD env variable is missing");
			}
			
			@Override
			public String getValidationQuery(Environment env) {
				return "SELECT 1";
			}
			
			@Override
			public String getDialect(Environment env) {
				return "PGSQL";
			}
		},

		DOCKER {
			@Override
			public String getDriveClassName(Environment env) {
				return "org.postgresql.Driver";
			}

			@Override
			public String getUrl(Environment env) {
				String dbHost = Objects.requireNonNull(System.getenv("DB_PORT_5432_TCP_ADDR"), "DB_PORT_5432_TCP_ADDR env variable is missing");
				String port = Objects.requireNonNull(System.getenv("DB_PORT_5432_TCP_PORT"), "DB_PORT_5432_TCP_PORT env variable is missing");
				String dbName = Objects.requireNonNull(System.getenv("DB_ENV_POSTGRES_USERNAME"), "DB_ENV_POSTGRES_USERNAME env variable is missing");
				return "jdbc:postgresql://" + dbHost + ":" + port + "/" + dbName;
			}

			@Override
			public String getUsername(Environment env) {
				return Objects.requireNonNull(System.getenv("DB_ENV_POSTGRES_USERNAME"), "DB_ENV_POSTGRES_USERNAME env variable is missing");
			}


			@Override
			public String getPassword(Environment env) {
				return Objects.requireNonNull(System.getenv("DB_ENV_POSTGRES_PASSWORD"), "DB_ENV_POSTGRES_PASSWORD env variable is missing");
			}

			@Override
			public String getValidationQuery(Environment env) {
				return "SELECT 1";
			}

			@Override
			public String getDialect(Environment env) {
				return "PGSQL";
			}
		};
		
		public String getDriveClassName(Environment env) {
			return env.getRequiredProperty("datasource.driver");
		}
		
		public String getUrl(Environment env) {
			return env.getRequiredProperty("datasource.url");
		}
		
		public String getUsername(Environment env) {
			return env.getRequiredProperty("datasource.username");
		}
		
		public String getPassword(Environment env) {
			return env.getRequiredProperty("datasource.password");
		}
		
		public String getValidationQuery(Environment env) {
			return env.getRequiredProperty("datasource.validationQuery");
		}
		
		public String getDialect(Environment env) {
			return env.getRequiredProperty("datasource.dialect");
		}
	}
	
	@Bean
	public PlatformProvider getCloudProvider() {
		Optional<String> dockerDbName = Optional.ofNullable(System.getenv("DB_ENV_DOCKER_DB_NAME"));
		if(dockerDbName.isPresent()) {
			return PlatformProvider.DOCKER;
		}
		Optional<String> openshiftAppName = Optional.ofNullable(System.getenv("OPENSHIFT_APP_NAME"));
		return openshiftAppName.map(n -> PlatformProvider.OPENSHIFT).orElse(PlatformProvider.DEFAULT);
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
		return new QueryFactory(platform.getDialect(env), namedParameterJdbcTemplate);
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
		source.setBasenames("alfio.i18n.application", "alfio.i18n.admin");
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

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}
}
