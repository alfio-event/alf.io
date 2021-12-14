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
import alfio.controller.form.SearchOptions;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.PriceContainer;
import alfio.model.TicketCategory;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.*;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.model.transaction.PaymentProxy;
import alfio.repository.EventDeleterRepository;
import alfio.repository.EventRepository;
import alfio.repository.SubscriptionRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.BaseIntegrationTest;
import alfio.util.ClockProvider;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static alfio.test.util.IntegrationTestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
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

    @Autowired
    EventDeleterRepository eventDeleterRepository;

    @Autowired
    SubscriptionRepository subscriptionRepository;

    @Autowired
    NamedParameterJdbcTemplate jdbcTemplate;

    private String fileBlobId;
    private Event event;
    private String username;

    @BeforeEach
    void setup() {
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

    @AfterEach
    void tearDown() {
        eventDeleterRepository.deleteAllForEvent(event.getId());
    }

    @Test
    void testCreateRead() {
        int orgId = event.getOrganizationId();
        assertTrue(subscriptionManager.findAll(orgId).isEmpty());
        var optionalDescriptorId = subscriptionManager.createSubscriptionDescriptor(buildSubscriptionDescriptor(orgId, null, new BigDecimal("100")));
        assertTrue(optionalDescriptorId.isPresent());
        var descriptorId = optionalDescriptorId.get();
        var count = jdbcTemplate.queryForObject("select count(*) from subscription where subscription_descriptor_fk = :descriptorId", Map.of("descriptorId", descriptorId), Integer.class);
        assertNotNull(count);
        assertEquals(42, count);
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

        var publicSubscriptions = subscriptionManager.getActivePublicSubscriptionsDescriptor(ZonedDateTime.now(ClockProvider.clock()), SearchOptions.empty());
        assertEquals(0, publicSubscriptions.size());

        subscriptionManager.setPublicStatus(descriptor.getId(), orgId, true);
        publicSubscriptions = subscriptionManager.getActivePublicSubscriptionsDescriptor(ZonedDateTime.now(ClockProvider.clock()), SearchOptions.empty());
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

    @Test
    void linkToEventUsingEventManager() {
        int orgId = event.getOrganizationId();
        assertTrue(subscriptionManager.findAll(orgId).isEmpty());
        var subscriptionId = subscriptionManager.createSubscriptionDescriptor(buildSubscriptionDescriptor(orgId, null, new BigDecimal("100"))).orElseThrow();
        var eventModification = new EventModification(event.getId(), null, null, null,
            null, null, null, null, null, null, orgId, null,
            null, null, null, null, null, null, BigDecimal.TEN, "CHF", 0,
            BigDecimal.ONE, false, List.of(PaymentProxy.OFFLINE), List.of(), false, null, 0, List.of(),
            List.of(), AlfioMetadata.empty(), List.of(subscriptionId));
        eventManager.updateEventPrices(event, eventModification, username);

        var links = subscriptionManager.getLinkedEvents(orgId, subscriptionId);
        assertFalse(links.isEmpty());
        assertEquals(event.getId(), links.get(0).getEventId());
        assertEquals(subscriptionId, links.get(0).getSubscriptionDescriptorId());
        assertEquals(0, links.get(0).getPricePerTicket());


        // subscription list not present, therefore nothing should happen
        eventModification = new EventModification(event.getId(), null, null, null,
            null, null, null, null, null, null, orgId, null,
            null, null, null, null, null, null, BigDecimal.TEN, "CHF", 0,
            BigDecimal.ONE, false, List.of(PaymentProxy.OFFLINE), List.of(), false, null, 0, List.of(),
            List.of(), AlfioMetadata.empty(), null);
        eventManager.updateEventPrices(event, eventModification, username);

        links = subscriptionManager.getLinkedEvents(orgId, subscriptionId);
        assertFalse(links.isEmpty());
        assertEquals(event.getId(), links.get(0).getEventId());
        assertEquals(subscriptionId, links.get(0).getSubscriptionDescriptorId());
        assertEquals(0, links.get(0).getPricePerTicket());

        // subscription list modified, we expect to have an additional link
        var subscriptionId2 = subscriptionManager.createSubscriptionDescriptor(buildSubscriptionDescriptor(orgId, null, new BigDecimal("100"))).orElseThrow();
        eventModification = new EventModification(event.getId(), null, null, null,
            null, null, null, null, null, null, orgId, null,
            null, null, null, null, null, null, BigDecimal.TEN, "CHF", 0,
            BigDecimal.ONE, false, List.of(PaymentProxy.OFFLINE), List.of(), false, null, 0, List.of(),
            List.of(), AlfioMetadata.empty(), List.of(subscriptionId, subscriptionId2));
        eventManager.updateEventPrices(event, eventModification, username);

        var subscriptions = subscriptionRepository.findLinkedSubscriptionIds(event.getId(), event.getOrganizationId());
        assertEquals(2, subscriptions.size());
        assertTrue(subscriptions.contains(subscriptionId));
        assertTrue(subscriptions.contains(subscriptionId2));

        // unlink all subscriptions
        eventModification = new EventModification(event.getId(), null, null, null,
            null, null, null, null, null, null, orgId, null,
            null, null, null, null, null, null, BigDecimal.TEN, "CHF", 0,
            BigDecimal.ONE, false, List.of(PaymentProxy.OFFLINE), List.of(), false, null, 0, List.of(),
            List.of(), AlfioMetadata.empty(), List.of());
        eventManager.updateEventPrices(event, eventModification, username);

        subscriptions = subscriptionRepository.findLinkedSubscriptionIds(event.getId(), event.getOrganizationId());
        assertTrue(subscriptions.isEmpty());
    }

    @Test
    void createAsUnlimitedThenSetLimit() {
        int orgId = event.getOrganizationId();
        assertTrue(subscriptionManager.findAll(orgId).isEmpty());
        var request = buildSubscriptionDescriptor(orgId, null, new BigDecimal("100"), null);

        var optionalDescriptorId = subscriptionManager.createSubscriptionDescriptor(request);
        assertTrue(optionalDescriptorId.isPresent());
        var descriptorId = optionalDescriptorId.get();
        var count = jdbcTemplate.queryForObject("select count(*) from subscription where subscription_descriptor_fk = :descriptorId", Map.of("descriptorId", descriptorId), Integer.class);
        assertNotNull(count);
        assertEquals(0, count);
        var res = subscriptionManager.findAll(orgId);
        assertEquals(1, res.size());
        var descriptor = res.get(0);
        assertEquals(-1, descriptor.getMaxAvailable());

        request = buildSubscriptionDescriptor(orgId, descriptorId, new BigDecimal("100"), 42);
        subscriptionManager.updateSubscriptionDescriptor(request);
        count = jdbcTemplate.queryForObject("select count(*) from subscription where subscription_descriptor_fk = :descriptorId", Map.of("descriptorId", descriptorId), Integer.class);
        assertNotNull(count);
        assertEquals(42, count);

        request = buildSubscriptionDescriptor(orgId, descriptorId, new BigDecimal("100"), 1);
        subscriptionManager.updateSubscriptionDescriptor(request);
        count = jdbcTemplate.queryForObject("select count(*) from subscription where subscription_descriptor_fk = :descriptorId and status = 'FREE'", Map.of("descriptorId", descriptorId), Integer.class);
        assertNotNull(count);
        assertEquals(1, count);
    }

    @Test
    void updatePrice() {
        int orgId = event.getOrganizationId();
        assertTrue(subscriptionManager.findAll(orgId).isEmpty());
        var request = buildSubscriptionDescriptor(orgId, null, new BigDecimal("100"), 10);

        var optionalDescriptorId = subscriptionManager.createSubscriptionDescriptor(request);
        assertTrue(optionalDescriptorId.isPresent());
        var descriptorId = optionalDescriptorId.get();
        var paramsMap = Map.of("descriptorId", descriptorId);
        var count = jdbcTemplate.queryForObject("select count(*) from subscription where subscription_descriptor_fk = :descriptorId and src_price_cts = 10000", paramsMap, Integer.class);
        assertNotNull(count);
        assertEquals(10, count);

        request = buildSubscriptionDescriptor(orgId, descriptorId, new BigDecimal("200"), 10);
        subscriptionManager.updateSubscriptionDescriptor(request);
        count = jdbcTemplate.queryForObject("select count(*) from subscription where subscription_descriptor_fk = :descriptorId and src_price_cts = 20000", paramsMap, Integer.class);
        assertNotNull(count);
        assertEquals(10, count);
    }

    private SubscriptionDescriptorModification buildSubscriptionDescriptor(int orgId, UUID id, BigDecimal price) {
        return buildSubscriptionDescriptor(orgId, id, price, 42);
    }

    private SubscriptionDescriptorModification buildSubscriptionDescriptor(int orgId, UUID id, BigDecimal price, Integer maxAvailable) {

        return new SubscriptionDescriptorModification(id,
            Map.of("en", "title"),
            Map.of("en", "description"),
            maxAvailable,
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
            List.of(PaymentProxy.STRIPE),
            ZoneId.of("Europe/Zurich"),
            false);
    }

}
