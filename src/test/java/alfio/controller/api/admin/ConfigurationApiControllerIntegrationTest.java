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
package alfio.controller.api.admin;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.controller.api.ControllerConfiguration;
import alfio.manager.EventManager;
import alfio.manager.payment.custom.offline.CustomOfflineConfigurationManager;
import alfio.manager.payment.custom.offline.CustomOfflineConfigurationManager.CustomOfflinePaymentMethodAlreadyExistsException;
import alfio.manager.payment.custom.offline.CustomOfflineConfigurationManager.CustomOfflinePaymentMethodDoesNotExistException;
import alfio.manager.payment.custom.offline.CustomOfflineConfigurationManager.CustomOfflinePaymentMethodIllegalDeletionStateException;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.Event.EventFormat;
import alfio.model.PriceContainer.VatStatus;
import alfio.model.TicketCategory;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.transaction.PaymentProxy;
import alfio.model.transaction.UserDefinedOfflinePaymentMethod;
import alfio.model.user.Organization;
import alfio.repository.EventRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.AlfioIntegrationTest;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.ClockProvider;

import static alfio.test.util.IntegrationTestUtil.*;

@AlfioIntegrationTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, ControllerConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
class ConfigurationApiControllerIntegrationTest {
    private static final String DEFAULT_CATEGORY_NAME = "default";

    @Autowired
    private ConfigurationApiController configurationApiController;
    @Autowired
    private ConfigurationRepository configurationRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserManager userManager;
    @Autowired
    private ClockProvider clockProvider;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private EventManager eventManager;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;
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
    void canCreateCustomOfflinePaymentMethod() throws CustomOfflinePaymentMethodAlreadyExistsException {
        final var PAYMENT_NAME = "Interac E-Transfer";
        final var PAYMENT_DESCRIPTION = "Instant Canadian bank transfer";
        final var PAYMENT_INSTRUCTIONS = "### Send the full invoiced amount to `payments@org.com`.";
        final var LOCALE = "en";

        var paymentMethod = new UserDefinedOfflinePaymentMethod(
            null,
            Map.of(LOCALE, new UserDefinedOfflinePaymentMethod.Localization(
                PAYMENT_NAME,
                PAYMENT_DESCRIPTION,
                PAYMENT_INSTRUCTIONS
            ))
        );

        var response = configurationApiController.createPaymentMethod(
            organization.getId(),
            paymentMethod,
            this.mockPrincipal
        );
        assertTrue(response.getStatusCode().is2xxSuccessful());

        var orgMethods = customOfflineConfigurationManager.getOrganizationCustomOfflinePaymentMethods(organization.getId());
        assertEquals(1, orgMethods.size());

        var retrieved = orgMethods.get(0);
        assertTrue(retrieved.getLocalizations().containsKey(LOCALE));

        var retrievedENLocale = retrieved.getLocaleByKey(LOCALE);
        assertEquals(PAYMENT_NAME, retrievedENLocale.paymentName());
        assertEquals(PAYMENT_DESCRIPTION, retrievedENLocale.paymentDescription());
        assertEquals(PAYMENT_INSTRUCTIONS, retrievedENLocale.paymentInstructions());
    }

    @Test
    void cannotCreatePaymentMethodWithExistingId() throws CustomOfflinePaymentMethodAlreadyExistsException {
        final var paymentMethods = List.of(
            new UserDefinedOfflinePaymentMethod(
                "15146df3-2436-4d2e-90b9-0d6cb273e291",
                Map.of(
                    "en", new UserDefinedOfflinePaymentMethod.Localization(
                        "Interac E-Transfer",
                        "Instant Canadian bank transfer",
                        "### Send the full invoiced amount to `payments@org.com`."
                    ),
                    "fr", new UserDefinedOfflinePaymentMethod.Localization(
                        "Virement Interac",
                        "Virement bancaire instantané au Canada",
                        "Envoyez le montant total facturé à payments@example.com"
                    )
                )
            )
        );
        final var LOCALE = "en";

        for(var pm : paymentMethods) {
            customOfflineConfigurationManager.createOrganizationCustomOfflinePaymentMethod(organization.getId(), pm);
        }

        var paymentMethod = new UserDefinedOfflinePaymentMethod(
            "15146df3-2436-4d2e-90b9-0d6cb273e291",
            Map.of(LOCALE, new UserDefinedOfflinePaymentMethod.Localization(
                "Test Name",
                "Test Description",
                "Test Instructions"
            ))
        );

        assertThrows(
            CustomOfflinePaymentMethodAlreadyExistsException.class,
            () -> {
                configurationApiController.createPaymentMethod(
                    organization.getId(),
                    paymentMethod,
                    this.mockPrincipal
                );
            }
        );
    }

    @Test
    void canGetExistingPaymentMethod() throws CustomOfflinePaymentMethodAlreadyExistsException {
        List<UserDefinedOfflinePaymentMethod> paymentMethods = List.of(
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
            customOfflineConfigurationManager.createOrganizationCustomOfflinePaymentMethod(
                organization.getId(),
                pm
            );
        }

        var response = configurationApiController.getPaymentMethodsForOrganization(
            organization.getId(),
            false,
            mockPrincipal
        );
        assertTrue(response.getStatusCode().is2xxSuccessful());

        var orgMethods = customOfflineConfigurationManager.getOrganizationCustomOfflinePaymentMethods(organization.getId());
        assertEquals(paymentMethods.size(), orgMethods.size());

        var mismatches = paymentMethods
            .stream()
            .filter(pm ->
                orgMethods
                    .stream()
                    .noneMatch(pm2 -> pm2.getPaymentMethodId().equals(pm.getPaymentMethodId()))
            )
            .count();

        assertEquals(0, mismatches, "Input payment method list and db posted list do not match.");
    }

    @Test
    void canGetActiveAndDeletedPaymentMethods() throws CustomOfflinePaymentMethodAlreadyExistsException {
        var paymentMethods = List.of(
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
        paymentMethods.get(0).setDeleted();

        for(var pm : paymentMethods) {
            customOfflineConfigurationManager.createOrganizationCustomOfflinePaymentMethod(
                organization.getId(),
                pm
            );
        }

        var nonDeletedPaymentMethodsResp = configurationApiController.getPaymentMethodsForOrganization(
            organization.getId(),
            false,
            mockPrincipal
        );
        assertTrue(nonDeletedPaymentMethodsResp.getStatusCode().is2xxSuccessful());

        var nonDeletedPaymentMethods = nonDeletedPaymentMethodsResp.getBody();
        assertEquals(1, nonDeletedPaymentMethods.size());
        assertEquals("ec6c5268-4122-4b27-98ee-fa070df11c5b", nonDeletedPaymentMethods.get(0).getPaymentMethodId());

        var allOrgMethodsResp = configurationApiController.getPaymentMethodsForOrganization(
            organization.getId(),
            true,
            mockPrincipal
        );
        assertTrue(allOrgMethodsResp.getStatusCode().is2xxSuccessful());

        var allOrgMethods = allOrgMethodsResp.getBody();
        assertEquals(2, allOrgMethods.size());

        var deletedPaymentMethods = allOrgMethods
            .stream()
            .filter(pm ->
                nonDeletedPaymentMethods
                    .stream()
                    .noneMatch(nonDeletePm -> nonDeletePm.getPaymentMethodId().equals(pm.getPaymentMethodId()))
            )
            .collect(Collectors.toList());
        assertEquals(1, deletedPaymentMethods.size());
        assertEquals("15146df3-2436-4d2e-90b9-0d6cb273e291", deletedPaymentMethods.get(0).getPaymentMethodId());
    }

    @Test
    void canUpdateExistingPaymentMethod() throws CustomOfflinePaymentMethodAlreadyExistsException, CustomOfflinePaymentMethodDoesNotExistException {
        final var NEW_PAYMENT_NAME = "Updated Name";
        final var NEW_PAYMENT_DESCRIPTION = "Test Description";
        final var NEW_PAYMENT_INSTRUCTIONS = "Test Instructions";
        final var LOCALE = "en";

        final var EXISTING_METHOD_ID = "15146df3-2436-4d2e-90b9-0d6cb273e291";
        final var paymentMethods = List.of(
            new UserDefinedOfflinePaymentMethod(
                "15146df3-2436-4d2e-90b9-0d6cb273e291",
                Map.of(
                    "en", new UserDefinedOfflinePaymentMethod.Localization(
                        "Interac E-Transfer",
                        "Instant Canadian bank transfer",
                        "### Send the full invoiced amount to `payments@org.com`."
                    ),
                    "fr", new UserDefinedOfflinePaymentMethod.Localization(
                        "Virement Interac",
                        "Virement bancaire instantané au Canada",
                        "Envoyez le montant total facturé à payments@example.com"
                    )
                )
            )
        );

        for(var pm : paymentMethods) {
            customOfflineConfigurationManager.createOrganizationCustomOfflinePaymentMethod(organization.getId(), pm);
        }

        var paymentMethod = new UserDefinedOfflinePaymentMethod(
            EXISTING_METHOD_ID,
            Map.of(LOCALE, new UserDefinedOfflinePaymentMethod.Localization(
                NEW_PAYMENT_NAME,
                NEW_PAYMENT_DESCRIPTION,
                NEW_PAYMENT_INSTRUCTIONS
            ))
        );

        var response = configurationApiController.updatePaymentMethod(
            organization.getId(),
            EXISTING_METHOD_ID,
            paymentMethod,
            mockPrincipal
        );

        assertTrue(response.getStatusCode().is2xxSuccessful());

        var orgMethods = customOfflineConfigurationManager.getOrganizationCustomOfflinePaymentMethods(organization.getId());
        assertEquals(1, orgMethods.size());

        var retrieved = orgMethods.get(0);
        assertTrue(retrieved.getLocalizations().containsKey(LOCALE));

        var retrievedENLocale = retrieved.getLocaleByKey(LOCALE);
        assertEquals(NEW_PAYMENT_NAME, retrievedENLocale.paymentName());
        assertEquals(NEW_PAYMENT_DESCRIPTION, retrievedENLocale.paymentDescription());
        assertEquals(NEW_PAYMENT_INSTRUCTIONS, retrievedENLocale.paymentInstructions());
    }

    @Test
    void updateExistingPaymentMethodBadRequestWhenNotExists() {
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

        assertThrows(
            CustomOfflinePaymentMethodDoesNotExistException.class,
            () -> configurationApiController.updatePaymentMethod(
                organization.getId(),
                paymentMethods.get(0).getPaymentMethodId(),
                paymentMethods.get(0),
                mockPrincipal
            )
        );
    }

    @Test
    void canDeleteExistingPaymentMethod() throws CustomOfflinePaymentMethodAlreadyExistsException, CustomOfflinePaymentMethodDoesNotExistException, CustomOfflinePaymentMethodIllegalDeletionStateException {
        final var EXISTING_METHOD_ID = "15146df3-2436-4d2e-90b9-0d6cb273e291";
        final var paymentMethods = List.of(
            new UserDefinedOfflinePaymentMethod(
                "15146df3-2436-4d2e-90b9-0d6cb273e291",
                Map.of(
                    "en", new UserDefinedOfflinePaymentMethod.Localization(
                        "Interac E-Transfer",
                        "Instant Canadian bank transfer",
                        "### Send the full invoiced amount to `payments@org.com`."
                    ),
                    "fr", new UserDefinedOfflinePaymentMethod.Localization(
                        "Virement Interac",
                        "Virement bancaire instantané au Canada",
                        "Envoyez le montant total facturé à payments@example.com"
                    )
                )
            )
        );

        for(var pm : paymentMethods) {
            customOfflineConfigurationManager.createOrganizationCustomOfflinePaymentMethod(organization.getId(), pm);
        }

        var response = configurationApiController.deletePaymentMethod(
            organization.getId(),
            EXISTING_METHOD_ID,
            mockPrincipal
        );
        assertTrue(response.getStatusCode().is2xxSuccessful());

        var orgMethods = customOfflineConfigurationManager.getOrganizationCustomOfflinePaymentMethods(organization.getId());
        assertEquals(0, orgMethods.size());
    }

    @Test
    void cannotDeletePaymentMethodWhichNotExists() {
        assertThrows(
            CustomOfflinePaymentMethodDoesNotExistException.class,
            () -> configurationApiController.deletePaymentMethod(
                organization.getId(),
                "15146df3-2436-4d2e-90b9-0d6cb273e291",
                mockPrincipal
            )
        );
    }

    @Test
    void cannotDeletePaymentMethodAttachedToActiveEvent() throws CustomOfflinePaymentMethodAlreadyExistsException, CustomOfflinePaymentMethodDoesNotExistException, CustomOfflinePaymentMethodIllegalDeletionStateException {
        var paymentMethods = List.of(
            new UserDefinedOfflinePaymentMethod(
                "15146df3-2436-4d2e-90b9-0d6cb273e291",
                Map.of(
                    "en", new UserDefinedOfflinePaymentMethod.Localization(
                        "Interac E-Transfer",
                        "Instant bank transfer from any Canadian account.",
                        "Send the payment to `payments@example.com`."
                    )
                )
            )
        );

        for(var pm : paymentMethods) {
            customOfflineConfigurationManager.createOrganizationCustomOfflinePaymentMethod(event.getOrganizationId(), pm);
        }

        customOfflineConfigurationManager.setAllowedCustomOfflinePaymentMethodsForEvent(
            event,
            List.of(paymentMethods.get(0).getPaymentMethodId())
        );

        assertThrows(
            CustomOfflinePaymentMethodIllegalDeletionStateException.class,
            () -> {
                configurationApiController.deletePaymentMethod(
                    event.getOrganizationId(),
                    paymentMethods.get(0).getPaymentMethodId(),
                    mockPrincipal
                );
            }
        );

        // Test after event has expired, payment method can be deleted.
        var newStartTs = ZonedDateTime.now(clockProvider.getClock()).minusDays(1).toOffsetDateTime();
        var newEndTs = newStartTs.plusHours(1);
        int result = jdbcTemplate.update(
            "update event set start_ts = :start_ts, end_ts = :end_ts where id = :id",
            new MapSqlParameterSource("start_ts", newStartTs).addValue("end_ts", newEndTs).addValue("id", event.getId())
        );
        assertEquals(1, result);

        var response = configurationApiController.deletePaymentMethod(
            event.getOrganizationId(),
            paymentMethods.get(0).getPaymentMethodId(),
            mockPrincipal
        );
        assertTrue(response.getStatusCode().is2xxSuccessful());

        assertThrows(CustomOfflinePaymentMethodDoesNotExistException.class, () ->
            customOfflineConfigurationManager.getOrganizationCustomOfflinePaymentMethodById(
                event.getOrganizationId(),
                paymentMethods.get(0).getPaymentMethodId()
            ),
            "Payment method should not exist after being deleted."
        );

        assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    void canGetAllowedPaymentMethodsForEvent() throws CustomOfflinePaymentMethodAlreadyExistsException, CustomOfflinePaymentMethodDoesNotExistException, PassedIdDoesNotExistException {
        final var EXISTING_METHOD_ID = "15146df3-2436-4d2e-90b9-0d6cb273e291";
        final var paymentMethods = List.of(
            new UserDefinedOfflinePaymentMethod(
                "15146df3-2436-4d2e-90b9-0d6cb273e291",
                Map.of(
                    "en", new UserDefinedOfflinePaymentMethod.Localization(
                        "Interac E-Transfer",
                        "Instant Canadian bank transfer",
                        "### Send the full invoiced amount to `payments@org.com`."
                    ),
                    "fr", new UserDefinedOfflinePaymentMethod.Localization(
                        "Virement Interac",
                        "Virement bancaire instantané au Canada",
                        "Envoyez le montant total facturé à payments@example.com"
                    )
                )
            )
        );

        for(var pm : paymentMethods) {
            customOfflineConfigurationManager.createOrganizationCustomOfflinePaymentMethod(organization.getId(), pm);
        }

        customOfflineConfigurationManager.setAllowedCustomOfflinePaymentMethodsForEvent(
            event,
            List.of(EXISTING_METHOD_ID)
        );

        var response = configurationApiController.getAllowedPaymentMethodsForEvent(
            event.getId(),
            mockPrincipal
        );
        assertTrue(response.getStatusCode().is2xxSuccessful());

        var returnedAllowedPaymentMethods = response.getBody();
        assertEquals(1, returnedAllowedPaymentMethods.size());

        var allowedPaymentMethod = returnedAllowedPaymentMethods.get(0);
        assertEquals(EXISTING_METHOD_ID, allowedPaymentMethod.getPaymentMethodId());
    }

    @Test
    void canSetAllowedPaymentMethodsForEvent() throws CustomOfflinePaymentMethodAlreadyExistsException, PassedIdDoesNotExistException, CustomOfflinePaymentMethodDoesNotExistException {
        final var EXISTING_METHOD_ID = "15146df3-2436-4d2e-90b9-0d6cb273e291";
        final var paymentMethods = List.of(
            new UserDefinedOfflinePaymentMethod(
                "15146df3-2436-4d2e-90b9-0d6cb273e291",
                Map.of(
                    "en", new UserDefinedOfflinePaymentMethod.Localization(
                        "Interac E-Transfer",
                        "Instant Canadian bank transfer",
                        "### Send the full invoiced amount to `payments@org.com`."
                    ),
                    "fr", new UserDefinedOfflinePaymentMethod.Localization(
                        "Virement Interac",
                        "Virement bancaire instantané au Canada",
                        "Envoyez le montant total facturé à payments@example.com"
                    )
                )
            )
        );

        for(var pm : paymentMethods) {
            customOfflineConfigurationManager.createOrganizationCustomOfflinePaymentMethod(organization.getId(), pm);
        }

        var response = configurationApiController.setEventAllowedPaymentMethods(
            event.getId(),
            List.of(EXISTING_METHOD_ID),
            mockPrincipal
        );
        assertTrue(response.getStatusCode().is2xxSuccessful());

        var eventSelectedPaymentMethods = customOfflineConfigurationManager.getAllowedCustomOfflinePaymentMethodsForEvent(
            event
        );

        assertEquals(1, eventSelectedPaymentMethods.size());
        assertEquals(EXISTING_METHOD_ID, eventSelectedPaymentMethods.get(0).getPaymentMethodId());
    }

    @Test
    void cannotSetAllowedPaymentMethodsToNonExisting() throws CustomOfflinePaymentMethodAlreadyExistsException {
        final var paymentMethods = List.of(
            new UserDefinedOfflinePaymentMethod(
                "15146df3-2436-4d2e-90b9-0d6cb273e291",
                Map.of(
                    "en", new UserDefinedOfflinePaymentMethod.Localization(
                        "Interac E-Transfer",
                        "Instant Canadian bank transfer",
                        "### Send the full invoiced amount to `payments@org.com`."
                    ),
                    "fr", new UserDefinedOfflinePaymentMethod.Localization(
                        "Virement Interac",
                        "Virement bancaire instantané au Canada",
                        "Envoyez le montant total facturé à payments@example.com"
                    )
                )
            )
        );

        for (var pm : paymentMethods) {
            customOfflineConfigurationManager.createOrganizationCustomOfflinePaymentMethod(
                organization.getId(),
                pm
            );
        }

        assertThrows(
            CustomOfflinePaymentMethodDoesNotExistException.class,
            () -> configurationApiController.setEventAllowedPaymentMethods(
                event.getId(),
                List.of("edc42b77-8696-4357-9164-0f09eb055855"), // Does not exist in ORG
                mockPrincipal
            )
        );
    }
}
