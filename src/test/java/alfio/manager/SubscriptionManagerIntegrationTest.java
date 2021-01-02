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
import alfio.model.Event;
import alfio.model.PriceContainer;
import alfio.model.TicketCategory;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.SubscriptionDescriptorModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.modification.UploadBase64FileModification;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.model.transaction.PaymentProxy;
import alfio.repository.EventRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.BaseIntegrationTest;
import alfio.util.ClockProvider;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
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
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static alfio.test.util.IntegrationTestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

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

    @Autowired
    FileUploadManager fileUploadManager;

    @Autowired
    EventManager eventManager;

    @Autowired
    EventRepository eventRepository;

    private String fileBlobId;
    private Event event;
    private String username;

    @Before
    public void setup() {
        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);
        initAdminUser(userRepository, authorityRepository);
        UploadBase64FileModification toInsert = new UploadBase64FileModification();
        toInsert.setFile(BaseIntegrationTest.ONE_PIXEL_BLACK_GIF);
        toInsert.setName("image.gif");
        toInsert.setType("image/gif");
        fileBlobId = fileUploadManager.insertFile(toInsert);

        //create test event
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()).minusDays(1), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()).plusDays(1), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> eventAndUser = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);

        event = eventAndUser.getLeft();
        username = eventAndUser.getRight();
    }

    @Test
    public void testCreateRead() {
        int orgId = event.getOrganizationId();

        assertTrue(subscriptionManager.findAll(orgId).isEmpty());
        subscriptionManager.createSubscriptionDescriptor(buildSubscriptionDescriptor(orgId, null, new BigDecimal("100")));
        var res = subscriptionManager.findAll(orgId);
        assertEquals(1, res.size());
        var descriptor = res.get(0);
        assertEquals("title", descriptor.getTitle().get("en"));
        assertEquals("description", descriptor.getDescription().get("en"));
        assertEquals(10000, descriptor.getPrice());
        assertEquals("en", descriptor.getContentLanguages().get(0).getLanguage());

        // update price
        subscriptionManager.updateSubscriptionDescriptor(buildSubscriptionDescriptor(orgId, descriptor.getId(), new BigDecimal("200")));

        res = subscriptionManager.findAll(orgId);
        assertEquals(1, res.size());
        descriptor = res.get(0);
        assertEquals("title", descriptor.getTitle().get("en"));
        assertEquals("description", descriptor.getDescription().get("en"));
        assertEquals(20000, descriptor.getPrice());

        var publicSubscriptions = subscriptionManager.getActivePublicSubscriptionsDescriptor(ZonedDateTime.now(ClockProvider.clock()));
        assertEquals(0, publicSubscriptions.size());

        subscriptionManager.setPublicStatus(descriptor.getId(), orgId, true);
        publicSubscriptions = subscriptionManager.getActivePublicSubscriptionsDescriptor(ZonedDateTime.now(ClockProvider.clock()));
        assertEquals(1, publicSubscriptions.size());
        assertEquals(res.get(0).getId(), publicSubscriptions.get(0).getId());

        assertTrue(subscriptionManager.getSubscriptionById(publicSubscriptions.get(0).getId()).isPresent());

        var subscriptionsWithStatistics = subscriptionManager.loadSubscriptionsWithStatistics(orgId);
        assertEquals(1, subscriptionsWithStatistics.size());
        assertEquals(0, subscriptionsWithStatistics.get(0).getSoldCount());

        assertEquals(1, subscriptionManager.linkSubscriptionToEvent(descriptor.getId(), event.getId(), orgId, 0));
        var links = subscriptionManager.getLinkedEvents(orgId, descriptor.getId());
        assertFalse(links.isEmpty());
        assertEquals(event.getId(), links.get(0).getEventId());
        assertEquals(descriptor.getId(), links.get(0).getSubscriptionDescriptorId());
        assertEquals(0, links.get(0).getPricePerTicket());
    }

    private SubscriptionDescriptorModification buildSubscriptionDescriptor(int orgId, UUID id, BigDecimal price) {

        return new SubscriptionDescriptorModification(id,
            Map.of("en", "title"),
            Map.of("en", "description"),
            42,
            ZonedDateTime.now(ClockProvider.clock()),
            null,
            price,
            new BigDecimal("7.7"),
            PriceContainer.VatStatus.INCLUDED,
            "CHF",
            false,
            orgId,
            42,
            SubscriptionDescriptor.SubscriptionValidityType.CUSTOM,
            null,
            null,
            ZonedDateTime.now(ClockProvider.clock()).minusDays(1),
            ZonedDateTime.now(ClockProvider.clock()).plusDays(42),
            SubscriptionDescriptor.SubscriptionUsageType.ONCE_PER_EVENT,
            "https://example.org",
            null,
            fileBlobId,
            List.of(PaymentProxy.STRIPE));
    }

}
