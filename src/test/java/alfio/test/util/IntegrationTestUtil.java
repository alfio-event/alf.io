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
package alfio.test.util;

import alfio.manager.EventManager;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.EventModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.modification.support.LocationDescriptor;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Organization;
import alfio.model.user.Role;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import org.apache.commons.lang3.tuple.Pair;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

public class IntegrationTestUtil {

    public static final int AVAILABLE_SEATS = 20;


    private static final Map<String, Map<String, String>> DB_CONF = new HashMap<>();
    static {
        DB_CONF.put("HSQLDB", c("HSQLDB", "org.hsqldb.jdbcDriver", "jdbc:hsqldb:mem:alfio", "sa", "", "SELECT 1 FROM INFORMATION_SCHEMA.SYSTEM_USERS"));
        DB_CONF.put("PGSQL", c("PGSQL", "org.postgresql.Driver", "jdbc:postgresql://localhost:5432/alfio", "postgres", "password", "SELECT 1"));
        DB_CONF.put("PGSQL-TRAVIS", c("PGSQL", "org.postgresql.Driver", "jdbc:postgresql://localhost:5432/alfio", "postgres", "", "SELECT 1"));
        DB_CONF.put("MYSQL", c("MYSQL", "com.mysql.jdbc.Driver", "jdbc:mysql://localhost:3306/alfio", "root", "", "SELECT 1"));
    }

    private static Map<String, String> c(String dialect, String driver, String url, String username, String password, String validationQuery) {
        Map<String, String> c = new HashMap<>();
        c.put("datasource.dialect", dialect);
        c.put("datasource.driver", driver);
        c.put("datasource.url", url);
        c.put("datasource.username", username);
        c.put("datasource.password", password);
        c.put("datasource.validationQuery", validationQuery);
        return c;
    }

    public static void initSystemProperties() {
        String dialect = System.getProperty("dbenv", "HSQLDB");
        DB_CONF.get(dialect).forEach(System::setProperty);
    }

    public static void ensureMinimalConfiguration(ConfigurationRepository configurationRepository) {
        configurationRepository.deleteByKey(ConfigurationKeys.BASE_URL.getValue());
        configurationRepository.deleteByKey(ConfigurationKeys.SUPPORTED_LANGUAGES.getValue());

        configurationRepository.insert(ConfigurationKeys.BASE_URL.getValue(), "http://localhost:8080", "");
        configurationRepository.insert(ConfigurationKeys.SUPPORTED_LANGUAGES.getValue(), "7", "");
    }

    public static Pair<Event, String> initEvent(List<TicketCategoryModification> categories,
                                                OrganizationRepository organizationRepository,
                                                UserManager userManager,
                                                EventManager eventManager) {

        String organizationName = UUID.randomUUID().toString();
        String username = UUID.randomUUID().toString();
        String eventName = UUID.randomUUID().toString();

        organizationRepository.create(organizationName, "org", "email@example.com");
        Organization organization = organizationRepository.findByName(organizationName).get(0);
        userManager.insertUser(organization.getId(), username, "test", "test", "test@example.com", Role.OPERATOR);
        userManager.insertUser(organization.getId(), username+"_owner", "test", "test", "test@example.com", Role.OWNER);

        LocalDateTime expiration = LocalDateTime.now().plusDays(5).plusHours(1);

        Map<String, String> desc = new HashMap<>();
        desc.put("en", "muh description");
        desc.put("it", "muh description");
        desc.put("de", "muh description");

        EventModification em = new EventModification(null, Event.EventType.INTERNAL, "url", "url", "url", "url", null,
                eventName, "event display name", organization.getId(),
                "muh location", desc,
                new DateTimeModification(LocalDate.now().plusDays(5), LocalTime.now()),
                new DateTimeModification(expiration.toLocalDate(), expiration.toLocalTime()),
                BigDecimal.TEN, "CHF", AVAILABLE_SEATS, BigDecimal.ONE, true, Collections.singletonList(PaymentProxy.OFFLINE), categories, false, new LocationDescriptor("","","",""), 7, null, null);
        eventManager.createEvent(em);
        return Pair.of(eventManager.getSingleEvent(eventName, username), username);
    }

    public static void initAdminUser(UserRepository userRepository, AuthorityRepository authorityRepository) {
        userRepository.create(UserManager.ADMIN_USERNAME, "", "The", "Administrator", "admin@localhost", true);
        authorityRepository.create(UserManager.ADMIN_USERNAME, Role.ADMIN.getRoleName());
    }
}
