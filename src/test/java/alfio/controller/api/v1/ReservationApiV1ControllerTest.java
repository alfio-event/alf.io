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
import alfio.manager.EventManager;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.TicketCategory;
import alfio.model.api.v1.admin.ReservationCreationRequest;
import alfio.model.api.v1.admin.ReservationUser;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.metadata.TicketMetadataContainer;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.modification.TicketReservationModification;
import alfio.model.user.Role;
import alfio.model.user.User;
import alfio.repository.EventRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.TicketRepository;
import alfio.repository.TicketReservationRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static alfio.model.system.ConfigurationKeys.OPENID_PUBLIC_ENABLED;
import static alfio.test.util.IntegrationTestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, ControllerConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
@Transactional
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
        var ticket = new TicketReservationModification();
        ticket.setQuantity(1);
        ticket.setTicketCategoryId(category.getId());
        var firstTicketProperties = Map.of("property", "value-first");
        ticket.setMetadata(List.of(firstTicketProperties));
        var creationRequest = new ReservationCreationRequest(
            List.of(ticket),
            List.of(),
            null,
            null,
            "en"
        );
        var principal = new APITokenAuthentication(username, null, List.of());
        var response = controller.createReservation(event.getShortName(), creationRequest, principal);
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
    }

    @Test
    void createTwoTicketsWithMetadata() {
        var category = ticketCategoryRepository.findFirstWithAvailableTickets(event.getId()).orElseThrow();
        var ticket = new TicketReservationModification();
        ticket.setQuantity(2);
        ticket.setTicketCategoryId(category.getId());
        // metadata will be applied only to the first ticket
        var firstTicketProperties = Map.of("property", "value-first");
        ticket.setMetadata(List.of(firstTicketProperties));
        var creationRequest = new ReservationCreationRequest(
            List.of(ticket),
            List.of(),
            null,
            null,
            "en"
        );
        var principal = new APITokenAuthentication(username, null, List.of());
        var response = controller.createReservation(event.getShortName(), creationRequest, principal);
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
    }

    @Test
    void createSingleTicketWithAuthenticatedUser() {
        configurationRepository.insert(OPENID_PUBLIC_ENABLED.name(), "true", "");
        var category = ticketCategoryRepository.findFirstWithAvailableTickets(event.getId()).orElseThrow();
        var ticket = new TicketReservationModification();
        ticket.setQuantity(1);
        ticket.setTicketCategoryId(category.getId());
        var firstTicketProperties = Map.of("property", "value-first");
        ticket.setMetadata(List.of(firstTicketProperties));
        var user = new ReservationUser(
            "test@example.org",
            "Test",
            "McTest",
            "test@example.org",
            "EXTERNALID"
        );
        var creationRequest = new ReservationCreationRequest(
            List.of(ticket),
            List.of(),
            user,
            null,
            "en"
        );
        var principal = new APITokenAuthentication(username, null, List.of());
        var response = controller.createReservation(event.getShortName(), creationRequest, principal);
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
}
