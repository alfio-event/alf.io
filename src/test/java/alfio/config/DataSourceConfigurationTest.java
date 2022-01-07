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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DataSourceConfigurationTest {

    private Environment environment;
    private final DataSourceConfiguration configuration = new DataSourceConfiguration();

    @BeforeEach
    void init() {
        environment = mock(Environment.class);
    }


    @Test
    public void selectOpenShift() {
        when(environment.getProperty("OPENSHIFT_APP_NAME")).thenReturn("openshift");
        assertEquals(PlatformProvider.OPENSHIFT, configuration.getCloudProvider(environment));
    }

    @Test
    public void selectCloudFoundry() {
        when(environment.getProperty("VCAP_SERVICES")).thenReturn("cloud foundry");
        assertEquals(PlatformProvider.CLOUD_FOUNDRY, configuration.getCloudProvider(environment));
    }

    @Test
    public void selectHeroku() {
        when(environment.getProperty("DYNO")).thenReturn("heroku");
        assertEquals(PlatformProvider.HEROKU, configuration.getCloudProvider(environment));
    }

    @Test
    public void selectBeanstalk() {
        when(environment.getProperty("RDS_HOSTNAME")).thenReturn("host.rds.amazonaws.com");
        when(environment.getRequiredProperty("RDS_HOSTNAME")).thenReturn("host.rds.amazonaws.com");
        when(environment.getRequiredProperty("RDS_PORT")).thenReturn("5432");
        when(environment.getRequiredProperty("RDS_DB_NAME")).thenReturn("ebdb");
        when(environment.getRequiredProperty("RDS_USERNAME")).thenReturn("foo");
        when(environment.getRequiredProperty("RDS_PASSWORD")).thenReturn("bar");
        PlatformProvider cloudProvider = configuration.getCloudProvider(environment);
        assertEquals(PlatformProvider.AWS_BEANSTALK, cloudProvider);
        assertEquals("foo", cloudProvider.getUsername(environment));
        assertEquals("bar", cloudProvider.getPassword(environment));
        assertEquals("foo", cloudProvider.getUsername(environment));
        assertEquals("jdbc:postgresql://host.rds.amazonaws.com:5432/ebdb", cloudProvider.getUrl(environment));
    }

    @Test
    public void selectCleverCloud() {
        when(environment.getProperty("CC_DEPLOYMENT_ID")).thenReturn("deployment-id");
        PlatformProvider cloudProvider = configuration.getCloudProvider(environment);
        assertEquals(PlatformProvider.CLEVER_CLOUD, cloudProvider);
    }

    @Test
    public void selectDefault() {
        assertEquals(PlatformProvider.DEFAULT, configuration.getCloudProvider(environment));
    }
}
