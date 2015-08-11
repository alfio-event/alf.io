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
import alfio.model.user.Organization;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import org.apache.commons.lang3.tuple.Pair;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public class IntegrationTestUtil {

    public static final int AVAILABLE_SEATS = 20;

    public static void initSystemProperties() {
        System.setProperty("datasource.dialect", "HSQLDB");
        System.setProperty("datasource.driver", "org.hsqldb.jdbcDriver");
        System.setProperty("datasource.url", "jdbc:hsqldb:mem:alfio");
        System.setProperty("datasource.username", "sa");
        System.setProperty("datasource.password", "");
        System.setProperty("datasource.validationQuery", "SELECT 1 FROM INFORMATION_SCHEMA.SYSTEM_USERS");
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
        userManager.insertUser(organization.getId(), username, "test", "test", "test@example.com");

        EventModification em = new EventModification(null, "url", "url", "url", null,
                eventName, "event display name", organization.getId(),
                "muh location", "muh description",
                new DateTimeModification(LocalDate.now().plusDays(5), LocalTime.now()),
                new DateTimeModification(LocalDate.now().plusDays(6), LocalTime.now()),
                BigDecimal.TEN, "CHF", AVAILABLE_SEATS, BigDecimal.ONE, true, null, categories, false, new LocationDescriptor("","","",""));
        eventManager.createEvent(em);
        return Pair.of(eventManager.getSingleEvent(eventName, username), username);
    }

    public static void initAdminUser(UserRepository userRepository, AuthorityRepository authorityRepository) {
        userRepository.create(UserManager.ADMIN_USERNAME, "", "The", "Administrator", "admin@localhost", true);
        authorityRepository.create(UserManager.ADMIN_USERNAME, AuthorityRepository.ROLE_ADMIN);
    }
}
