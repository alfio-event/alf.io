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

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.TicketCategory;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.EventModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.modification.support.LocationDescriptor;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.model.user.Organization;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
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
    private ConfigurationRepository configurationRepository;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserManager userManager;

    @Test
    public void testPresentStringConfigValue() {
        assertEquals(Optional.of("5"), configurationManager.getStringConfigValue(Configuration.system(), ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION));
    }

    @Test
    public void testEmptyStringConfigValue() {
        assertEquals(Optional.empty(), configurationManager.getStringConfigValue(Configuration.system(), ConfigurationKeys.SMTP_PASSWORD));
    }

    @Test
    public void testStringValueWithDefault() {
        assertEquals("5", configurationManager.getStringConfigValue(Configuration.system(), ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION, "-1"));
        assertEquals("-1", configurationManager.getStringConfigValue(Configuration.system(), ConfigurationKeys.SMTP_PASSWORD, "-1"));
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

    @Test
    public void testOverrideMechanism() {


        //setup...
        organizationRepository.create("org", "org", "email@example.com");
        Organization organization = organizationRepository.findByName("org").get(0);

        userManager.insertUser(organization.getId(), "test", "test", "test", "test@example.com");

        List<TicketCategoryModification> ticketsCategory = Collections.singletonList(
                new TicketCategoryModification(null, "default", 20,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        "desc", BigDecimal.TEN, false, "", false));
        EventModification em = new EventModification(null, "url", "url", "url", null,
                "eventShortName", organization.getId(),
                "muh location", "muh description",
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                BigDecimal.TEN, "CHF", 20, BigDecimal.ONE, true, null, ticketsCategory, false, new LocationDescriptor("","","",""));
        eventManager.createEvent(em);

        Event event = eventManager.getSingleEvent("eventShortName", "test");

        TicketCategory tc = eventManager.loadTicketCategories(event).get(0);
        //

        //insert override value at each level

        configurationRepository.insertOrganizationLevel(organization.getId(), ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION.getValue(), "6", "desc");
        configurationRepository.insertEventLevel(organization.getId(), event.getId(), ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION.getValue(), "7", "desc");
        configurationRepository.insertTicketCategoryLevel(organization.getId(), event.getId(), tc.getId(), ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION.getValue(), "8", "desc");

        assertEquals(5, configurationManager.getIntConfigValue(Configuration.system(), ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION, -1));
        assertEquals(6, configurationManager.getIntConfigValue(Configuration.organization(organization.getId()), ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION, -1));
        assertEquals(7, configurationManager.getIntConfigValue(Configuration.event(event), ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION, -1));
        assertEquals(8, configurationManager.getIntConfigValue(Configuration.ticketCategory(organization.getId(), event.getId(), tc.getId()), ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION, -1));

    }


}
