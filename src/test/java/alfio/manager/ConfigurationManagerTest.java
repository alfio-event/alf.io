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
import alfio.test.util.IntegrationTestUtil;
import org.junit.Before;
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
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS})
@Transactional
public class ConfigurationManagerTest {

    @BeforeClass
    public static void initEnv() {
        IntegrationTestUtil.initSystemProperties();
    }

    @Before
    public void prepareEnv() {
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
                "eventShortName", "displayName", organization.getId(),
                "muh location", "muh description",
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                BigDecimal.TEN, "CHF", 20, BigDecimal.ONE, true, null, ticketsCategory, false, new LocationDescriptor("","","",""), 7);
        eventManager.createEvent(em);

        event = eventManager.getSingleEvent("eventShortName", "test");
    }

    Event event;

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
        assertEquals(Optional.of("5"), configurationManager.getStringConfigValue(Configuration.maxAmountOfTicketsByReservation(event)));
    }

    @Test
    public void testEmptyStringConfigValue() {
        assertEquals(Optional.empty(), configurationManager.getStringConfigValue(Configuration.smtpPassword(event)));
    }

    @Test
    public void testStringValueWithDefault() {
        assertEquals("5", configurationManager.getStringConfigValue(Configuration.maxAmountOfTicketsByReservation(event), "-1"));
        assertEquals("-1", configurationManager.getStringConfigValue(Configuration.smtpPassword(event), "-1"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingConfigValue() {
        configurationManager.getRequiredValue(Configuration.smtpPassword(event));
    }

    @Test
    public void testRequiredValue() {
        assertEquals("5", configurationManager.getRequiredValue(Configuration.maxAmountOfTicketsByReservation(event)));
    }

    @Test
    public void testIntValue() {
        assertEquals(5, configurationManager.getIntConfigValue(Configuration.maxAmountOfTicketsByReservation(event), -1));

        //missing value
        assertEquals(-1, configurationManager.getIntConfigValue(Configuration.assignmentReminderInterval(event), -1));


        configurationManager.saveSystemConfiguration(ConfigurationKeys.BASE_URL, "blabla");
        assertEquals("blabla", configurationManager.getRequiredValue(Configuration.baseUrl(event)));
        //not a number
        assertEquals(-1, configurationManager.getIntConfigValue(Configuration.baseUrl(event), -1));
    }

    @Test
    public void testBooleanValue() {
        //missing value
        assertFalse(configurationManager.getBooleanConfigValue(Configuration.allowFreeTicketsCancellation(event), false));

        //false value
        configurationManager.saveSystemConfiguration(ConfigurationKeys.ALLOW_FREE_TICKETS_CANCELLATION, "false");
        assertFalse(configurationManager.getBooleanConfigValue(Configuration.allowFreeTicketsCancellation(event), true));

        //true value
        configurationManager.saveSystemConfiguration(ConfigurationKeys.ALLOW_FREE_TICKETS_CANCELLATION, "true");
        assertTrue(configurationManager.getBooleanConfigValue(Configuration.allowFreeTicketsCancellation(event), false));
    }

    @Test
    public void testOverrideMechanism() {

        Organization organization = organizationRepository.findByName("org").get(0);


        Event event = eventManager.getSingleEvent("eventShortName", "test");

        TicketCategory tc = eventManager.loadTicketCategories(event).get(0);
        //

        //check override level up to event level

        assertEquals(5, configurationManager.getIntConfigValue(Configuration.maxAmountOfTicketsByReservation(event), -1));

        configurationRepository.insertOrganizationLevel(organization.getId(), ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION.getValue(), "6", "desc");

        assertEquals(6, configurationManager.getIntConfigValue(Configuration.maxAmountOfTicketsByReservation(event), -1));

        configurationRepository.insertEventLevel(organization.getId(), event.getId(), ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION.getValue(), "7", "desc");
        assertEquals(7, configurationManager.getIntConfigValue(Configuration.maxAmountOfTicketsByReservation(event), -1));

        configurationRepository.insertTicketCategoryLevel(organization.getId(), event.getId(), tc.getId(), ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION.getValue(), "8", "desc");

        assertEquals(7, configurationManager.getIntConfigValue(Configuration.maxAmountOfTicketsByReservation(event), -1));

    }


}
