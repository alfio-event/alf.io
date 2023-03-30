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
import alfio.controller.api.ControllerConfiguration;
import alfio.controller.api.v1.admin.EventApiV1Controller;
import alfio.manager.EventManager;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.TicketCategory;
import alfio.model.TicketCategoryWithAdditionalInfo;
import alfio.model.api.v1.admin.EventCreationRequest;
import alfio.model.modification.OrganizationModification;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Organization;
import alfio.model.user.Role;
import alfio.model.user.User;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.AlfioIntegrationTest;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.BaseIntegrationTest;
import alfio.util.ClockProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;


@AlfioIntegrationTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, ControllerConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
class EventApiV1IntegrationTest extends BaseIntegrationTest {

    @BeforeAll
    public static void initEnv() {
    }

    @Autowired
    private ConfigurationRepository configurationRepository;

    @Autowired
    private UserManager userManager;

    @Autowired
    private OrganizationRepository organizationRepository;


    @Autowired
    private EventApiV1Controller controller;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;


    private String organizationName;
    private String username;
    private Organization organization;
    private Principal mockPrincipal;

    private final String slug = "test";

    @BeforeEach
    public void ensureConfiguration() {
        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);

        this.organizationName = UUID.randomUUID().toString();
        this.username = UUID.randomUUID().toString();

        var organizationModification = new OrganizationModification(null, organizationName, "email@example.com", "org", null, null);
        userManager.createOrganization(organizationModification, null);
        this.organization = organizationRepository.findByName(organizationName).orElseThrow();
        userManager.insertUser(organization.getId(), username, "test", "test", "test@example.com", Role.API_CONSUMER, User.Type.INTERNAL, null);

        this.mockPrincipal = Mockito.mock(Principal.class);
        Mockito.when(mockPrincipal.getName()).thenReturn(username);

    }


    static EventCreationRequest creationRequest(String shortName) {
        return new EventCreationRequest(
            "Title",
            shortName,
            Collections.singletonList(new EventCreationRequest.DescriptionRequest("en", "desc")),
            null,
            new EventCreationRequest.LocationRequest(
                "Pollegio 6742 Switzerland",
                new EventCreationRequest.CoordinateRequest("45.5","9.00")
            ),
            "Europe/Zurich",
            LocalDateTime.now(ClockProvider.clock()).plusDays(30),
            LocalDateTime.now(ClockProvider.clock()).plusDays(30).plusHours(2),
            "https://alf.io",
            "https://alf.io",
            "https://alf.io",
            "https://alf.io/img/tutorials/check-in-app/003.png",
            new EventCreationRequest.TicketRequest(
                false,
                10,
                "CHF",
                new BigDecimal("7.7"),
                true,
                Arrays.asList(PaymentProxy.OFFLINE,PaymentProxy.STRIPE),
                Collections.singletonList(
                    new EventCreationRequest.CategoryRequest(
                        null, // forces new category
                        "standard",
                        Collections.singletonList(new EventCreationRequest.DescriptionRequest("en", "desc")),
                        10,
                        false,
                        BigDecimal.TEN,
                        LocalDateTime.of(2019, 1, 10, 12, 0),
                        LocalDateTime.of(2019, 1, 30, 18, 0),
                        null,
                        null,
                        null,
                        null
                    )
                ),
                null
            ),
            null,
            null
        );

    }



    @Test
    void createTest() {

        EventCreationRequest eventCreationRequest = creationRequest(slug);

        String slug = controller.create(eventCreationRequest,mockPrincipal).getBody();
        Event event = eventManager.getSingleEvent(slug,username);
        List<TicketCategory> tickets = ticketCategoryRepository.findAllTicketCategories(event.getId());
        assertEquals(eventCreationRequest.getTitle(),event.getDisplayName());
        assertEquals(eventCreationRequest.getSlug(),event.getShortName());
        assertEquals(eventCreationRequest.getTickets().getCurrency(),event.getCurrency());
        assertEquals(eventCreationRequest.getWebsiteUrl(),event.getWebsiteUrl());
        assertEquals(eventCreationRequest.getTickets().getPaymentMethods(),event.getAllowedPaymentProxies());
        assertTrue(event.getFileBlobIdIsPresent());
        assertEquals(eventCreationRequest.getTickets().getCategories().size(),tickets.size());
        tickets.forEach((t) -> {
                List<EventCreationRequest.CategoryRequest> requestCategories = eventCreationRequest.getTickets().getCategories().stream().filter((rt) -> rt.getName().equals(t.getName())).toList();
                assertEquals(1,requestCategories.size());
                requestCategories.forEach((rtc) -> {
                        assertNotEquals(0, t.getOrdinal());
                        assertEquals(t.getMaxTickets(), rtc.getMaxTickets().intValue());
                        assertEquals(0, t.getPrice().compareTo(rtc.getPrice()));
                    }
                );
            }
        );
    }

    @Test
    void stats() {
        controller.create(creationRequest(slug), mockPrincipal);
        var statsResponse = controller.stats(slug, mockPrincipal);
        assertTrue(statsResponse.getStatusCode().is2xxSuccessful());
        var stats = requireNonNull(statsResponse.getBody());
        int lastOrdinal = -1;
        for (TicketCategoryWithAdditionalInfo ticketCategory : stats.getTicketCategories()) {
            assertTrue(ticketCategory.getOrdinal() > 0);
            assertTrue(ticketCategory.getOrdinal() > lastOrdinal);
            lastOrdinal = ticketCategory.getOrdinal();
        }
    }

    @Test
    void updateTest() {
        controller.create(creationRequest(slug), mockPrincipal);
        String newTitle = "new title";
        EventCreationRequest updateRequest = new EventCreationRequest(newTitle,null,null,null, null,null,null,null,null,null, null,null,
            new EventCreationRequest.TicketRequest(null,10,null,null,null,null,null,null), null, null
        );
        controller.update(slug, updateRequest, mockPrincipal);
        Event event = eventManager.getSingleEvent(slug,username);
        assertEquals(newTitle,event.getDisplayName());
    }

    @Test
    void updateExistingCategoryUsingId() {
        controller.create(creationRequest(slug), mockPrincipal);
        var existing = requireNonNull(controller.stats(slug, mockPrincipal).getBody());
        var existingCategory = existing.getTicketCategories().get(0);
        var categoriesRequest = List.of(
            new EventCreationRequest.CategoryRequest(
                existingCategory.getId(),
                existingCategory.getName() + "_1",
                List.of(new EventCreationRequest.DescriptionRequest("en", "desc")),
                existingCategory.getMaxTickets(),
                existingCategory.isAccessRestricted(),
                existingCategory.getPrice(),
                LocalDateTime.now(ClockProvider.clock()),
                LocalDateTime.now(ClockProvider.clock()).plusHours(1),
                existingCategory.getCode(),
                null,
                null,
                existingCategory.getTicketAccessType()
            )
        );
        var ticketRequest = new EventCreationRequest.TicketRequest(null,10,null,null,null,null, categoriesRequest,null);
        EventCreationRequest updateRequest = new EventCreationRequest(null,null,null,null, null,null,null,null,null,null, null,null,
            ticketRequest, null, null
        );
        assertTrue(controller.update(slug, updateRequest, mockPrincipal).getStatusCode().is2xxSuccessful());
        var modifiedCategories = ticketCategoryRepository.findAllTicketCategories(existing.getId());
        assertEquals(1, modifiedCategories.size());
        assertEquals(existingCategory.getName() + "_1", modifiedCategories.get(0).getName());
    }

    @Test
    void updateExistingCategoryAndAddNewUsingId() {
        controller.create(creationRequest(slug), mockPrincipal);
        var existing = requireNonNull(controller.stats(slug, mockPrincipal).getBody());
        var existingCategory = existing.getTicketCategories().get(0);
        var categoriesRequest = List.of(
            new EventCreationRequest.CategoryRequest(
                existingCategory.getId(),
                existingCategory.getName() + "_1",
                List.of(new EventCreationRequest.DescriptionRequest("en", "desc")),
                existingCategory.getMaxTickets() - 5,
                existingCategory.isAccessRestricted(),
                existingCategory.getPrice(),
                LocalDateTime.now(ClockProvider.clock()),
                LocalDateTime.now(ClockProvider.clock()).plusHours(1),
                existingCategory.getCode(),
                null,
                null,
                existingCategory.getTicketAccessType()
            ),
            new EventCreationRequest.CategoryRequest(
                null,
                existingCategory.getName() + "_2",
                List.of(new EventCreationRequest.DescriptionRequest("en", "desc")),
                existingCategory.getMaxTickets() - 5,
                existingCategory.isAccessRestricted(),
                existingCategory.getPrice(),
                LocalDateTime.now(ClockProvider.clock()),
                LocalDateTime.now(ClockProvider.clock()).plusHours(1),
                existingCategory.getCode(),
                null,
                null,
                existingCategory.getTicketAccessType()
            )
        );
        var ticketRequest = new EventCreationRequest.TicketRequest(null,10,null,null,null,null, categoriesRequest,null);
        EventCreationRequest updateRequest = new EventCreationRequest(null,null,null,null, null,null,null,null,null,null, null,null,
            ticketRequest, null, null
        );
        assertTrue(controller.update(slug, updateRequest, mockPrincipal).getStatusCode().is2xxSuccessful());
        var modifiedCategories = ticketCategoryRepository.findAllTicketCategories(existing.getId());
        assertEquals(2, modifiedCategories.size());
        assertEquals(existingCategory.getName() + "_1", modifiedCategories.get(0).getName());
        assertEquals(existingCategory.getOrdinal(), modifiedCategories.get(0).getOrdinal());
        assertEquals(existingCategory.getName() + "_2", modifiedCategories.get(1).getName());
        assertEquals(existingCategory.getOrdinal() + 1, modifiedCategories.get(1).getOrdinal());
    }

    @Test
    void updateExistingCategoryUsingName() {
        controller.create(creationRequest(slug), mockPrincipal);
        var existing = requireNonNull(controller.stats(slug, mockPrincipal).getBody());
        var existingCategory = existing.getTicketCategories().get(0);
        var categoriesRequest = List.of(
            new EventCreationRequest.CategoryRequest(null,
                existingCategory.getName(),
                List.of(new EventCreationRequest.DescriptionRequest("en", "desc")),
                existingCategory.getMaxTickets() - 1,
                existingCategory.isAccessRestricted(),
                existingCategory.getPrice(),
                LocalDateTime.now(ClockProvider.clock()),
                LocalDateTime.now(ClockProvider.clock()).plusHours(1),
                existingCategory.getCode(),
                null,
                null,
                existingCategory.getTicketAccessType()
            )
        );
        var ticketRequest = new EventCreationRequest.TicketRequest(null,10,null,null,null,null, categoriesRequest,null);
        EventCreationRequest updateRequest = new EventCreationRequest(null,null,null,null, null,null,null,null,null,null, null,null,
            ticketRequest, null, null
        );
        assertTrue(controller.update(slug, updateRequest, mockPrincipal).getStatusCode().is2xxSuccessful());
        var modifiedCategories = ticketCategoryRepository.findAllTicketCategories(existing.getId());
        assertEquals(1, modifiedCategories.size());
        assertEquals(existingCategory.getMaxTickets() - 1, modifiedCategories.get(0).getMaxTickets());
    }

    @Test
    void retrieveLinkedSubscriptions() {
        controller.create(creationRequest(slug),mockPrincipal);
        var response = controller.getLinkedSubscriptions(slug, mockPrincipal);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        var linkedSubscriptions = response.getBody();
        assertNotNull(linkedSubscriptions);
        assertTrue(linkedSubscriptions.getSubscriptions().isEmpty());
        assertEquals(slug, linkedSubscriptions.getEventSlug());
    }


}
