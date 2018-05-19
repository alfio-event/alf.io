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
import alfio.config.RepositoryConfiguration;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.TicketCategory;
import alfio.model.modification.ConfigurationModification;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.EventModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.modification.support.LocationDescriptor;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.model.system.ConfigurationPathLevel;
import alfio.model.user.Organization;
import alfio.model.user.Role;
import alfio.model.user.User;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.OptionalWrapper;
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
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static alfio.model.system.ConfigurationKeys.*;
import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {RepositoryConfiguration.class, DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS})
@Transactional
public class ConfigurationManagerIntegrationTest {

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
    private UserManager userManager;

    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;

    @Before
    public void prepareEnv() {
        //setup...
        organizationRepository.create("org", "org", "email@example.com");
        Organization organization = organizationRepository.findByName("org").get();

        userManager.insertUser(organization.getId(), USERNAME, "test", "test", "test@example.com", Role.OWNER, User.Type.INTERNAL);

        Map<String, String> desc = new HashMap<>();
        desc.put("en", "muh description");
        desc.put("it", "muh description");
        desc.put("de", "muh description");

        List<TicketCategoryModification> ticketsCategory = Collections.singletonList(
            new TicketCategoryModification(null, "default", 20,
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                new DateTimeModification(LocalDate.now(), LocalTime.now()),
                Collections.singletonMap("en", "desc"), BigDecimal.TEN, false, "", false, null, null,
                null, null, null));
        EventModification em = new EventModification(null, Event.EventType.INTERNAL, "url", "url", "url", "privacy", null, null,
            "eventShortName", "displayName", organization.getId(),
            "muh location", "0.0", "0.0", ZoneId.systemDefault().getId(), desc,
            new DateTimeModification(LocalDate.now(), LocalTime.now()),
            new DateTimeModification(LocalDate.now(), LocalTime.now()),
            BigDecimal.TEN, "CHF", 20, BigDecimal.ONE, true, null, ticketsCategory, false, new LocationDescriptor("","","",""), 7, null, null);
        eventManager.createEvent(em);

        event = eventManager.getSingleEvent("eventShortName", "test");
        ticketCategory = ticketCategoryRepository.findAllTicketCategories(event.getId()).get(0);
    }

    @Test
    public void testPresentStringConfigValue() {
        assertEquals(Optional.of("5"), configurationManager.getStringConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), MAX_AMOUNT_OF_TICKETS_BY_RESERVATION)));
    }

    @Test
    public void testEmptyStringConfigValue() {
        assertEquals(Optional.empty(), configurationManager.getStringConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), SMTP_PASSWORD)));
    }

    @Test
    public void testStringValueWithDefault() {
        assertEquals("5", configurationManager.getStringConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), MAX_AMOUNT_OF_TICKETS_BY_RESERVATION), "-1"));
        assertEquals("-1", configurationManager.getStringConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), SMTP_PASSWORD), "-1"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingConfigValue() {
        configurationManager.getRequiredValue(Configuration.from(event.getOrganizationId(), event.getId(), SMTP_PASSWORD));
    }

    @Test
    public void testRequiredValue() {
        assertEquals("5", configurationManager.getRequiredValue(Configuration.from(event.getOrganizationId(), event.getId(), MAX_AMOUNT_OF_TICKETS_BY_RESERVATION)));
    }

    @Test
    public void testIntValue() {
        assertEquals(5, configurationManager.getIntConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), MAX_AMOUNT_OF_TICKETS_BY_RESERVATION), -1));

        //missing value
        assertEquals(-1, configurationManager.getIntConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), ASSIGNMENT_REMINDER_INTERVAL), -1));


        configurationManager.saveSystemConfiguration(ConfigurationKeys.BASE_URL, "blabla");
        assertEquals("blabla", configurationManager.getRequiredValue(Configuration.from(event.getOrganizationId(), event.getId(), ConfigurationKeys.BASE_URL)));
        //not a number
        assertEquals(-1, configurationManager.getIntConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), ConfigurationKeys.BASE_URL), -1));
    }

    @Test
    public void testBooleanValue() {
        //missing value
        assertFalse(configurationManager.getBooleanConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), ticketCategory.getId(), ALLOW_FREE_TICKETS_CANCELLATION), false));

        //false value
        configurationManager.saveSystemConfiguration(ConfigurationKeys.ALLOW_FREE_TICKETS_CANCELLATION, "false");
        assertFalse(configurationManager.getBooleanConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), ticketCategory.getId(), ALLOW_FREE_TICKETS_CANCELLATION), true));

        //true value
        configurationManager.saveSystemConfiguration(ConfigurationKeys.ALLOW_FREE_TICKETS_CANCELLATION, "true");
        assertTrue(configurationManager.getBooleanConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), ticketCategory.getId(), ALLOW_FREE_TICKETS_CANCELLATION), false));
    }

    @Test
    public void testOverrideMechanism() {

        Organization organization = organizationRepository.findByName("org").get();


        Event event = eventManager.getSingleEvent("eventShortName", "test");

        TicketCategory tc = eventManager.loadTicketCategories(event).get(0);
        //

        //check override level up to event level

        assertEquals(5, configurationManager.getIntConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), MAX_AMOUNT_OF_TICKETS_BY_RESERVATION), -1));

        configurationRepository.insertOrganizationLevel(organization.getId(), MAX_AMOUNT_OF_TICKETS_BY_RESERVATION.getValue(), "6", "desc");

        assertEquals(6, configurationManager.getIntConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), MAX_AMOUNT_OF_TICKETS_BY_RESERVATION), -1));

        configurationRepository.insertEventLevel(organization.getId(), event.getId(), MAX_AMOUNT_OF_TICKETS_BY_RESERVATION.getValue(), "7", "desc");
        assertEquals(7, configurationManager.getIntConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), MAX_AMOUNT_OF_TICKETS_BY_RESERVATION), -1));

        configurationRepository.insertTicketCategoryLevel(organization.getId(), event.getId(), tc.getId(), MAX_AMOUNT_OF_TICKETS_BY_RESERVATION.getValue(), "8", "desc");

        assertEquals(7, configurationManager.getIntConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), MAX_AMOUNT_OF_TICKETS_BY_RESERVATION), -1));

    }

    @Test
    public void testBasicConfigurationNotNeeded() {

        configurationRepository.deleteByKey(ConfigurationKeys.BASE_URL.getValue());
        configurationRepository.deleteByKey(ConfigurationKeys.SUPPORTED_LANGUAGES.getValue());

        configurationRepository.insert(ConfigurationKeys.BASE_URL.getValue(), "http://localhost:8080", "");
        configurationRepository.insert(ConfigurationKeys.SUPPORTED_LANGUAGES.getValue(), "7", "");
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
        assertEquals(1, organizationConfiguration.size());
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
        assertEquals(2, organizationConfiguration.size());
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
        assertEquals(9, orgConf.size());
        assertEquals(ConfigurationKeys.byPathLevel(ConfigurationPathLevel.ORGANIZATION).size(), orgConf.values().stream().flatMap(Collection::stream).count());
        String value = "MY-ACCOUNT_NUMBER";
        configurationRepository.insertOrganizationLevel(event.getOrganizationId(), ConfigurationKeys.BANK_ACCOUNT_NR.getValue(), value, "empty");
        orgConf = configurationManager.loadOrganizationConfig(event.getOrganizationId(), USERNAME);
        assertEquals(9, orgConf.size());
        assertEquals(ConfigurationKeys.byPathLevel(ConfigurationPathLevel.ORGANIZATION).size(), orgConf.values().stream().flatMap(Collection::stream).count());
        assertEquals(value, orgConf.get(SettingCategory.PAYMENT_OFFLINE).stream().filter(c -> c.getConfigurationKey() == ConfigurationKeys.BANK_ACCOUNT_NR).findFirst().orElseThrow(IllegalStateException::new).getValue());
    }

    @Test
    public void testBasicConfigurationNeeded() {
        configurationRepository.deleteByKey(ConfigurationKeys.BASE_URL.getValue());
        assertTrue(configurationManager.isBasicConfigurationNeeded());
    }

    @Test
    public void testSaveBooleanOptions() {
        String ftcKey = ALLOW_FREE_TICKETS_CANCELLATION.getValue();
        configurationRepository.insert(ftcKey, "false", "this should be updated to true");
        ConfigurationModification ftc = new ConfigurationModification(configurationRepository.findByKey(ftcKey).getId(), ftcKey, "true");

        String prKey = ENABLE_PRE_REGISTRATION.getValue();
        configurationRepository.insert(prKey, "true", "this should be updated to false");
        ConfigurationModification pr = new ConfigurationModification(configurationRepository.findByKey(prKey).getId(), prKey, "false");

        ConfigurationModification newTrue = new ConfigurationModification(-1, ENABLE_WAITING_QUEUE.getValue(), "true");
        ConfigurationModification newFalse = new ConfigurationModification(-1, ENABLE_WAITING_QUEUE_NOTIFICATION.getValue(), "false");
        ConfigurationModification newNull = new ConfigurationModification(-1, GOOGLE_ANALYTICS_ANONYMOUS_MODE.getValue(), null);

        configurationManager.saveAllSystemConfiguration(Arrays.asList(ftc, pr, newTrue, newFalse, newNull));

        Configuration cFtc = configurationRepository.findByKey(ftcKey);
        assertNotNull(ftc);
        assertEquals("true", cFtc.getValue());

        Configuration cPr = configurationRepository.findByKey(prKey);
        assertNotNull(cPr);
        assertEquals("false", cPr.getValue());

        Configuration nTrue = configurationRepository.findByKey(ENABLE_WAITING_QUEUE.getValue());
        assertNotNull(nTrue);
        assertEquals("true", nTrue.getValue());

        Configuration nFalse = configurationRepository.findByKey(ENABLE_WAITING_QUEUE_NOTIFICATION.getValue());
        assertNotNull(nFalse);
        assertEquals("false", nFalse.getValue());

        Optional<Configuration> opt = OptionalWrapper.optionally(() -> configurationRepository.findByKey(GOOGLE_ANALYTICS_ANONYMOUS_MODE.getValue()));
        assertFalse(opt.isPresent());

    }


}
