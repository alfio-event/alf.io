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

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableTransactionManagement
@ComponentScan(basePackages = {"io.bagarino.manager"})
public class DataSourceConfiguration {

	@Bean(destroyMethod = "close")
	public DataSource getDataSource(Environment env) throws URISyntaxException {
		org.apache.tomcat.jdbc.pool.DataSource dataSource = new org.apache.tomcat.jdbc.pool.DataSource();
		dataSource.setDriverClassName(env.getRequiredProperty("datasource.driver"));
		dataSource.setUrl(env.getRequiredProperty("datasource.url"));
		dataSource.setUsername(env.getRequiredProperty("datasource.username"));
		dataSource.setPassword(env.getRequiredProperty("datasource.password"));
		dataSource.setValidationQuery(env.getRequiredProperty("datasource.validationQuery"));
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
	public Flyway migrator(Environment env, DataSource dataSource) {
		String sqlDialect = env.getRequiredProperty("datasource.dialect");
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
