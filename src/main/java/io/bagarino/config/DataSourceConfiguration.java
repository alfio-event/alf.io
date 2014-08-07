package io.bagarino.config;

import java.net.URISyntaxException;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class DataSourceConfiguration {

	// TODO use a profile instead of doing a fallback
	@Bean(destroyMethod = "close")
	public DataSource getDataSource(Environment env) throws URISyntaxException {
		org.apache.tomcat.jdbc.pool.DataSource dataSource = new org.apache.tomcat.jdbc.pool.DataSource();
		dataSource.setDriverClassName(env.getProperty("datasource.driver", "org.hsqldb.jdbcDriver"));
		dataSource.setUrl(env.getProperty("datasource.url", "jdbc:hsqldb:mem:lavagna"));
		dataSource.setUsername(env.getProperty("datasource.username", "sa"));
		dataSource.setPassword(env.getProperty("datasource.password", ""));
		dataSource.setValidationQuery(env.getProperty("datasource.validationQuery",
				"SELECT 1 FROM INFORMATION_SCHEMA.SYSTEM_USERS"));
		dataSource.setTestOnBorrow(true);
		dataSource.setTestOnConnect(true);
		dataSource.setTestWhileIdle(true);
		return dataSource;
	}

	@Bean
	public Flyway migrator(Environment env, DataSource dataSource) {
		String sqlDialect = env.getProperty("datasource.dialect", "HSQLDB");
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
}
