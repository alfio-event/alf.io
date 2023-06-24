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
import alfio.controller.api.v1.admin.ConfigurationApiV1Controller;
import alfio.controller.api.v1.admin.EventApiV1Controller;
import alfio.controller.api.v1.admin.SubscriptionApiV1Controller;
import alfio.manager.user.UserManager;
import alfio.model.PurchaseContext;
import alfio.model.modification.OrganizationModification;
import alfio.model.system.ConfigurationKeyValuePathLevel;
import alfio.model.user.Organization;
import alfio.model.user.Role;
import alfio.model.user.User;
import alfio.repository.EventRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.AlfioIntegrationTest;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.BaseIntegrationTest;
import alfio.util.ClockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static alfio.controller.api.v1.EventApiV1IntegrationTest.creationRequest;
import static alfio.controller.api.v1.SubscriptionApiV1IntegrationTest.modificationRequest;
import static alfio.model.subscription.SubscriptionDescriptor.SubscriptionUsageType.ONCE_PER_EVENT;
import static alfio.model.system.ConfigurationKeys.*;
import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.*;


@AlfioIntegrationTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, ControllerConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
class ConfigurationApiV1IntegrationTest extends BaseIntegrationTest {

    public static final List<String> OPTIONS_TO_MODIFY = List.of(GENERATE_ONLY_INVOICE.name(), USE_INVOICE_NUMBER_AS_ID.name(), VAT_NUMBER_IS_REQUIRED.name());
    @Autowired
    private ConfigurationRepository configurationRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private UserManager userManager;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private EventApiV1Controller eventApiController;
    @Autowired
    private SubscriptionApiV1Controller subscriptionApiV1Controller;
    @Autowired
    private ConfigurationApiV1Controller controller;
    @Autowired
    private ClockProvider clockProvider;

    private Principal mockPrincipal;
    private Organization organization;

    @BeforeEach
    public void ensureConfiguration() {
        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);

        String organizationName = UUID.randomUUID().toString();
        String username = UUID.randomUUID().toString();

        var organizationModification = new OrganizationModification(null, organizationName, "email@example.com", "org", null, null);
        userManager.createOrganization(organizationModification, null);
        organization = organizationRepository.findByName(organizationName).orElseThrow();
        userManager.insertUser(organization.getId(), username, "test", "test", "test@example.com", Role.API_CONSUMER, User.Type.INTERNAL, null);

        this.mockPrincipal = Mockito.mock(Principal.class);
        Mockito.when(mockPrincipal.getName()).thenReturn(username);
    }

    @Test
    void addEventConfiguration() {
        String slug = "test";
        eventApiController.create(creationRequest(slug), mockPrincipal);
        int eventId = eventRepository.findOptionalEventAndOrganizationIdByShortName(slug).orElseThrow().getId();
        var existing = configurationRepository.findByEventAndKeys(organization.getId(), eventId, OPTIONS_TO_MODIFY);
        assertTrue(existing.isEmpty());
        assertEquals(1, configurationRepository.insertEventLevel(organization.getId(), eventId, GENERATE_ONLY_INVOICE.name(), "true", ""));
        var payload = Map.ofEntries(
            entry(GENERATE_ONLY_INVOICE.name(), "false"),
            entry(USE_INVOICE_NUMBER_AS_ID.name(), "true"),
            entry(VAT_NUMBER_IS_REQUIRED.name(), "true")
        );
        var response = controller.saveConfigurationForPurchaseContext(organization.getId(), PurchaseContext.PurchaseContextType.event, slug, payload, mockPrincipal);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        var modified = configurationRepository.findByEventAndKeys(organization.getId(), eventId, OPTIONS_TO_MODIFY);
        assertEquals(3, modified.size());
        for (ConfigurationKeyValuePathLevel kv : modified) {
            assertEquals(kv.getConfigurationKey() == GENERATE_ONLY_INVOICE ? "false" : "true", kv.getValue());
        }
    }

    @Test
    void addSubscriptionConfiguration() {
        var createResponse = subscriptionApiV1Controller.create(modificationRequest(ONCE_PER_EVENT, true, clockProvider), mockPrincipal);
        assertTrue(createResponse.getStatusCode().is2xxSuccessful());
        assertNotNull(createResponse.getBody());
        var subscriptionDescriptorId = UUID.fromString(createResponse.getBody());
        var existing = configurationRepository.findBySubscriptionDescriptorAndKeys(organization.getId(), subscriptionDescriptorId, OPTIONS_TO_MODIFY);
        assertTrue(existing.isEmpty());
        assertEquals(1, configurationRepository.insertSubscriptionDescriptorLevel(organization.getId(), subscriptionDescriptorId, GENERATE_ONLY_INVOICE.name(), "true", ""));
        var payload = Map.ofEntries(
            entry(GENERATE_ONLY_INVOICE.name(), "false"),
            entry(USE_INVOICE_NUMBER_AS_ID.name(), "true"),
            entry(VAT_NUMBER_IS_REQUIRED.name(), "true")
        );
        var response = controller.saveConfigurationForPurchaseContext(organization.getId(), PurchaseContext.PurchaseContextType.subscription, subscriptionDescriptorId.toString(), payload, mockPrincipal);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        var modified = configurationRepository.findBySubscriptionDescriptorAndKeys(organization.getId(), subscriptionDescriptorId, OPTIONS_TO_MODIFY);
        assertEquals(3, modified.size());
        for (ConfigurationKeyValuePathLevel kv : modified) {
            assertEquals(kv.getConfigurationKey() == GENERATE_ONLY_INVOICE ? "false" : "true", kv.getValue());
        }
    }

    @Test
    void addOrganizationConfiguration() {
        var existing = configurationRepository.findByOrganizationAndKeys(organization.getId(), OPTIONS_TO_MODIFY);
        assertTrue(existing.isEmpty());
        assertEquals(1, configurationRepository.insertOrganizationLevel(organization.getId(), GENERATE_ONLY_INVOICE.name(), "true", ""));
        var payload = Map.ofEntries(
            entry(GENERATE_ONLY_INVOICE.name(), "false"),
            entry(USE_INVOICE_NUMBER_AS_ID.name(), "true"),
            entry(VAT_NUMBER_IS_REQUIRED.name(), "true")
        );
        var response = controller.saveConfigurationForOrganization(organization.getId(), payload, mockPrincipal);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        var modified = configurationRepository.findByOrganizationAndKeys(organization.getId(), OPTIONS_TO_MODIFY);
        assertEquals(3, modified.size());
        for (ConfigurationKeyValuePathLevel kv : modified) {
            assertEquals(kv.getConfigurationKey() == GENERATE_ONLY_INVOICE ? "false" : "true", kv.getValue());
        }
    }
}
