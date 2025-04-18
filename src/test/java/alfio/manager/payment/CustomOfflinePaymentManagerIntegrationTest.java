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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.controller.api.ControllerConfiguration;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
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
public class CustomOfflinePaymentManagerIntegrationTest {
    @Autowired
    private ConfigurationRepository configurationRepository;
    @Autowired
    private ConfigurationManager configurationManager;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private TicketReservationRepository ticketReservationRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private UserManager userManager;

    private Principal mockPrincipal;
    private Organization organization;
    private List<UserDefinedOfflinePaymentMethod> paymentMethods;

    @BeforeEach
    public void ensureConfiguration() throws JsonProcessingException {
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

        ObjectMapper objectMapper = new ObjectMapper();
        var jsonPayload = objectMapper.writeValueAsString(paymentMethods);

        configurationRepository.insertOrganizationLevel(
            organization.getId(),
            ConfigurationKeys.CUSTOM_OFFLINE_PAYMENTS.name(),
            jsonPayload,
            null
        );
    }

    @Test
    void supportedPaymentMethodsReturnsExpected() {
        CustomOfflinePaymentManager paymentManager = new CustomOfflinePaymentManager(
            clockProvider(),
            configurationRepository,
            ticketReservationRepository,
            transactionRepository,
            configurationManager
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
            configurationRepository,
            ticketReservationRepository,
            transactionRepository,
            configurationManager
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
            configurationRepository,
            ticketReservationRepository,
            transactionRepository,
            configurationManager
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
