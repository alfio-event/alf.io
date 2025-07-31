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
package alfio.manager.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.controller.api.ControllerConfiguration;
import alfio.manager.payment.custom.offline.CustomOfflineConfigurationManager;
import alfio.manager.payment.custom.offline.CustomOfflineConfigurationManager.CustomOfflinePaymentMethodAlreadyExistsException;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.modification.OrganizationModification;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentContext;
import alfio.model.transaction.TransactionRequest;
import alfio.model.transaction.UserDefinedOfflinePaymentMethod;
import alfio.model.user.Organization;
import alfio.model.user.Role;
import alfio.model.user.User;
import alfio.repository.EventRepository;
import alfio.repository.TicketReservationRepository;
import alfio.repository.TransactionRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.AlfioIntegrationTest;
import alfio.test.util.IntegrationTestUtil;
import static alfio.test.util.TestUtil.clockProvider;

@AlfioIntegrationTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, ControllerConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
class CustomOfflinePaymentManagerIntegrationTest {
    @Autowired
    private ConfigurationRepository configurationRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private TicketReservationRepository ticketReservationRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private UserManager userManager;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private CustomOfflineConfigurationManager customOfflineConfigurationManager;

    private Principal mockPrincipal;
    private Organization organization;
    private List<UserDefinedOfflinePaymentMethod> paymentMethods;

    @BeforeEach
    void ensureConfiguration() throws CustomOfflinePaymentMethodAlreadyExistsException {
        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);

        String organizationName = UUID.randomUUID().toString();
        String username = UUID.randomUUID().toString();

        var organizationModification = new OrganizationModification(
            null,
            organizationName,
            "email@example.com",
            "org",
            null,
            null
        );
        userManager.createOrganization(organizationModification, null);
        organization = organizationRepository.findByName(organizationName).orElseThrow();
        userManager.insertUser(
            organization.getId(),
            username,
            "test",
            "test",
            "test@example.com",
            Role.OWNER,
            User.Type.INTERNAL,
            null
        );

        this.mockPrincipal = Mockito.mock(Principal.class);
        Mockito.when(mockPrincipal.getName()).thenReturn(username);

        paymentMethods = List.of(
            new UserDefinedOfflinePaymentMethod(
                "15146df3-2436-4d2e-90b9-0d6cb273e291",
                Map.of(
                    "en", new UserDefinedOfflinePaymentMethod.Localization(
                        "Interac E-Transfer",
                        "Instant bank transfer from any Canadian account.",
                        "Send the payment to `payments@example.com`."
                    )
                )
            ),
            new UserDefinedOfflinePaymentMethod(
                "ec6c5268-4122-4b27-98ee-fa070df11c5b",
                Map.of(
                    "en", new UserDefinedOfflinePaymentMethod.Localization(
                        "Venmo",
                        "Instant money transfers via the Venmo app.",
                        "Send the payment to user `exampleco` on Venmo."
                    )
                )
            )
        );

        for(var pm : paymentMethods) {
            customOfflineConfigurationManager.createOrganizationCustomOfflinePaymentMethod(organization.getId(), pm);
        }
    }

    @Test
    void supportedPaymentMethodsReturnsExpected() {
        CustomOfflinePaymentManager paymentManager = new CustomOfflinePaymentManager(
            clockProvider(),
            ticketReservationRepository,
            transactionRepository,
            eventRepository,
            customOfflineConfigurationManager
        );

        var event = Mockito.mock(Event.class);
        var configLevel = ConfigurationLevel.organization(organization.getId());
        var paymentContext = new PaymentContext(event, configLevel);
        var transactionRequest = TransactionRequest.empty();

        var supportedMethods = paymentManager.getSupportedPaymentMethods(paymentContext, transactionRequest);

        assertEquals(
            supportedMethods.size(),
            paymentMethods.size(),
            "supportedMethods length does not match example methods."
        );

        assertTrue(
            supportedMethods
                .stream()
                .allMatch(spm ->
                    paymentMethods
                        .stream()
                        .anyMatch(pm -> spm.getPaymentMethodId().equals(pm.getPaymentMethodId()))
                )
            ,
            "Supported offline payment methods do not match example methods."
        );
    }

    @Test
    void supportedPaymentMethodsNoOrgReturnsEmpty() {
        CustomOfflinePaymentManager paymentManager = new CustomOfflinePaymentManager(
            clockProvider(),
            ticketReservationRepository,
            transactionRepository,
            eventRepository,
            customOfflineConfigurationManager
        );

        var event = Mockito.mock(Event.class);
        var configLevel = ConfigurationLevel.system();
        var paymentContext = new PaymentContext(event, configLevel);
        var transactionRequest = TransactionRequest.empty();

        var supportedMethods = paymentManager.getSupportedPaymentMethods(paymentContext, transactionRequest);

        assertEquals(
            Set.of(),
            supportedMethods,
            "In the event of no orgId available, supported methods should be empty."
        );
    }

    @Test
    void supportedPaymentMethodsNoOfflinePaymentsKeyReturnsEmpty() {
        configurationRepository.deleteOrganizationLevelByKey(
            ConfigurationKeys.CUSTOM_OFFLINE_PAYMENTS.name(),
            organization.getId()
        );

        CustomOfflinePaymentManager paymentManager = new CustomOfflinePaymentManager(
            clockProvider(),
            ticketReservationRepository,
            transactionRepository,
            eventRepository,
            customOfflineConfigurationManager
        );

        var event = Mockito.mock(Event.class);
        var configLevel = ConfigurationLevel.organization(organization.getId());
        var paymentContext = new PaymentContext(event, configLevel);
        var transactionRequest = TransactionRequest.empty();

        var supportedMethods = paymentManager.getSupportedPaymentMethods(paymentContext, transactionRequest);

        assertEquals(
            Set.of(),
            supportedMethods,
            "In the event of no CUSTOM_OFFLINE_PAYMENTS config key, supported methods should be empty."
        );
    }
}
