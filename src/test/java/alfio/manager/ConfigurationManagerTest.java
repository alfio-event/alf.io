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
package alfio.manager;

import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.manager.system.ConfigurationManager;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {DataSourceConfiguration.class})
@ActiveProfiles(Initializer.PROFILE_DEV)
@Transactional
public class ConfigurationManagerTest {

    @BeforeClass
    public static void initEnv() {
        System.setProperty("datasource.dialect", "HSQLDB");
        System.setProperty("datasource.driver", "org.hsqldb.jdbcDriver");
        System.setProperty("datasource.url", "jdbc:hsqldb:mem:alfio");
        System.setProperty("datasource.username", "sa");
        System.setProperty("datasource.password", "");
        System.setProperty("datasource.validationQuery", "SELECT 1 FROM INFORMATION_SCHEMA.SYSTEM_USERS");
        //System.setProperty("spring.profiles.active", Initializer.PROFILE_DEV);
    }

    @Autowired
    private ConfigurationManager configurationManager;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    public void testPresentStringConfigValue() {
        assertEquals(Optional.of("5"), configurationManager.getStringConfigValue(Configuration.system(), ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION));
    }

    @Test
    public void testEmptyStringConfigValue() {
        assertEquals(Optional.empty(), configurationManager.getStringConfigValue(Configuration.system(), ConfigurationKeys.SMTP_PASSWORD));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingConfigValue() {
        configurationManager.getRequiredValue(Configuration.system(), ConfigurationKeys.SMTP_PASSWORD);
    }

    @Test
    public void testRequiredValue() {
        assertEquals("5", configurationManager.getRequiredValue(Configuration.system(), ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION));
    }

    @Test
    public void testIntValue() {
        assertEquals(5, configurationManager.getIntConfigValue(Configuration.system(), ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION, -1));

        //missing value
        assertEquals(-1, configurationManager.getIntConfigValue(Configuration.system(), ConfigurationKeys.ASSIGNMENT_REMINDER_INTERVAL, -1));


        configurationManager.saveSystemConfiguration(ConfigurationKeys.BASE_URL, "blabla");
        assertEquals("blabla", configurationManager.getRequiredValue(Configuration.system(), ConfigurationKeys.BASE_URL));
        //not a number
        assertEquals(-1, configurationManager.getIntConfigValue(Configuration.system(), ConfigurationKeys.BASE_URL, -1));
    }

    @Test
    public void testBooleanValue() {
        //missing value
        assertFalse(configurationManager.getBooleanConfigValue(Configuration.system(), ConfigurationKeys.ALLOW_FREE_TICKETS_CANCELLATION, false));

        //false value
        configurationManager.saveSystemConfiguration(ConfigurationKeys.ALLOW_FREE_TICKETS_CANCELLATION, "false");
        assertFalse(configurationManager.getBooleanConfigValue(Configuration.system(), ConfigurationKeys.ALLOW_FREE_TICKETS_CANCELLATION, true));

        //true value
        configurationManager.saveSystemConfiguration(ConfigurationKeys.ALLOW_FREE_TICKETS_CANCELLATION, "true");
        assertTrue(configurationManager.getBooleanConfigValue(Configuration.system(), ConfigurationKeys.ALLOW_FREE_TICKETS_CANCELLATION, false));
    }

}
