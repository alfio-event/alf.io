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
import alfio.manager.user.UserManager;
import alfio.model.PriceContainer;
import alfio.model.SubscriptionDescriptor;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.ClockProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

import static alfio.test.util.IntegrationTestUtil.initAdminUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
@Transactional
public class SubscriptionManagerIntegrationTest {


    @Autowired
    ConfigurationRepository configurationRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    AuthorityRepository authorityRepository;

    @Autowired
    SubscriptionManager subscriptionManager;

    @Autowired
    UserManager userManager;

    @Autowired
    OrganizationRepository organizationRepository;

    @Before
    public void setup() {
        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);
        initAdminUser(userRepository, authorityRepository);
    }

    @Test
    public void testCreateRead() {
        String organizationName = UUID.randomUUID().toString();
        userManager.createOrganization(organizationName, "desc", "email@example.com");
        int orgId = organizationRepository.getIdByName(organizationName);
        assertTrue(subscriptionManager.findAll(orgId).isEmpty());
        subscriptionManager.createSubscriptionDescriptor(new SubscriptionDescriptor(0, 42,
            null, ZonedDateTime.now(ClockProvider.clock()).minusDays(1), ZonedDateTime.now(ClockProvider.clock()).plusDays(42),
            100, new BigDecimal("7.7"), PriceContainer.VatStatus.INCLUDED, "CHF", SubscriptionDescriptor.SubscriptionAvailability.ONCE_PER_EVENT, true,
            Map.of("en", "title"), Map.of("en", "description"), orgId));
        var res = subscriptionManager.findAll(orgId);
        assertEquals(1, res.size());
        assertEquals("title", res.get(0).getTitle().get("en"));
        assertEquals("description", res.get(0).getDescription().get("en"));
    }

}
