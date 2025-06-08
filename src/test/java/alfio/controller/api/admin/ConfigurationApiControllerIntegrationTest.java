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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.controller.api.ControllerConfiguration;
import alfio.manager.EventManager;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.TicketCategory;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.UserDefinedOfflinePaymentMethod;
import alfio.model.user.Organization;
import alfio.repository.EventRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.AlfioIntegrationTest;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.ClockProvider;
import alfio.util.Json;

import static alfio.test.util.IntegrationTestUtil.*;

@AlfioIntegrationTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, ControllerConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
public class ConfigurationApiControllerIntegrationTest {
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
    private ObjectMapper objectMapper;

    private Principal mockPrincipal;
    private Organization organization;
    private Event event;

    @BeforeEach
    public void ensureConfiguration() {
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

        organization = organizationRepository.getById(event.getOrganizationId());

        var username = eventAndUser.getRight();
        mockPrincipal = Mockito.mock(Principal.class);
        Mockito.when(mockPrincipal.getName()).thenReturn(owner(username));
    }

    @Test
    void canCreateCustomOfflinePaymentMethod() throws JsonMappingException, JsonProcessingException {
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

        var maybeSaved = configurationRepository.findByKeyAtOrganizationLevel(
            organization.getId(),
            ConfigurationKeys.CUSTOM_OFFLINE_PAYMENTS.name()
        );

        assertTrue(maybeSaved.isPresent());

        var saved = maybeSaved.get();

        ObjectMapper objectMapper = new ObjectMapper();
        var orgMethods = objectMapper.readValue(
            saved.getValue(),
            new TypeReference<List<UserDefinedOfflinePaymentMethod>>() {}
        );

        assertEquals(1, orgMethods.size());

        var retrieved = orgMethods.get(0);
        assertTrue(retrieved.getLocalizations().containsKey(LOCALE));

        var retrieved_en_locale = retrieved.getLocaleByKey(LOCALE);
        assertEquals(PAYMENT_NAME, retrieved_en_locale.getPaymentName());
        assertEquals(PAYMENT_DESCRIPTION, retrieved_en_locale.getPaymentDescription());
        assertEquals(PAYMENT_INSTRUCTIONS, retrieved_en_locale.getPaymentInstructions());
    }

    @Test
    void cannotCreatePaymentMethodWithExistingId() {
        final var EXISTING_CONFIG = "[{\"paymentMethodId\":\"15146df3-2436-4d2e-90b9-0d6cb273e291\",\"localizations\":{\"en\":{\"paymentName\":\"Interac E-Transfer\",\"paymentDescription\":\"Instant Canadian bank transfer\",\"paymentInstructions\":\"### Send the full invoiced amount to `payments@org.com`.\"},\"fr\":{\"paymentName\":\"Virement Interac\",\"paymentDescription\":\"Virement bancaire instantané au Canada\",\"paymentInstructions\":\"Envoyez le montant total facturé à payments@example.com\"}}}]";
        final var LOCALE = "en";

        var insertResult = configurationRepository.insertOrganizationLevel(
            organization.getId(),
            ConfigurationKeys.CUSTOM_OFFLINE_PAYMENTS.name(),
            EXISTING_CONFIG,
            null
        );
        assertEquals(1, insertResult);

        var paymentMethod = new UserDefinedOfflinePaymentMethod(
            "15146df3-2436-4d2e-90b9-0d6cb273e291",
            Map.of(LOCALE, new UserDefinedOfflinePaymentMethod.Localization(
                "Test Name",
                "Test Description",
                "Test Instructions"
            ))
        );

        var response = configurationApiController.createPaymentMethod(
            organization.getId(),
            paymentMethod,
            this.mockPrincipal
        );

        assertEquals(HttpStatus.BAD_REQUEST_400, response.getStatusCode().value());
    }

    @Test
    void canGetExistingPaymentMethod() throws JsonMappingException, JsonProcessingException {
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

        ObjectMapper objectMapper = new ObjectMapper();
        var jsonPayload = objectMapper.writeValueAsString(paymentMethods);

        configurationRepository.insertOrganizationLevel(
            organization.getId(),
            ConfigurationKeys.CUSTOM_OFFLINE_PAYMENTS.name(),
            jsonPayload,
            null
        );

        var response = configurationApiController.getPaymentMethodsForOrganization(
            organization.getId(),
            mockPrincipal
        );

        assertTrue(response.getStatusCode().is2xxSuccessful());

        var maybeSaved = configurationRepository.findByKeyAtOrganizationLevel(
            organization.getId(),
            ConfigurationKeys.CUSTOM_OFFLINE_PAYMENTS.name()
        );

        assertTrue(maybeSaved.isPresent());

        var saved = maybeSaved.get();

        var orgMethods = objectMapper.readValue(
            saved.getValue(),
            new TypeReference<List<UserDefinedOfflinePaymentMethod>>() {}
        );

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
    void canUpdateExistingPaymentMethod() throws JsonMappingException, JsonProcessingException {
        final var NEW_PAYMENT_NAME = "Updated Name";
        final var NEW_PAYMENT_DESCRIPTION = "Test Description";
        final var NEW_PAYMENT_INSTRUCTIONS = "Test Instructions";
        final var LOCALE = "en";

        final var EXISTING_METHOD_ID = "15146df3-2436-4d2e-90b9-0d6cb273e291";
        final var EXISTING_CONFIG = "[{\"paymentMethodId\":\"15146df3-2436-4d2e-90b9-0d6cb273e291\",\"localizations\":{\"en\":{\"paymentName\":\"Interac E-Transfer\",\"paymentDescription\":\"Instant Canadian bank transfer\",\"paymentInstructions\":\"### Send the full invoiced amount to `payments@org.com`.\"},\"fr\":{\"paymentName\":\"Virement Interac\",\"paymentDescription\":\"Virement bancaire instantané au Canada\",\"paymentInstructions\":\"Envoyez le montant total facturé à payments@example.com\"}}}]";

        var insertResult = configurationRepository.insertOrganizationLevel(
            organization.getId(),
            ConfigurationKeys.CUSTOM_OFFLINE_PAYMENTS.name(),
            EXISTING_CONFIG,
            null
        );
        assertEquals(1, insertResult);

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

        var maybeSaved = configurationRepository.findByKeyAtOrganizationLevel(
            organization.getId(),
            ConfigurationKeys.CUSTOM_OFFLINE_PAYMENTS.name()
        );

        assertTrue(maybeSaved.isPresent());

        var saved = maybeSaved.get();

        ObjectMapper objectMapper = new ObjectMapper();
        var orgMethods = objectMapper.readValue(
            saved.getValue(),
            new TypeReference<List<UserDefinedOfflinePaymentMethod>>() {}
        );

        assertEquals(1, orgMethods.size());

        var retrieved = orgMethods.get(0);
        assertTrue(retrieved.getLocalizations().containsKey(LOCALE));

        var retrieved_en_locale = retrieved.getLocaleByKey(LOCALE);
        assertEquals(NEW_PAYMENT_NAME, retrieved_en_locale.getPaymentName());
        assertEquals(NEW_PAYMENT_DESCRIPTION, retrieved_en_locale.getPaymentDescription());
        assertEquals(NEW_PAYMENT_INSTRUCTIONS, retrieved_en_locale.getPaymentInstructions());
    }

    @Test
    void canDeleteExistingPaymentMethod() throws JsonMappingException, JsonProcessingException {
        final var EXISTING_METHOD_ID = "15146df3-2436-4d2e-90b9-0d6cb273e291";
        final var EXISTING_CONFIG = "[{\"paymentMethodId\":\"15146df3-2436-4d2e-90b9-0d6cb273e291\",\"localizations\":{\"en\":{\"paymentName\":\"Interac E-Transfer\",\"paymentDescription\":\"Instant Canadian bank transfer\",\"paymentInstructions\":\"### Send the full invoiced amount to `payments@org.com`.\"},\"fr\":{\"paymentName\":\"Virement Interac\",\"paymentDescription\":\"Virement bancaire instantané au Canada\",\"paymentInstructions\":\"Envoyez le montant total facturé à payments@example.com\"}}}]";

        configurationRepository.insertOrganizationLevel(
            organization.getId(),
            ConfigurationKeys.CUSTOM_OFFLINE_PAYMENTS.name(),
            EXISTING_CONFIG,
            null
        );

        var response = configurationApiController.deletePaymentMethod(
            organization.getId(),
            EXISTING_METHOD_ID,
            mockPrincipal
        );

        assertTrue(response.getStatusCode().is2xxSuccessful());

        var maybeSaved = configurationRepository.findByKeyAtOrganizationLevel(
            organization.getId(),
            ConfigurationKeys.CUSTOM_OFFLINE_PAYMENTS.name()
        );

        assertTrue(maybeSaved.isPresent());

        var saved = maybeSaved.get();

        ObjectMapper objectMapper = new ObjectMapper();
        var orgMethods = objectMapper.readValue(
            saved.getValue(),
            new TypeReference<List<UserDefinedOfflinePaymentMethod>>() {}
        );

        assertEquals(0, orgMethods.size());
    }

    @Test
    void canGetAllowedPaymentMethodsForEvent() throws JsonProcessingException {
        final var EXISTING_METHOD_ID = "15146df3-2436-4d2e-90b9-0d6cb273e291";
        final var EXISTING_CONFIG = "[{\"paymentMethodId\":\"15146df3-2436-4d2e-90b9-0d6cb273e291\",\"localizations\":{\"en\":{\"paymentName\":\"Interac E-Transfer\",\"paymentDescription\":\"Instant Canadian bank transfer\",\"paymentInstructions\":\"### Send the full invoiced amount to `payments@org.com`.\"},\"fr\":{\"paymentName\":\"Virement Interac\",\"paymentDescription\":\"Virement bancaire instantané au Canada\",\"paymentInstructions\":\"Envoyez le montant total facturé à payments@example.com\"}}}]";

        configurationRepository.insertOrganizationLevel(
            organization.getId(),
            ConfigurationKeys.CUSTOM_OFFLINE_PAYMENTS.name(),
            EXISTING_CONFIG,
            null
        );

        configurationRepository.insertEventLevel(
            organization.getId(),
            event.getId(),
            ConfigurationKeys.SELECTED_CUSTOM_PAYMENTS.name(),
            objectMapper.writeValueAsString(List.of(EXISTING_METHOD_ID)),
            ""
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
    void canSetAllowedPaymentMethodsForEvent() throws JsonProcessingException {
        final var EXISTING_METHOD_ID = "15146df3-2436-4d2e-90b9-0d6cb273e291";
        final var EXISTING_CONFIG = "[{\"paymentMethodId\":\"15146df3-2436-4d2e-90b9-0d6cb273e291\",\"localizations\":{\"en\":{\"paymentName\":\"Interac E-Transfer\",\"paymentDescription\":\"Instant Canadian bank transfer\",\"paymentInstructions\":\"### Send the full invoiced amount to `payments@org.com`.\"},\"fr\":{\"paymentName\":\"Virement Interac\",\"paymentDescription\":\"Virement bancaire instantané au Canada\",\"paymentInstructions\":\"Envoyez le montant total facturé à payments@example.com\"}}}]";

        configurationRepository.insertOrganizationLevel(
            organization.getId(),
            ConfigurationKeys.CUSTOM_OFFLINE_PAYMENTS.name(),
            EXISTING_CONFIG,
            null
        );

        var response = configurationApiController.setEventAllowedPaymentMethods(
            event.getId(),
            List.of(EXISTING_METHOD_ID),
            mockPrincipal
        );

        assertTrue(response.getStatusCode().is2xxSuccessful());

        var eventSelectedPaymentMethods = configurationRepository.findByKeyAtEventLevel(
            event.getId(),
            organization.getId(),
            ConfigurationKeys.SELECTED_CUSTOM_PAYMENTS.name()
        );

        assertTrue(eventSelectedPaymentMethods.isPresent());

        var parsedSelectedMethods = Json.fromJson(
            eventSelectedPaymentMethods.get().getValue(),
            new TypeReference<List<String>>() {}
        );

        assertEquals(1, parsedSelectedMethods.size());
        assertEquals(EXISTING_METHOD_ID, parsedSelectedMethods.get(0));
    }

    @Test
    void cannotSetAllowedPaymentMethodsToNonExisting() throws JsonProcessingException {
        final var EXISTING_CONFIG = "[{\"paymentMethodId\":\"15146df3-2436-4d2e-90b9-0d6cb273e291\",\"localizations\":{\"en\":{\"paymentName\":\"Interac E-Transfer\",\"paymentDescription\":\"Instant Canadian bank transfer\",\"paymentInstructions\":\"### Send the full invoiced amount to `payments@org.com`.\"},\"fr\":{\"paymentName\":\"Virement Interac\",\"paymentDescription\":\"Virement bancaire instantané au Canada\",\"paymentInstructions\":\"Envoyez le montant total facturé à payments@example.com\"}}}]";

        configurationRepository.insertOrganizationLevel(
            organization.getId(),
            ConfigurationKeys.CUSTOM_OFFLINE_PAYMENTS.name(),
            EXISTING_CONFIG,
            null
        );

        var response = configurationApiController.setEventAllowedPaymentMethods(
            event.getId(),
            List.of("edc42b77-8696-4357-9164-0f09eb055855"), // Does not exist in ORG
            mockPrincipal
        );

        assertTrue(response.getStatusCode().is4xxClientError());
    }
}
