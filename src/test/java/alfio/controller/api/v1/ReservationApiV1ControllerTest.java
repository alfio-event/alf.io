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
package alfio.controller.api.v1;

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.config.authentication.support.APITokenAuthentication;
import alfio.controller.api.ControllerConfiguration;
import alfio.controller.api.v1.admin.ReservationApiV1Controller;
import alfio.controller.api.v1.admin.SubscriptionApiV1Controller;
import alfio.manager.EventManager;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.TicketCategory;
import alfio.model.api.v1.admin.*;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.metadata.TicketMetadataContainer;
import alfio.model.modification.AttendeeData;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.model.system.ConfigurationKeys;
import alfio.model.user.Role;
import alfio.model.user.User;
import alfio.repository.*;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.AlfioIntegrationTest;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.ClockProvider;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
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
import java.util.*;

import static alfio.controller.api.v1.SubscriptionApiV1IntegrationTest.modificationRequest;
import static alfio.model.system.ConfigurationKeys.OPENID_PUBLIC_ENABLED;
import static alfio.test.util.IntegrationTestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

@AlfioIntegrationTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, ControllerConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
class ReservationApiV1ControllerTest {

    private static final String DEFAULT_CATEGORY_NAME = "default";
    private static final String ANONYMOUS_RESERVATION_URL_PREFIX = BASE_URL + "/event";
    private static final String LOGGED_IN_RESERVATION_URL_PREFIX = BASE_URL + "/openid/authentication";
    @Autowired
    private ConfigurationRepository configurationRepository;
    @Autowired
    private ClockProvider clockProvider;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserManager userManager;
    @Autowired
    private EventManager eventManager;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private ReservationApiV1Controller controller;
    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private TicketReservationRepository ticketReservationRepository;
    @Autowired
    private SubscriptionApiV1Controller subscriptionApiV1Controller;
    @Autowired
    private SubscriptionRepository subscriptionRepository;

    private Event event;
    private String username;

    @BeforeEach
    void setUp() {
        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);
        List<TicketCategoryModification> categories = Arrays.asList(
            new TicketCategoryModification(null, DEFAULT_CATEGORY_NAME, TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()).minusDays(1), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()).plusDays(1), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()),
            new TicketCategoryModification(null, "hidden", TicketCategory.TicketAccessType.INHERIT, 2,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()).minusDays(1), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()).plusDays(1), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.ONE, true, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty())
        );
        Pair<Event, String> eventAndUser = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        event = eventAndUser.getLeft();
        username = UUID.randomUUID().toString();
        userManager.insertUser(event.getOrganizationId(), username, "test", "test", "test@example.com", Role.API_CONSUMER, User.Type.INTERNAL);
    }

    @Test
    void createSingleTicketWithMetadata() {
        var category = ticketCategoryRepository.findFirstWithAvailableTickets(event.getId()).orElseThrow();
        var firstTicketProperties = Map.of("property", "value-first");
        var ticket = new AttendeesByCategory(category.getId(), 1, List.of(), List.of(firstTicketProperties));
        var creationRequest = new TicketReservationCreationRequest(
            List.of(ticket),
            List.of(),
            new ReservationConfiguration(true),
            null,
            null,
            "en",
            null
        );
        var principal = new APITokenAuthentication(username, null, List.of());
        var response = controller.createTicketsReservation(event.getShortName(), creationRequest, principal);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        var body = response.getBody();
        assertNotNull(body);
        assertNull(body.getErrors());
        assertTrue(body.isSuccess());
        var reservationId = body.getId();
        assertNotNull(reservationId);
        assertFalse(reservationId.isBlank());
        var href = body.getHref();
        assertTrue(StringUtils.startsWith(href, ANONYMOUS_RESERVATION_URL_PREFIX));

        var tickets = ticketRepository.findTicketsInReservation(reservationId);
        assertEquals(1, tickets.size());
        var metadata = ticketRepository.getTicketMetadata(tickets.get(0).getId());
        assertNotNull(metadata);
        var attributes = metadata.getMetadataForKey(TicketMetadataContainer.GENERAL);
        assertTrue(attributes.isPresent());
        assertEquals(firstTicketProperties, attributes.get().getAttributes());
        var reservationMetadata = ticketReservationRepository.getMetadata(reservationId);
        assertNotNull(reservationMetadata);
        assertTrue(reservationMetadata.isHideContactData());
    }

    @Test
    void createTwoTicketsWithMetadata() {
        var category = ticketCategoryRepository.findFirstWithAvailableTickets(event.getId()).orElseThrow();
        var firstTicketProperties = Map.of("property", "value-first");
        var ticket = new AttendeesByCategory(category.getId(), 2, null, List.of(firstTicketProperties));
        var creationRequest = new TicketReservationCreationRequest(
            List.of(ticket),
            List.of(),
            null,
            null,
            null,
            "en",
            null
        );
        var principal = new APITokenAuthentication(username, null, List.of());
        var response = controller.createTicketsReservation(event.getShortName(), creationRequest, principal);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        var body = response.getBody();
        assertNotNull(body);
        assertNull(body.getErrors());
        assertTrue(body.isSuccess());
        var reservationId = body.getId();
        assertNotNull(reservationId);
        assertFalse(reservationId.isBlank());
        var href = body.getHref();
        assertTrue(StringUtils.startsWith(href, ANONYMOUS_RESERVATION_URL_PREFIX));

        var tickets = ticketRepository.findTicketsInReservation(reservationId);
        assertEquals(2, tickets.size());
        assertEquals(1L, tickets.stream().filter(t -> {
            var metadata = ticketRepository.getTicketMetadata(t.getId());
            if (metadata == null) {
                return false;
            }
            var attributes = metadata.getMetadataForKey(TicketMetadataContainer.GENERAL);
            return attributes.isPresent() && attributes.get().getAttributes().equals(firstTicketProperties);
        }).count());
        var reservationMetadata = ticketReservationRepository.getMetadata(reservationId);
        assertNotNull(reservationMetadata);
        assertFalse(reservationMetadata.isHideContactData());
    }

    @Test
    void createSingleTicketWithAuthenticatedUser() {
        configurationRepository.insert(OPENID_PUBLIC_ENABLED.name(), "true", "");
        var category = ticketCategoryRepository.findFirstWithAvailableTickets(event.getId()).orElseThrow();
        var firstTicketProperties = Map.of("property", "value-first");
        var ticket = new AttendeesByCategory(category.getId(), 1, List.of(), List.of(firstTicketProperties));
        var user = new ReservationUser(
            "test@example.org",
            "Test",
            "McTest",
            "test@example.org",
            "EXTERNALID"
        );
        var creationRequest = new TicketReservationCreationRequest(
            List.of(ticket),
            List.of(),
            null,
            user,
            null,
            "en",
            null
        );
        var principal = new APITokenAuthentication(username, null, List.of());
        var response = controller.createTicketsReservation(event.getShortName(), creationRequest, principal);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        var body = response.getBody();
        assertNotNull(body);
        assertNull(body.getErrors());
        assertTrue(body.isSuccess());
        var reservationId = body.getId();
        assertNotNull(reservationId);
        assertFalse(reservationId.isBlank());
        var href = body.getHref();
        assertTrue(StringUtils.startsWith(href, LOGGED_IN_RESERVATION_URL_PREFIX));

        var tickets = ticketRepository.findTicketsInReservation(reservationId);
        assertEquals(1, tickets.size());
        var metadata = ticketRepository.getTicketMetadata(tickets.get(0).getId());
        assertNotNull(metadata);
        var attributes = metadata.getMetadataForKey(TicketMetadataContainer.GENERAL);
        assertTrue(attributes.isPresent());
        assertEquals(firstTicketProperties, attributes.get().getAttributes());

        var createdUser = userManager.findOptionalEnabledUserByUsername("test@example.org");
        assertTrue(createdUser.isPresent());
        var reservations = ticketReservationRepository.findAllReservationsForUser(createdUser.get().getId());
        assertFalse(reservations.isEmpty());
        assertEquals(1, reservations.size());
        assertEquals(reservationId, reservations.get(0).getId());
    }

    @Test
    void createSingleTicketWithAttendees() {
        var category = ticketCategoryRepository.findFirstWithAvailableTickets(event.getId()).orElseThrow();
        var firstTicketProperties = Map.of("property", "value-first");
        var ticket = new AttendeesByCategory(category.getId(), 1, List.of(
            new AttendeeData("firstName", "lastName", "example@example.org", firstTicketProperties)
        ), null);
        var user = new ReservationUser(
            "test@example.org",
            "Test",
            "McTest",
            "test@example.org",
            "EXTERNALID"
        );
        var creationRequest = new TicketReservationCreationRequest(
            List.of(ticket),
            List.of(),
            null,
            user,
            null,
            "en",
            null
        );
        var principal = new APITokenAuthentication(username, null, List.of());
        var response = controller.createTicketsReservation(event.getShortName(), creationRequest, principal);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        var body = response.getBody();
        assertNotNull(body);
        assertNull(body.getErrors());
        assertTrue(body.isSuccess());
        var reservationId = body.getId();
        assertNotNull(reservationId);
        assertFalse(reservationId.isBlank());
        var href = body.getHref();
        assertFalse(StringUtils.startsWith(href, LOGGED_IN_RESERVATION_URL_PREFIX));

        var tickets = ticketRepository.findTicketsInReservation(reservationId);
        var savedTicket = tickets.get(0);
        assertEquals(1, tickets.size());
        assertEquals("firstName", savedTicket.getFirstName());
        assertEquals("lastName", savedTicket.getLastName());
        assertEquals("example@example.org", savedTicket.getEmail());
        var metadata = ticketRepository.getTicketMetadata(savedTicket.getId());
        assertNotNull(metadata);
        var attributes = metadata.getMetadataForKey(TicketMetadataContainer.GENERAL);
        assertTrue(attributes.isPresent());
        assertEquals(firstTicketProperties, attributes.get().getAttributes());

        var createdUser = userManager.findOptionalEnabledUserByUsername("test@example.org");
        assertFalse(createdUser.isPresent());
    }

    @Test
    void createMultipleTicketsWithAttendees() {
        var category = ticketCategoryRepository.findFirstWithAvailableTickets(event.getId()).orElseThrow();
        var firstTicketProperties = Map.of("property", "value-first");
        var ticket = new AttendeesByCategory(category.getId(), 2, List.of(
            new AttendeeData("firstName", "lastName", "example@example.org", firstTicketProperties),
            new AttendeeData("firstName", "lastName", "example@example.org", firstTicketProperties)
        ), null);
        var user = new ReservationUser(
            "test@example.org",
            "Test",
            "McTest",
            "test@example.org",
            "EXTERNALID"
        );
        var creationRequest = new TicketReservationCreationRequest(
            List.of(ticket),
            List.of(),
            null,
            user,
            null,
            "en",
            null
        );
        var principal = new APITokenAuthentication(username, null, List.of());
        var response = controller.createTicketsReservation(event.getShortName(), creationRequest, principal);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        var body = response.getBody();
        assertNotNull(body);
        assertNull(body.getErrors());
        assertTrue(body.isSuccess());
        var reservationId = body.getId();
        assertNotNull(reservationId);
        assertFalse(reservationId.isBlank());
        var href = body.getHref();
        assertFalse(StringUtils.startsWith(href, LOGGED_IN_RESERVATION_URL_PREFIX));

        var tickets = ticketRepository.findTicketsInReservation(reservationId);
        assertEquals(2, tickets.size());
        tickets.forEach(savedTicket -> {
            assertEquals("firstName", savedTicket.getFirstName());
            assertEquals("lastName", savedTicket.getLastName());
            assertEquals("example@example.org", savedTicket.getEmail());
            var metadata = ticketRepository.getTicketMetadata(savedTicket.getId());
            assertNotNull(metadata);
            var attributes = metadata.getMetadataForKey(TicketMetadataContainer.GENERAL);
            assertTrue(attributes.isPresent());
            assertEquals(firstTicketProperties, attributes.get().getAttributes());
        });
        var createdUser = userManager.findOptionalEnabledUserByUsername("test@example.org");
        assertFalse(createdUser.isPresent());
    }

    @Test
    void createSingleTicketWithSubscriptionId() {
        var subscriptionId = UUID.randomUUID().toString();
        var category = ticketCategoryRepository.findFirstWithAvailableTickets(event.getId()).orElseThrow();
        var firstTicketProperties = Map.of("property", "value-first");
        var ticket = new AttendeesByCategory(category.getId(), 1, List.of(
            new AttendeeData("firstName", "lastName", "example@example.org", firstTicketProperties)
        ), null);
        var user = new ReservationUser(
            "test@example.org",
            "Test",
            "McTest",
            "test@example.org",
            "EXTERNALID"
        );
        var creationRequest = new TicketReservationCreationRequest(
            List.of(ticket),
            List.of(),
            null,
            user,
            null,
            "en",
            subscriptionId
        );
        var principal = new APITokenAuthentication(username, null, List.of());
        var response = controller.createTicketsReservation(event.getShortName(), creationRequest, principal);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        var body = response.getBody();
        assertNotNull(body);
        assertNull(body.getErrors());
        assertTrue(body.isSuccess());
        var reservationId = body.getId();
        assertNotNull(reservationId);
        assertFalse(reservationId.isBlank());
        var href = body.getHref();
        assertFalse(StringUtils.startsWith(href, LOGGED_IN_RESERVATION_URL_PREFIX));
        assertTrue(StringUtils.endsWith(href, "subscription="+subscriptionId));
        var tickets = ticketRepository.findTicketsInReservation(reservationId);
        assertEquals(1, tickets.size());
        tickets.forEach(savedTicket -> {
            assertEquals("firstName", savedTicket.getFirstName());
            assertEquals("lastName", savedTicket.getLastName());
            assertEquals("example@example.org", savedTicket.getEmail());
            var metadata = ticketRepository.getTicketMetadata(savedTicket.getId());
            assertNotNull(metadata);
            var attributes = metadata.getMetadataForKey(TicketMetadataContainer.GENERAL);
            assertTrue(attributes.isPresent());
            assertEquals(firstTicketProperties, attributes.get().getAttributes());
        });
        var createdUser = userManager.findOptionalEnabledUserByUsername("test@example.org");
        assertFalse(createdUser.isPresent());
    }

    @Test
    void createSubscriptionWithMetadata() {
        configurationRepository.insert(ConfigurationKeys.STRIPE_PUBLIC_KEY.getValue(), "pk", "");
        configurationRepository.insert(ConfigurationKeys.STRIPE_SECRET_KEY.getValue(), "sk", "");
        var principal = new APITokenAuthentication(username, null, List.of());
        var creationResponse = subscriptionApiV1Controller.create(modificationRequest(SubscriptionDescriptor.SubscriptionUsageType.ONCE_PER_EVENT, true, clockProvider), principal);
        assertTrue(creationResponse.getStatusCode().is2xxSuccessful());
        assertNotNull(creationResponse.getBody());
        var descriptorId = creationResponse.getBody();
        var reservationRequest = new SubscriptionReservationCreationRequest(Map.of("key", "value"),
            new ReservationUser("test@test.org", "Test", "Test1", "test@test.org", null),
            "en",
            new ReservationConfiguration(true),
            null);
        var reservationResponse = controller.createSubscriptionReservation(descriptorId, reservationRequest, principal);
        assertTrue(reservationResponse.getStatusCode().is2xxSuccessful());
        assertNotNull(reservationResponse.getBody());
        var body = Objects.requireNonNull(reservationResponse.getBody());
        assertTrue(body.isSuccess());
        var reservationId = body.getId();
        var reservation = ticketReservationRepository.findReservationById(reservationId);
        assertEquals("Test", reservation.getFirstName());
        assertEquals("Test1", reservation.getLastName());
        assertEquals("test@test.org", reservation.getEmail());
        var subscriptions = subscriptionRepository.findSubscriptionsByReservationId(reservationId);
        assertEquals(1, subscriptions.size());
        var subscriptionId = subscriptions.get(0).getId();
        var subscriptionMetadata = subscriptionRepository.getSubscriptionMetadata(subscriptionId);
        assertNotNull(subscriptionMetadata);
        assertNotNull(subscriptionMetadata.getProperties());
        assertFalse(subscriptionMetadata.getProperties().isEmpty());
        assertEquals("value", subscriptionMetadata.getProperties().get("key"));
    }
}
