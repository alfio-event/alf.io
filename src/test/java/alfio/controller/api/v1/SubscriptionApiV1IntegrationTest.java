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
import alfio.controller.api.v1.admin.SubscriptionApiV1Controller;
import alfio.manager.user.UserManager;
import alfio.model.PriceContainer;
import alfio.model.TicketCategory;
import alfio.model.api.v1.admin.EventCreationRequest;
import alfio.model.api.v1.admin.SubscriptionDescriptorModificationRequest;
import alfio.model.api.v1.admin.subscription.StandardPeriodTerm;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.OrganizationModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.model.subscription.SubscriptionDescriptor.SubscriptionUsageType;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Role;
import alfio.model.user.User;
import alfio.repository.SubscriptionRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.AlfioIntegrationTest;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.ClockProvider;
import org.junit.jupiter.api.Assertions;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static alfio.model.subscription.SubscriptionDescriptor.SubscriptionUsageType.ONCE_PER_EVENT;
import static alfio.model.subscription.SubscriptionDescriptor.SubscriptionUsageType.UNLIMITED;
import static alfio.test.util.IntegrationTestUtil.AVAILABLE_SEATS;
import static alfio.test.util.IntegrationTestUtil.DESCRIPTION;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;

@AlfioIntegrationTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, ControllerConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
class SubscriptionApiV1IntegrationTest {

    @Autowired
    UserManager userManager;
    @Autowired
    OrganizationRepository organizationRepository;
    @Autowired
    ConfigurationRepository configurationRepository;
    @Autowired
    SubscriptionApiV1Controller controller;
    @Autowired
    EventApiV1Controller eventController;
    @Autowired
    ClockProvider clockProvider;
    @Autowired
    SubscriptionRepository subscriptionRepository;

    SubscriptionDescriptor subscriptionDescriptor;

    String username;
    Principal principal;

    @BeforeEach
    public void ensureConfiguration() {
        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);

        var organizationName = UUID.randomUUID().toString();
        this.username = UUID.randomUUID().toString();

        var organizationModification = new OrganizationModification(null, organizationName, "email@example.com", "org", null, null);
        userManager.createOrganization(organizationModification, null);
        var organization = organizationRepository.findByName(organizationName).orElseThrow();
        userManager.insertUser(organization.getId(), username, "test", "test", "test@example.com", Role.API_CONSUMER, User.Type.INTERNAL, null);

        this.principal = Mockito.mock(Principal.class);
        Mockito.when(principal.getName()).thenReturn(username);
        var creationRequest = creationRequest();
        var result = controller.create(creationRequest, principal);
        assertTrue(result.getStatusCode().is2xxSuccessful());
        assertNotNull(result.getBody());
        var descriptorOptional = subscriptionRepository.findOne(UUID.fromString(result.getBody()));
        assertTrue(descriptorOptional.isPresent());
        this.subscriptionDescriptor = descriptorOptional.get();
    }

    @Test
    void update() {
        assertEquals(ONCE_PER_EVENT, subscriptionDescriptor.getUsageType());
        assertTrue(subscriptionDescriptor.isPublic());

        var updateRequest = modificationRequest(UNLIMITED, false);
        var result = controller.update(subscriptionDescriptor.getId(), updateRequest, principal);
        assertTrue(result.getStatusCode().is2xxSuccessful());
        assertNotNull(result.getBody());
        var uuid = UUID.fromString(result.getBody());
        assertEquals(subscriptionDescriptor.getId(), uuid);
        var updated = subscriptionRepository.findOne(uuid).orElseThrow();
        assertEquals(UNLIMITED, updated.getUsageType());
        assertFalse(updated.isPublic());
    }

    @Test
    void deactivate() {
        var result = controller.deactivate(subscriptionDescriptor.getId(), principal);
        assertTrue(result.getStatusCode().is2xxSuccessful());
        assertTrue(subscriptionRepository.findOne(subscriptionDescriptor.getId()).isEmpty());
    }

    @Test
    void updateLinkedEvents() {
        var eventCreateResponse = eventController.create(EventApiV1IntegrationTest.creationRequest("short-name"), principal);
        assertNotNull(eventCreateResponse.getBody());
        var eventSlug = eventCreateResponse.getBody();
        var descriptorId = subscriptionDescriptor.getId();
        var response = controller.getLinkedEvents(descriptorId, principal);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(requireNonNull(response.getBody()).isEmpty());
        controller.updateLinkedEvents(descriptorId, List.of(eventSlug), principal);
        response = controller.getLinkedEvents(descriptorId, principal);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals(List.of(eventSlug), response.getBody());
    }



    private SubscriptionDescriptorModificationRequest modificationRequest(SubscriptionUsageType usageType,
                                                                          boolean isPublic) {
        return modificationRequest(usageType, isPublic, clockProvider);
    }

    static SubscriptionDescriptorModificationRequest modificationRequest(SubscriptionUsageType usageType,
                                                                         boolean isPublic,
                                                                         ClockProvider clockProvider) {
        return new SubscriptionDescriptorModificationRequest(
            usageType,
            SubscriptionDescriptorModificationRequest.TERM_STANDARD,
            new StandardPeriodTerm(SubscriptionDescriptor.SubscriptionTimeUnit.MONTHS, 1),
            List.of(new EventCreationRequest.DescriptionRequest("en", "this is the title")),
            List.of(new EventCreationRequest.DescriptionRequest("en", "this is the description")),
            null,
            LocalDateTime.now(clockProvider.getClock()),
            LocalDateTime.now(clockProvider.getClock()).plusMonths(5),
            new BigDecimal("10.00"),
            new BigDecimal("7.7"),
            PriceContainer.VatStatus.INCLUDED,
            "CHF",
            isPublic,
            "https://alf.io/img/tutorials/check-in-app/003.png",
            "https://alf.io",
            "https://alf.io",
            "Europe/Zurich",
            false,
            List.of(PaymentProxy.STRIPE)
        );
    }

    private SubscriptionDescriptorModificationRequest creationRequest() {
        return modificationRequest(ONCE_PER_EVENT, true);
    }

}
