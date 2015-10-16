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
import alfio.model.modification.ConfigurationModification;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.EventModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.modification.support.LocationDescriptor;
import alfio.model.system.ComponentType;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.model.system.ConfigurationPathLevel;
import alfio.model.user.Organization;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.AuthorityRepository;
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
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS})
@Transactional
public class ConfigurationManagerTest {

    public static final String USERNAME = "test";

    @BeforeClass
    public static void initEnv() {
        IntegrationTestUtil.initSystemProperties();
    }

    Event event;
    TicketCategory ticketCategory;

    @Autowired
    private ConfigurationManager configurationManager;

    @Autowired
    private ConfigurationRepository configurationRepository;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private UserManager userManager;

    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;

    @Before
    public void prepareEnv() {
        //setup...
        organizationRepository.create("org", "org", "email@example.com");
        Organization organization = organizationRepository.findByName("org").get(0);

        userManager.insertUser(organization.getId(), USERNAME, "test", "test", "test@example.com");
        authorityRepository.create(USERNAME, AuthorityRepository.ROLE_OWNER);

        Map<String, String> desc = new HashMap<>();
        desc.put("en", "muh description");
        desc.put("it", "muh description");
        desc.put("de", "muh description");

        List<TicketCategoryModification> ticketsCategory = Collections.singletonList(
            new TicketCategoryModification(null, "default", 20,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                Collections.singletonMap("en", "desc"), BigDecimal.TEN, false, "", false));
        EventModification em = new EventModification(null, "url", "url", "url", null,
            "eventShortName", "displayName", organization.getId(),
            "muh location", desc,
            new DateTimeModification(LocalDate.now(), LocalTime.now()),
            new DateTimeModification(LocalDate.now(), LocalTime.now()),
            BigDecimal.TEN, "CHF", 20, BigDecimal.ONE, true, null, ticketsCategory, false, new LocationDescriptor("","","",""), 7);
        eventManager.createEvent(em);

        event = eventManager.getSingleEvent("eventShortName", "test");
        ticketCategory = ticketCategoryRepository.findAllTicketCategories(event.getId()).get(0);
    }

    @Test
    public void testPresentStringConfigValue() {
        assertEquals(Optional.of("5"), configurationManager.getStringConfigValue(Configuration.maxAmountOfTicketsByReservation(event)));
    }

    @Test
    public void testEmptyStringConfigValue() {
        assertEquals(Optional.empty(), configurationManager.getStringConfigValue(Configuration.smtpPassword()));
    }

    @Test
    public void testStringValueWithDefault() {
        assertEquals("5", configurationManager.getStringConfigValue(Configuration.maxAmountOfTicketsByReservation(event), "-1"));
        assertEquals("-1", configurationManager.getStringConfigValue(Configuration.smtpPassword(), "-1"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingConfigValue() {
        configurationManager.getRequiredValue(Configuration.smtpPassword());
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
        assertEquals("blabla", configurationManager.getRequiredValue(Configuration.baseUrl()));
        //not a number
        assertEquals(-1, configurationManager.getIntConfigValue(Configuration.baseUrl(), -1));
    }

    @Test
    public void testBooleanValue() {
        //missing value
        assertFalse(configurationManager.getBooleanConfigValue(Configuration.allowFreeTicketsCancellation(event, ticketCategory), false));

        //false value
        configurationManager.saveSystemConfiguration(ConfigurationKeys.ALLOW_FREE_TICKETS_CANCELLATION, "false");
        assertFalse(configurationManager.getBooleanConfigValue(Configuration.allowFreeTicketsCancellation(event, ticketCategory), true));

        //true value
        configurationManager.saveSystemConfiguration(ConfigurationKeys.ALLOW_FREE_TICKETS_CANCELLATION, "true");
        assertTrue(configurationManager.getBooleanConfigValue(Configuration.allowFreeTicketsCancellation(event, ticketCategory), false));
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

    @Test
    public void testBasicConfigurationNotNeeded() {

        configurationRepository.update(ConfigurationKeys.BASE_URL.getValue(), "http://localhost:8080");
        configurationRepository.update(ConfigurationKeys.SUPPORTED_LANGUAGES.getValue(), "7");
        configurationRepository.insert(ConfigurationKeys.MAPS_SERVER_API_KEY.getValue(), "MAPS_SERVER_API_KEY", "");
        configurationRepository.insert(ConfigurationKeys.MAPS_CLIENT_API_KEY.getValue(), "MAPS_CLIENT_API_KEY", "");
        configurationRepository.insert(ConfigurationKeys.MAILER_TYPE.getValue(), "smtp", "");
        assertFalse(configurationManager.isBasicConfigurationNeeded());
    }

    @Test
    public void testSaveOnlyExistingConfiguration() {
        configurationRepository.insertOrganizationLevel(event.getOrganizationId(), ConfigurationKeys.BANK_ACCOUNT_NR.getValue(), "MY-ACCOUNT_NUMBER", "empty");
        Configuration existing = configurationRepository.findByKeyAtOrganizationLevel(event.getOrganizationId(), ConfigurationKeys.BANK_ACCOUNT_NR.getValue()).orElseThrow(IllegalStateException::new);
        Map<ConfigurationKeys.SettingCategory, List<Configuration>> all = configurationManager.loadOrganizationConfig(event.getOrganizationId(), USERNAME);
        List<Configuration> flatten = all.entrySet().stream().flatMap(e -> e.getValue().stream()).collect(Collectors.toList());
        List<ConfigurationModification> modified = flatten.stream().filter(c -> !c.getKey().equals(ConfigurationKeys.BANK_ACCOUNT_NR.getValue())).map(ConfigurationModification::fromConfiguration).collect(Collectors.toList());
        modified.add(new ConfigurationModification(existing.getId(), existing.getKey(), "NEW-NUMBER"));
        configurationManager.saveAllOrganizationConfiguration(event.getOrganizationId(), modified, USERNAME);
        List<Configuration> organizationConfiguration = configurationRepository.findOrganizationConfiguration(event.getOrganizationId());
        assertEquals(5, organizationConfiguration.size());
        assertTrue(organizationConfiguration.stream().filter(o -> o.getComponentType() == ComponentType.BOOLEAN).count() == 4);
        Optional<Configuration> result = configurationRepository.findByKeyAtOrganizationLevel(event.getOrganizationId(), ConfigurationKeys.BANK_ACCOUNT_NR.getValue());
        assertTrue(result.isPresent());
        Configuration configuration = result.get();
        assertEquals(ConfigurationKeys.BANK_ACCOUNT_NR, configuration.getConfigurationKey());
        assertEquals("NEW-NUMBER", configuration.getValue());
    }

    @Test
    public void testSaveOnlyValidConfiguration() {
        configurationRepository.insertOrganizationLevel(event.getOrganizationId(), ConfigurationKeys.BANK_ACCOUNT_NR.getValue(), "MY-ACCOUNT_NUMBER", "empty");
        Configuration existing = configurationRepository.findByKeyAtOrganizationLevel(event.getOrganizationId(), ConfigurationKeys.BANK_ACCOUNT_NR.getValue()).orElseThrow(IllegalStateException::new);
        Map<ConfigurationKeys.SettingCategory, List<Configuration>> all = configurationManager.loadOrganizationConfig(event.getOrganizationId(), USERNAME);
        List<Configuration> flatten = all.entrySet().stream().flatMap(e -> e.getValue().stream()).collect(Collectors.toList());
        List<ConfigurationModification> modified = flatten.stream().filter(c -> !c.getKey().equals(ConfigurationKeys.BANK_ACCOUNT_NR.getValue()) && !c.getKey().equals(ConfigurationKeys.PARTIAL_RESERVATION_ID_LENGTH.getValue())).map(ConfigurationModification::fromConfiguration).collect(Collectors.toList());
        modified.add(new ConfigurationModification(existing.getId(), existing.getKey(), "NEW-NUMBER"));
        modified.add(new ConfigurationModification(-1, ConfigurationKeys.PARTIAL_RESERVATION_ID_LENGTH.getValue(), "9"));
        configurationManager.saveAllOrganizationConfiguration(event.getOrganizationId(), modified, USERNAME);
        List<Configuration> organizationConfiguration = configurationRepository.findOrganizationConfiguration(event.getOrganizationId());
        assertEquals(6, organizationConfiguration.size());
        assertTrue(organizationConfiguration.stream().filter(o -> o.getComponentType() == ComponentType.BOOLEAN).count() == 4);
        Optional<Configuration> result = configurationRepository.findByKeyAtOrganizationLevel(event.getOrganizationId(), ConfigurationKeys.BANK_ACCOUNT_NR.getValue());
        assertTrue(result.isPresent());
        Configuration configuration = result.get();
        assertEquals(ConfigurationKeys.BANK_ACCOUNT_NR, configuration.getConfigurationKey());
        assertEquals("NEW-NUMBER", configuration.getValue());
        result = configurationRepository.findByKeyAtOrganizationLevel(event.getOrganizationId(), ConfigurationKeys.PARTIAL_RESERVATION_ID_LENGTH.getValue());
        assertTrue(result.isPresent());
        configuration = result.get();
        assertEquals(ConfigurationKeys.PARTIAL_RESERVATION_ID_LENGTH, configuration.getConfigurationKey());
        assertEquals("9", configuration.getValue());
    }

    @Test
    public void testLoadOrganizationConfiguration() {
        Map<ConfigurationKeys.SettingCategory, List<Configuration>> orgConf = configurationManager.loadOrganizationConfig(event.getOrganizationId(), USERNAME);
        assertEquals(3, orgConf.size());
        assertEquals(ConfigurationKeys.byPathLevel(ConfigurationPathLevel.ORGANIZATION).size(), orgConf.values().stream().flatMap(Collection::stream).count());
        String value = "MY-ACCOUNT_NUMBER";
        configurationRepository.insertOrganizationLevel(event.getOrganizationId(), ConfigurationKeys.BANK_ACCOUNT_NR.getValue(), value, "empty");
        orgConf = configurationManager.loadOrganizationConfig(event.getOrganizationId(), USERNAME);
        assertEquals(3, orgConf.size());
        assertEquals(ConfigurationKeys.byPathLevel(ConfigurationPathLevel.ORGANIZATION).size(), orgConf.values().stream().flatMap(Collection::stream).count());
        assertEquals(value, orgConf.get(ConfigurationKeys.SettingCategory.PAYMENT).stream().filter(c -> c.getConfigurationKey() == ConfigurationKeys.BANK_ACCOUNT_NR).findFirst().orElseThrow(IllegalStateException::new).getValue());
    }

    @Test
    public void testBasicConfigurationNeeded() {
        configurationRepository.deleteByKey(ConfigurationKeys.BASE_URL.getValue());
        assertTrue(configurationManager.isBasicConfigurationNeeded());
    }


}
