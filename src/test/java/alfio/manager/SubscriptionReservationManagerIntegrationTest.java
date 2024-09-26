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
import alfio.model.AllocationStatus;
import alfio.model.Event;
import alfio.model.TicketCategory;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.modification.UploadBase64FileModification;
import alfio.repository.EventRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.test.util.AlfioIntegrationTest;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.BaseIntegrationTest;
import alfio.util.ClockProvider;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static alfio.manager.SubscriptionManagerIntegrationTest.buildSubscriptionDescriptor;
import static alfio.test.util.IntegrationTestUtil.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@AlfioIntegrationTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
class SubscriptionReservationManagerIntegrationTest {

    private static final String COUNT_ITEMS = "select count(*) from subscription where status = :status::allocation_status and subscription_descriptor_fk = :descriptorId";
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ConfigurationRepository configurationRepository;
    @Autowired
    private AuthorityRepository authorityRepository;
    @Autowired
    private FileUploadManager fileUploadManager;
    @Autowired
    private SubscriptionManager subscriptionManager;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserManager userManager;
    @Autowired
    private EventManager eventManager;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private TicketReservationManager reservationManager;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;
    @Autowired
    private ClockProvider clockProvider;

    String fileBlobId;
    int organizationId;
    String username;

    @BeforeEach
    void setUp() {
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

        organizationId = eventAndUser.getLeft().getOrganizationId();
        username = eventAndUser.getRight();
    }

    @Test
    void cancelReservationWithUnlimitedSubscription() {
        var optionalDescriptorId = subscriptionManager.createSubscriptionDescriptor(buildSubscriptionDescriptor(organizationId, null, new BigDecimal("0"), null, fileBlobId));
        assertTrue(optionalDescriptorId.isPresent());
        var subscriptionDescriptor = subscriptionManager.getSubscriptionById(optionalDescriptorId.get()).orElseThrow();
        var optionalReservation = reservationManager.createSubscriptionReservation(subscriptionDescriptor, Locale.ENGLISH, null);
        assertTrue(optionalReservation.isPresent());
        var reservationId = optionalReservation.get();
        assertEquals(1, jdbcTemplate.queryForObject(COUNT_ITEMS, new MapSqlParameterSource("descriptorId", subscriptionDescriptor.getId()).addValue("status", AllocationStatus.PENDING.name()), Integer.class));
        reservationManager.cancelPendingReservation(reservationId, false, username);
        assertEquals(0, jdbcTemplate.queryForObject(COUNT_ITEMS, new MapSqlParameterSource("descriptorId", subscriptionDescriptor.getId()).addValue("status", AllocationStatus.PENDING.name()), Integer.class));
    }

    @Test
    void cancelReservationWithLimitedSubscription() {
        var optionalDescriptorId = subscriptionManager.createSubscriptionDescriptor(buildSubscriptionDescriptor(organizationId, null, new BigDecimal("0"), 2, fileBlobId));
        assertTrue(optionalDescriptorId.isPresent());
        var subscriptionDescriptor = subscriptionManager.getSubscriptionById(optionalDescriptorId.get()).orElseThrow();
        var optionalReservation = reservationManager.createSubscriptionReservation(subscriptionDescriptor, Locale.ENGLISH, null);
        assertTrue(optionalReservation.isPresent());
        var reservationId = optionalReservation.get();
        assertEquals(1, jdbcTemplate.queryForObject(COUNT_ITEMS, new MapSqlParameterSource("descriptorId", subscriptionDescriptor.getId()).addValue("status", AllocationStatus.FREE.name()), Integer.class));
        reservationManager.cancelPendingReservation(reservationId, false, username);
        assertEquals(2, jdbcTemplate.queryForObject(COUNT_ITEMS, new MapSqlParameterSource("descriptorId", subscriptionDescriptor.getId()).addValue("status", AllocationStatus.FREE.name()), Integer.class));
    }

    @Test
    void expireReservationWithUnlimitedSubscription() {
        var optionalDescriptorId = subscriptionManager.createSubscriptionDescriptor(buildSubscriptionDescriptor(organizationId, null, new BigDecimal("0"), null, fileBlobId));
        assertTrue(optionalDescriptorId.isPresent());
        var subscriptionDescriptor = subscriptionManager.getSubscriptionById(optionalDescriptorId.get()).orElseThrow();
        var optionalReservation = reservationManager.createSubscriptionReservation(subscriptionDescriptor, Locale.ENGLISH, null);
        assertTrue(optionalReservation.isPresent());
        assertEquals(1, jdbcTemplate.queryForObject(COUNT_ITEMS, new MapSqlParameterSource("descriptorId", subscriptionDescriptor.getId()).addValue("status", AllocationStatus.PENDING.name()), Integer.class));
        reservationManager.cleanupExpiredReservations(Date.from(Instant.now(clockProvider.getClock()).plus(1L, ChronoUnit.DAYS)));
        assertEquals(0, jdbcTemplate.queryForObject(COUNT_ITEMS, new MapSqlParameterSource("descriptorId", subscriptionDescriptor.getId()).addValue("status", AllocationStatus.PENDING.name()), Integer.class));
    }

    @Test
    void expireReservationWithLimitedSubscription() {
        var optionalDescriptorId = subscriptionManager.createSubscriptionDescriptor(buildSubscriptionDescriptor(organizationId, null, new BigDecimal("0"), 2, fileBlobId));
        assertTrue(optionalDescriptorId.isPresent());
        var subscriptionDescriptor = subscriptionManager.getSubscriptionById(optionalDescriptorId.get()).orElseThrow();
        var optionalReservation = reservationManager.createSubscriptionReservation(subscriptionDescriptor, Locale.ENGLISH, null);
        assertTrue(optionalReservation.isPresent());
        assertEquals(1, jdbcTemplate.queryForObject(COUNT_ITEMS, new MapSqlParameterSource("descriptorId", subscriptionDescriptor.getId()).addValue("status", AllocationStatus.FREE.name()), Integer.class));
        reservationManager.cleanupExpiredReservations(Date.from(Instant.now(clockProvider.getClock()).plus(1L, ChronoUnit.DAYS)));
        assertEquals(2, jdbcTemplate.queryForObject(COUNT_ITEMS, new MapSqlParameterSource("descriptorId", subscriptionDescriptor.getId()).addValue("status", AllocationStatus.FREE.name()), Integer.class));
    }
}