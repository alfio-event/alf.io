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
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.TicketCategory;
import alfio.model.metadata.AlfioMetadata;
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
import alfio.util.BaseIntegrationTest;
import alfio.util.ClockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static alfio.model.system.ConfigurationKeys.*;
import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
@Transactional
public class ConfigurationManagerIntegrationTest extends BaseIntegrationTest {

    public static final String USERNAME = "test";

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

    @BeforeEach
    public void prepareEnv() {
        //setup...
        organizationRepository.create("org", "org", "email@example.com", null, null);
        Organization organization = organizationRepository.findByName("org").get();

        userManager.insertUser(organization.getId(), USERNAME, "test", "test", "test@example.com", Role.OWNER, User.Type.INTERNAL);

        Map<String, String> desc = new HashMap<>();
        desc.put("en", "muh description");
        desc.put("it", "muh description");
        desc.put("de", "muh description");

        List<TicketCategoryModification> ticketsCategory = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 20,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
                Collections.singletonMap("en", "desc"), BigDecimal.TEN, false, "", false, null, null,
                null, null, null, 0, null, null, AlfioMetadata.empty()));
        EventModification em = new EventModification(null, Event.EventFormat.IN_PERSON, "url", "url", "url", null, null, null,
            "eventShortName", "displayName", organization.getId(),
            "muh location", "0.0", "0.0", ZoneId.systemDefault().getId(), desc,
            new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
            new DateTimeModification(LocalDate.now(ClockProvider.clock()), LocalTime.now(ClockProvider.clock())),
            BigDecimal.TEN, "CHF", 20, BigDecimal.ONE, true, null, ticketsCategory, false, new LocationDescriptor("","","",""), 7, null, null, AlfioMetadata.empty(), List.of());
        eventManager.createEvent(em, USERNAME);

        event = eventManager.getSingleEvent("eventShortName", "test");
        ticketCategory = ticketCategoryRepository.findAllTicketCategories(event.getId()).get(0);
    }

    @Test
    public void testPresentStringConfigValue() {
        assertEquals(Optional.of("5"), configurationManager.getFor(MAX_AMOUNT_OF_TICKETS_BY_RESERVATION, ConfigurationLevel.event(event)).getValue());
    }

    @Test
    public void testEmptyStringConfigValue() {
        assertTrue(configurationManager.getFor(SMTP_PASSWORD, ConfigurationLevel.event(event)).getValue().isEmpty());
    }

    @Test
    public void testStringValueWithDefault() {
        assertEquals("5", configurationManager.getFor(MAX_AMOUNT_OF_TICKETS_BY_RESERVATION, ConfigurationLevel.event(event)).getRequiredValue());
        assertEquals("-1", configurationManager.getFor(SMTP_PASSWORD, ConfigurationLevel.event(event)).getValueOrDefault("-1"));
    }

    @Test
    public void testMissingConfigValue() {
        assertThrows(IllegalArgumentException.class, () -> configurationManager.getFor(SMTP_PASSWORD, ConfigurationLevel.event(event)).getRequiredValue());
    }

    @Test
    public void testRequiredValue() {
        assertEquals("5", configurationManager.getFor(MAX_AMOUNT_OF_TICKETS_BY_RESERVATION, ConfigurationLevel.event(event)).getRequiredValue());
    }

    @Test
    public void testIntValue() {
        assertEquals(5, configurationManager.getFor(MAX_AMOUNT_OF_TICKETS_BY_RESERVATION, ConfigurationLevel.event(event)).getValueAsIntOrDefault(-1));

        //missing value
        assertEquals(-1, configurationManager.getFor(ASSIGNMENT_REMINDER_INTERVAL, ConfigurationLevel.event(event)).getValueAsIntOrDefault(-1));


        configurationManager.saveSystemConfiguration(ConfigurationKeys.BASE_URL, "blabla");
        assertEquals("blabla", configurationManager.getFor(ConfigurationKeys.BASE_URL, ConfigurationLevel.event(event)).getRequiredValue());
        //not a number
        assertEquals(-1, configurationManager.getFor(ConfigurationKeys.BASE_URL, ConfigurationLevel.event(event)).getValueAsIntOrDefault(-1));
    }

    @Test
    public void testBooleanValue() {
        //missing value
        assertFalse(configurationManager.getFor(ALLOW_FREE_TICKETS_CANCELLATION, ConfigurationLevel.ticketCategory(event, ticketCategory.getId())).getValueAsBooleanOrDefault());

        //false value
        configurationManager.saveSystemConfiguration(ConfigurationKeys.ALLOW_FREE_TICKETS_CANCELLATION, "false");
        assertFalse(configurationManager.getFor(ALLOW_FREE_TICKETS_CANCELLATION, ConfigurationLevel.ticketCategory(event, ticketCategory.getId())).getValueAsBooleanOrDefault());

        //true value
        configurationManager.saveSystemConfiguration(ConfigurationKeys.ALLOW_FREE_TICKETS_CANCELLATION, "true");
        assertTrue(configurationManager.getFor(ALLOW_FREE_TICKETS_CANCELLATION, ConfigurationLevel.ticketCategory(event, ticketCategory.getId())).getValueAsBooleanOrDefault());
    }

    @Test
    public void testOverrideMechanism() {

        Organization organization = organizationRepository.findByName("org").orElseThrow();


        Event event = eventManager.getSingleEvent("eventShortName", "test");

        TicketCategory tc = eventManager.loadTicketCategories(event).get(0);
        //

        //check override level up to event level

        assertEquals(5, configurationManager.getFor(Collections.singleton(MAX_AMOUNT_OF_TICKETS_BY_RESERVATION), ConfigurationLevel.event(event)).get(MAX_AMOUNT_OF_TICKETS_BY_RESERVATION).getValueAsIntOrDefault(Integer.MIN_VALUE));

        assertEquals(5, configurationManager.getFor(MAX_AMOUNT_OF_TICKETS_BY_RESERVATION, ConfigurationLevel.event(event)).getValueAsIntOrDefault(-1));

        configurationRepository.insertOrganizationLevel(organization.getId(), MAX_AMOUNT_OF_TICKETS_BY_RESERVATION.getValue(), "6", "desc");

        assertEquals(6, configurationManager.getFor(MAX_AMOUNT_OF_TICKETS_BY_RESERVATION, ConfigurationLevel.event(event)).getValueAsIntOrDefault(-1));

        assertEquals(6, configurationManager.getFor(Collections.singleton(MAX_AMOUNT_OF_TICKETS_BY_RESERVATION), ConfigurationLevel.event(event)).get(MAX_AMOUNT_OF_TICKETS_BY_RESERVATION).getValueAsIntOrDefault(Integer.MIN_VALUE));

        configurationRepository.insertEventLevel(organization.getId(), event.getId(), MAX_AMOUNT_OF_TICKETS_BY_RESERVATION.getValue(), "7", "desc");

        assertEquals(7, configurationManager.getFor(Collections.singleton(MAX_AMOUNT_OF_TICKETS_BY_RESERVATION), ConfigurationLevel.event(event)).get(MAX_AMOUNT_OF_TICKETS_BY_RESERVATION).getValueAsIntOrDefault(Integer.MIN_VALUE));

        configurationRepository.insertTicketCategoryLevel(organization.getId(), event.getId(), tc.getId(), MAX_AMOUNT_OF_TICKETS_BY_RESERVATION.getValue(), "8", "desc");

        assertEquals(7, configurationManager.getFor(MAX_AMOUNT_OF_TICKETS_BY_RESERVATION, ConfigurationLevel.event(event)).getValueAsIntOrDefault(-1));

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
        assertEquals(ConfigurationKeys.byPathLevel(ConfigurationPathLevel.ORGANIZATION).size(), orgConf.values().stream().mapToLong(Collection::size).sum());
        String value = "MY-ACCOUNT_NUMBER";
        configurationRepository.insertOrganizationLevel(event.getOrganizationId(), ConfigurationKeys.BANK_ACCOUNT_NR.getValue(), value, "empty");
        orgConf = configurationManager.loadOrganizationConfig(event.getOrganizationId(), USERNAME);
        assertEquals(ConfigurationKeys.byPathLevel(ConfigurationPathLevel.ORGANIZATION).size(), orgConf.values().stream().mapToLong(Collection::size).sum());
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

        Optional<Configuration> opt = configurationRepository.findOptionalByKey(GOOGLE_ANALYTICS_ANONYMOUS_MODE.getValue());
        assertFalse(opt.isPresent());

    }

    @Test
    public void testBulk() {
        Event event = eventManager.getSingleEvent("eventShortName", "test");

        var res = configurationManager.getFor(Set.of(MAX_AMOUNT_OF_TICKETS_BY_RESERVATION, ENABLE_WAITING_QUEUE, ENABLE_WAITING_QUEUE_NOTIFICATION), ConfigurationLevel.event(event));

        assertEquals(3, res.size());
        assertNotNull(res.get(MAX_AMOUNT_OF_TICKETS_BY_RESERVATION));
        assertNotNull(res.get(ENABLE_WAITING_QUEUE));
        assertNotNull(res.get(ENABLE_WAITING_QUEUE_NOTIFICATION));
        assertTrue(res.get(MAX_AMOUNT_OF_TICKETS_BY_RESERVATION).isPresent());
        assertEquals(5, res.get(MAX_AMOUNT_OF_TICKETS_BY_RESERVATION).getValueAsIntOrDefault(Integer.MIN_VALUE));
        assertEquals(ConfigurationPathLevel.SYSTEM, res.get(MAX_AMOUNT_OF_TICKETS_BY_RESERVATION).getConfigurationPathLevelOrDefault(null));
        assertFalse(res.get(ENABLE_WAITING_QUEUE).isPresent());
        assertFalse(res.get(ENABLE_WAITING_QUEUE_NOTIFICATION).isPresent());

        configurationRepository.insertOrganizationLevel(event.getOrganizationId(), ENABLE_WAITING_QUEUE.getValue(), "true", "");
        configurationRepository.insertOrganizationLevel(event.getOrganizationId(), ENABLE_WAITING_QUEUE_NOTIFICATION.getValue(), "false", "");


        res = configurationManager.getFor(Set.of(MAX_AMOUNT_OF_TICKETS_BY_RESERVATION, ENABLE_WAITING_QUEUE, ENABLE_WAITING_QUEUE_NOTIFICATION), ConfigurationLevel.event(event));
        assertEquals(3, res.size());
        assertTrue(res.get(MAX_AMOUNT_OF_TICKETS_BY_RESERVATION).isPresent());
        assertTrue(res.get(ENABLE_WAITING_QUEUE).isPresent());
        assertTrue(res.get(ENABLE_WAITING_QUEUE_NOTIFICATION).isPresent());

        assertEquals(ConfigurationPathLevel.ORGANIZATION, res.get(ENABLE_WAITING_QUEUE).getConfigurationPathLevelOrDefault(null));
        assertEquals(ConfigurationPathLevel.ORGANIZATION, res.get(ENABLE_WAITING_QUEUE_NOTIFICATION).getConfigurationPathLevelOrDefault(null));
        assertTrue(res.get(ENABLE_WAITING_QUEUE).getValueAsBooleanOrDefault());
        assertFalse(res.get(ENABLE_WAITING_QUEUE_NOTIFICATION).getValueAsBooleanOrDefault());


        configurationRepository.insertEventLevel(event.getOrganizationId(), event.getId(), MAX_AMOUNT_OF_TICKETS_BY_RESERVATION.getValue(), "20", "");
        configurationRepository.insertEventLevel(event.getOrganizationId(), event.getId(), ENABLE_WAITING_QUEUE.getValue(), "true", "");
        configurationRepository.insertEventLevel(event.getOrganizationId(), event.getId(), ENABLE_WAITING_QUEUE_NOTIFICATION.getValue(), "true", "");

        res = configurationManager.getFor(Set.of(MAX_AMOUNT_OF_TICKETS_BY_RESERVATION, ENABLE_WAITING_QUEUE, ENABLE_WAITING_QUEUE_NOTIFICATION), ConfigurationLevel.event(event));

        assertEquals(ConfigurationPathLevel.EVENT, res.get(MAX_AMOUNT_OF_TICKETS_BY_RESERVATION).getConfigurationPathLevelOrDefault(null));
        assertEquals(20, res.get(MAX_AMOUNT_OF_TICKETS_BY_RESERVATION).getValueAsIntOrDefault(Integer.MIN_VALUE));
        assertEquals(ConfigurationPathLevel.EVENT, res.get(ENABLE_WAITING_QUEUE).getConfigurationPathLevelOrDefault(null));
        assertEquals(ConfigurationPathLevel.EVENT, res.get(ENABLE_WAITING_QUEUE_NOTIFICATION).getConfigurationPathLevelOrDefault(null));
    }

    @Test
    void testBaseUrl() {
        configurationRepository.insertEventLevel(event.getOrganizationId(), event.getId(), BASE_URL.getValue(), "https://test/", "");
        assertEquals("https://test", configurationManager.baseUrl(event));
    }
}