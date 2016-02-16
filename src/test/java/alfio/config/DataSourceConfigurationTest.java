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
import com.insightfullogic.lambdabehave.JunitSuiteRunner;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;

import static com.insightfullogic.lambdabehave.Suite.describe;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(JunitSuiteRunner.class)
public class DataSourceConfigurationTest {{
    describe("DataSourceConfiguration", it -> {

        Environment environment = mock(Environment.class);
        DataSourceConfiguration configuration = new DataSourceConfiguration();
        it.isSetupWith(() -> Mockito.reset(environment));
        it.should("select OPENSHIFT environment", expect -> {
            when(environment.getProperty("OPENSHIFT_APP_NAME")).thenReturn("openshift");
            expect.that(configuration.getCloudProvider(environment)).isEqualTo(PlatformProvider.OPENSHIFT);
        });
        it.should("select CLOUD_FOUNDRY environment", expect -> {
            when(environment.getProperty("VCAP_SERVICES")).thenReturn("cloud foundry");
            expect.that(configuration.getCloudProvider(environment)).isEqualTo(PlatformProvider.CLOUD_FOUNDRY);
        });
        it.should("select HEROKU environment", expect -> {
            when(environment.getProperty("DYNO")).thenReturn("heroku");
            expect.that(configuration.getCloudProvider(environment)).isEqualTo(PlatformProvider.HEROKU);
        });
        it.should("select DOCKER environment", expect -> {
            when(environment.getProperty("DB_ENV_POSTGRES_DB")).thenReturn("docker");
            when(environment.getProperty("DB_ENV_DOCKER_DB_NAME")).thenReturn("docker");
            expect.that(configuration.getCloudProvider(environment)).isEqualTo(PlatformProvider.DOCKER);
        });

        it.should("select DEFAULT environment otherwise", expect -> {
            expect.that(configuration.getCloudProvider(environment)).isEqualTo(PlatformProvider.DEFAULT);
        });

    });
}}
