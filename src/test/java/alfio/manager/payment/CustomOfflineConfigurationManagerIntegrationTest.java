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

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.controller.api.ControllerConfiguration;
import alfio.manager.EventManager;
import alfio.manager.payment.custom.offline.CustomOfflineConfigurationManager;
import alfio.manager.payment.custom.offline.CustomOfflineConfigurationManager.CustomOfflinePaymentMethodDoesNotExistException;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.Event.EventFormat;
import alfio.model.PriceContainer.VatStatus;
import alfio.model.TicketCategory;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentProxy;
import alfio.model.transaction.UserDefinedOfflinePaymentMethod;
import alfio.model.user.Organization;
import alfio.repository.EventRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.AlfioIntegrationTest;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.ClockProvider;

import static alfio.test.util.IntegrationTestUtil.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@AlfioIntegrationTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, ControllerConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
class CustomOfflineConfigurationManagerIntegrationTest {
    private static final String DEFAULT_CATEGORY_NAME = "default";

    @Autowired
    private ConfigurationRepository configurationRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;
    @Autowired
    private UserManager userManager;
    @Autowired
    private ClockProvider clockProvider;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private EventManager eventManager;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CustomOfflineConfigurationManager customOfflineConfigurationManager;

    private Principal mockPrincipal;
    private Organization organization;
    private Event event;

    @BeforeEach
    void ensureConfiguration() {
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
        Pair<Event, String> eventAndUser = initEvent(
            categories,
            organizationRepository,
            userManager,
            eventManager,
            eventRepository,
            null,
            EventFormat.ONLINE,
            VatStatus.INCLUDED,
            List.of(PaymentProxy.CUSTOM_OFFLINE)
        );
        event = eventAndUser.getLeft();

        organization = organizationRepository.getById(event.getOrganizationId());

        var username = eventAndUser.getRight();
        mockPrincipal = Mockito.mock(Principal.class);
        Mockito.when(mockPrincipal.getName()).thenReturn(owner(username));
    }

    @Test
    void canSetDeniedPaymentMethodsWhenKeyAlreadyExists() throws JsonProcessingException, CustomOfflinePaymentMethodDoesNotExistException {
        final var paymentMethods = List.of(
            new UserDefinedOfflinePaymentMethod(
                "15146df3-2436-4d2e-90b9-0d6cb273e291",
                Map.of(
                    "en", new UserDefinedOfflinePaymentMethod.Localization(
                        "Interac E-Transfer",
                        "Instant Canadian bank transfer",
                        "### Send the full invoiced amount to `payments@org.com`."
                    )
                )
            ),
            new UserDefinedOfflinePaymentMethod(
                "853fdf8d-9489-46d1-89ce-6266e18fb4db",
                Map.of(
                    "en", new UserDefinedOfflinePaymentMethod.Localization(
                        "Cash App",
                        "Instant money transfer through the Cash App IOS/Android app",
                        "### Send the full invoiced amount to user `org1payments`."
                    )
                )
            )
        );

        String paymentMethodsJson = objectMapper.writeValueAsString(paymentMethods);
        configurationRepository.insertOrganizationLevel(
            organization.getId(),
            ConfigurationKeys.CUSTOM_OFFLINE_PAYMENTS.name(),
            paymentMethodsJson,
            ConfigurationKeys.CUSTOM_OFFLINE_PAYMENTS.getDescription()
        );

        var categories = ticketCategoryRepository.findAllTicketCategories(event.getId());

        customOfflineConfigurationManager.setDeniedPaymentMethodsByTicketCategory(
            event,
            categories.get(0),
            List.of(paymentMethods.get(0))
        );

        var deniedJson = configurationRepository.findByKeyAtCategoryLevel(
            event.getId(),
            event.getOrganizationId(),
            categories.get(0).getId(),
            ConfigurationKeys.DENIED_CUSTOM_PAYMENTS.name()
        ).get().getValue();

        var deniedItems = objectMapper.readValue(deniedJson, new TypeReference<List<String>>() {});
        assertEquals(1, deniedItems.size());
        assertEquals(paymentMethods.get(0).getPaymentMethodId(), deniedItems.get(0));

        customOfflineConfigurationManager.setDeniedPaymentMethodsByTicketCategory(
            event,
            categories.get(0),
            List.of(paymentMethods.get(1))
        );

        deniedJson = configurationRepository.findByKeyAtCategoryLevel(
            event.getId(),
            event.getOrganizationId(),
            categories.get(0).getId(),
            ConfigurationKeys.DENIED_CUSTOM_PAYMENTS.name()
        ).get().getValue();

        deniedItems = objectMapper.readValue(deniedJson, new TypeReference<List<String>>() {});
        assertEquals(1, deniedItems.size());
        assertEquals(paymentMethods.get(1).getPaymentMethodId(), deniedItems.get(0));
    }

    @Test
    void cannotAddDeniedMethodsWhichDoNotExist() {
        final var paymentMethods = List.of(
            new UserDefinedOfflinePaymentMethod(
                "15146df3-2436-4d2e-90b9-0d6cb273e291",
                Map.of(
                    "en", new UserDefinedOfflinePaymentMethod.Localization(
                        "Interac E-Transfer",
                        "Instant Canadian bank transfer",
                        "### Send the full invoiced amount to `payments@org.com`."
                    )
                )
            )
        );
        // Not inserting into DB

        var categories = ticketCategoryRepository.findAllTicketCategories(event.getId());

        assertThrows(
            CustomOfflinePaymentMethodDoesNotExistException.class,
            () -> customOfflineConfigurationManager.setDeniedPaymentMethodsByTicketCategory(
                event,
                categories.get(0),
                List.of(paymentMethods.get(0))
            )
        );
    }

    @Test
    void canSetAllowedEventPaymentMethodsWhenKeyAlreadyExists() throws JsonProcessingException, CustomOfflinePaymentMethodDoesNotExistException {
        final var paymentMethods = List.of(
            new UserDefinedOfflinePaymentMethod(
                "15146df3-2436-4d2e-90b9-0d6cb273e291",
                Map.of(
                    "en", new UserDefinedOfflinePaymentMethod.Localization(
                        "Interac E-Transfer",
                        "Instant Canadian bank transfer",
                        "### Send the full invoiced amount to `payments@org.com`."
                    )
                )
            ),
            new UserDefinedOfflinePaymentMethod(
                "853fdf8d-9489-46d1-89ce-6266e18fb4db",
                Map.of(
                    "en", new UserDefinedOfflinePaymentMethod.Localization(
                        "Cash App",
                        "Instant money transfer through the Cash App IOS/Android app",
                        "### Send the full invoiced amount to user `org1payments`."
                    )
                )
            )
        );

        String paymentMethodsJson = objectMapper.writeValueAsString(paymentMethods);
        configurationRepository.insertOrganizationLevel(
            organization.getId(),
            ConfigurationKeys.CUSTOM_OFFLINE_PAYMENTS.name(),
            paymentMethodsJson,
            ConfigurationKeys.CUSTOM_OFFLINE_PAYMENTS.getDescription()
        );

        customOfflineConfigurationManager.setAllowedCustomOfflinePaymentMethodsForEvent(
            event,
            List.of(paymentMethods.get(0).getPaymentMethodId())
        );

        var allowedMethodsJson = configurationRepository.findByKeyAtEventLevel(
            event.getId(),
            event.getOrganizationId(),
            ConfigurationKeys.SELECTED_CUSTOM_PAYMENTS.name()
        ).get().getValue();

        var allowedMethods = objectMapper.readValue(allowedMethodsJson, new TypeReference<List<String>>() {});
        assertEquals(1, allowedMethods.size());
        assertEquals(paymentMethods.get(0).getPaymentMethodId(), allowedMethods.get(0));

        customOfflineConfigurationManager.setAllowedCustomOfflinePaymentMethodsForEvent(
            event,
            List.of(paymentMethods.get(1).getPaymentMethodId())
        );

        allowedMethodsJson = configurationRepository.findByKeyAtEventLevel(
            event.getId(),
            event.getOrganizationId(),
            ConfigurationKeys.SELECTED_CUSTOM_PAYMENTS.name()
        ).get().getValue();

        allowedMethods = objectMapper.readValue(allowedMethodsJson, new TypeReference<List<String>>() {});
        assertEquals(1, allowedMethods.size());
        assertEquals(paymentMethods.get(1).getPaymentMethodId(), allowedMethods.get(0));
    }

    @Test
    void cannotUpdateDeletedPaymentMethod() throws JsonProcessingException, CustomOfflinePaymentMethodDoesNotExistException {
        final var paymentMethods = List.of(
            new UserDefinedOfflinePaymentMethod(
                "15146df3-2436-4d2e-90b9-0d6cb273e291",
                new HashMap<>() {{
                    put("en", new UserDefinedOfflinePaymentMethod.Localization(
                        "Interac E-Transfer",
                        "Instant Canadian bank transfer",
                        "### Send the full invoiced amount to `payments@org.com`."
                    ));
                }}
            ),
            new UserDefinedOfflinePaymentMethod(
                "853fdf8d-9489-46d1-89ce-6266e18fb4db",
                new HashMap<>() {{
                    put("en", new UserDefinedOfflinePaymentMethod.Localization(
                        "Cash App",
                        "Instant money transfer through the Cash App IOS/Android app",
                        "### Send the full invoiced amount to user `org1payments`."
                    ));
                }}
            )
        );
        paymentMethods.get(0).setDeleted();

        String paymentMethodsJson = objectMapper.writeValueAsString(paymentMethods);
        configurationRepository.insertOrganizationLevel(
            organization.getId(),
            ConfigurationKeys.CUSTOM_OFFLINE_PAYMENTS.name(),
            paymentMethodsJson,
            ConfigurationKeys.CUSTOM_OFFLINE_PAYMENTS.getDescription()
        );

        var updated = paymentMethods.get(0);
        var originalLocalization = updated.getLocaleByKey("en");
        var updatedLocalization = new UserDefinedOfflinePaymentMethod.Localization(
            "Interac E-Transfer 2",
            originalLocalization.paymentDescription(),
            originalLocalization.paymentInstructions()
        );
        updated.getLocalizations().put("en", updatedLocalization);

        assertThrows(
            CustomOfflinePaymentMethodDoesNotExistException.class,
            () -> {
                customOfflineConfigurationManager.updateOrganizationCustomOfflinePaymentMethod(
                    organization.getId(), updated
                );
            }
        );
    }
}
