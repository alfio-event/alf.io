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
import alfio.model.user.User;
import alfio.repository.EventRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;

public class IntegrationTestUtil {

    public static final int AVAILABLE_SEATS = 20;


    public static final Map<String, Map<String, String>> DB_CONF = new HashMap<>();
    public static final Map<String, String> DESCRIPTION = Collections.singletonMap("en", "desc");

    static {
        DB_CONF.put("PGSQL", generateDBConfig("jdbc:postgresql://localhost:5432/alfio", "postgres", "password"));
        DB_CONF.put("PGSQL-TRAVIS", generateDBConfig("jdbc:postgresql://localhost:5432/alfio", "postgres", ""));
    }

    public static Map<String, String> generateDBConfig(String url, String username, String password) {
        Map<String, String> c = new HashMap<>();
        c.put("datasource.url", url);
        c.put("datasource.username", username);
        c.put("datasource.password", password);
        return c;
    }

    public static void ensureMinimalConfiguration(ConfigurationRepository configurationRepository) {
        configurationRepository.deleteByKey(ConfigurationKeys.BASE_URL.getValue());
        configurationRepository.deleteByKey(ConfigurationKeys.SUPPORTED_LANGUAGES.getValue());

        configurationRepository.insert(ConfigurationKeys.BASE_URL.getValue(), "http://localhost:8080", "");
        configurationRepository.insert(ConfigurationKeys.SUPPORTED_LANGUAGES.getValue(), "7", "");


        configurationRepository.deleteByKey(ConfigurationKeys.INVOICE_ADDRESS.getValue());
        configurationRepository.insert(ConfigurationKeys.INVOICE_ADDRESS.getValue(), "INVOICE_ADDRESS", "");
        configurationRepository.deleteByKey(ConfigurationKeys.VAT_NR.getValue());
        configurationRepository.insert(ConfigurationKeys.VAT_NR.getValue(), "42", "");
    }

    public static Pair<Event, String> initEvent(List<TicketCategoryModification> categories,
                                                OrganizationRepository organizationRepository,
                                                UserManager userManager,
                                                EventManager eventManager,
                                                EventRepository eventRepository) {
        return initEvent(categories, organizationRepository, userManager, eventManager, eventRepository, null);
    }

    public static Pair<Event, String> initEvent(List<TicketCategoryModification> categories,
                                                OrganizationRepository organizationRepository,
                                                UserManager userManager,
                                                EventManager eventManager,
                                                EventRepository eventRepository,
                                                List<EventModification.AdditionalService> additionalServices) {

        String organizationName = UUID.randomUUID().toString();
        String username = UUID.randomUUID().toString();
        String eventName = UUID.randomUUID().toString();

        userManager.createOrganization(organizationName, "org", "email@example.com");
        Organization organization = organizationRepository.findByName(organizationName).get();
        userManager.insertUser(organization.getId(), username, "test", "test", "test@example.com", Role.OPERATOR, User.Type.INTERNAL);
        userManager.insertUser(organization.getId(), username+"_owner", "test", "test", "test@example.com", Role.OWNER, User.Type.INTERNAL);

        LocalDateTime expiration = LocalDateTime.now().plusDays(5).plusHours(1);

        Map<String, String> desc = new HashMap<>();
        desc.put("en", "muh description");
        desc.put("it", "muh description");
        desc.put("de", "muh description");

        EventModification em = new EventModification(null, Event.EventType.INTERNAL, "url", "url", "url", "privacy","url", null,
                eventName, "event display name", organization.getId(),
                "muh location", "0.0", "0.0", ZoneId.systemDefault().getId(), desc,
                new DateTimeModification(LocalDate.now().plusDays(5), LocalTime.now()),
                new DateTimeModification(expiration.toLocalDate(), expiration.toLocalTime()),
                BigDecimal.TEN, "CHF", AVAILABLE_SEATS, BigDecimal.ONE, true, Collections.singletonList(PaymentProxy.OFFLINE), categories, false, new LocationDescriptor("","","",""), 7, null, additionalServices);
        eventManager.createEvent(em);
        Event event = eventManager.getSingleEvent(eventName, username);
        Assert.assertEquals(AVAILABLE_SEATS, eventRepository.countExistingTickets(event.getId()).intValue());
        return Pair.of(event, username);
    }

    public static void initAdminUser(UserRepository userRepository, AuthorityRepository authorityRepository) {
        userRepository.create(UserManager.ADMIN_USERNAME, "", "The", "Administrator", "admin@localhost", true, User.Type.INTERNAL, null, null);
        authorityRepository.create(UserManager.ADMIN_USERNAME, Role.ADMIN.getRoleName());
    }

    public static void removeAdminUser(UserRepository userRepository, AuthorityRepository authorityRepository) {
        authorityRepository.revokeAll(UserManager.ADMIN_USERNAME);
        userRepository.deleteUser(userRepository.findIdByUserName(UserManager.ADMIN_USERNAME).get());
    }
}
