/**
 * This file is part of bagarino.
 *
 * bagarino is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bagarino is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.bagarino.config;

import io.bagarino.datamapper.QueryFactory;
import io.bagarino.datamapper.QueryRepositoryScanner;

import java.net.URISyntaxException;
import java.util.Objects;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableTransactionManagement
@EnableScheduling
@ComponentScan(basePackages = {"io.bagarino.manager"})
public class DataSourceConfiguration {
	
	
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
		return System.getenv("OPENSHIFT_APP_NAME") != null ? PlatformProvider.OPENSHIFT : PlatformProvider.DEFAULT;
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
	public PlatformTransactionManager platformTransactionManager(DataSource dataSource) {
		return new DataSourceTransactionManager(dataSource);
	}

	@Bean
	public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
		return new NamedParameterJdbcTemplate(dataSource);
	}

	@Bean
	public QueryFactory queryFactory(Environment env, NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
		return new QueryFactory(env.getRequiredProperty("datasource.dialect"), namedParameterJdbcTemplate);
	}

	@Bean
	public QueryRepositoryScanner queryRepositoryScanner(QueryFactory queryFactory) {
		return new QueryRepositoryScanner(queryFactory, "io.bagarino.repository");
	}

	@Bean
	public Flyway migrator(Environment env, PlatformProvider platform, DataSource dataSource) {
		String sqlDialect = platform.getDialect(env);
		Flyway migration = new Flyway();
		migration.setDataSource(dataSource);
		// TODO remove the validation = false when the schemas will be stable
		migration.setValidateOnMigrate(false);
		//

		migration.setTarget(MigrationVersion.LATEST);

		migration.setLocations("io/bagarino/db/" + sqlDialect + "/");
		migration.migrate();
		return migration;
	}
	
	 @Bean
	 public PasswordEncoder getPasswordEncoder() {
		 return new BCryptPasswordEncoder();
	 }
}
