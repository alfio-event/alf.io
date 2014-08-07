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
