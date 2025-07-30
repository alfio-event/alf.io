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

import alfio.controller.api.support.TicketHelper;
import alfio.job.executor.AssignTicketToSubscriberJobExecutor;
import alfio.manager.AccessService;
import alfio.manager.BillingDocumentManager;
import alfio.manager.EventManager;
import alfio.manager.payment.custom.offline.CustomOfflineConfigurationManager;
import alfio.manager.payment.custom.offline.CustomOfflineConfigurationManager.CustomOfflinePaymentMethodAlreadyExistsException;
import alfio.manager.payment.custom.offline.CustomOfflineConfigurationManager.CustomOfflinePaymentMethodDoesNotExistException;
import alfio.manager.payment.custom.offline.CustomOfflineConfigurationManager.CustomOfflinePaymentMethodIllegalDeletionStateException;
import alfio.manager.system.AdminJobExecutor;
import alfio.manager.system.AdminJobManager;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.modification.ConfigurationModification;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.UserDefinedOfflinePaymentMethod;
import alfio.model.user.Organization;
import alfio.repository.EventRepository;
import alfio.util.ClockProvider;
import alfio.util.Json;
import alfio.util.RequestUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static alfio.manager.system.AdminJobExecutor.JobName.ASSIGN_TICKETS_TO_SUBSCRIBERS;
import static alfio.model.system.ConfigurationKeys.*;
import static alfio.util.Wrappers.optionally;
import static java.util.Objects.requireNonNullElse;

@RestController
@RequestMapping("/admin/api/configuration")
@AllArgsConstructor
public class ConfigurationApiController {

    private final ConfigurationManager configurationManager;
    private final BillingDocumentManager billingDocumentManager;
    private final AdminJobManager adminJobManager;
    private final EventRepository eventRepository;
    private final EventManager eventManager;
    private final ClockProvider clockProvider;
    private final UserManager userManager;
    private final AccessService accessService;
    private final CustomOfflineConfigurationManager customOfflineConfigurationManager;

    @GetMapping(value = "/load")
    public Map<ConfigurationKeys.SettingCategory, List<Configuration>> loadConfiguration(Principal principal) {
        accessService.ensureAdmin(principal);
        return configurationManager.loadAllSystemConfigurationIncludingMissing(principal.getName());
    }

    @GetMapping("/basic-configuration-needed")
    public boolean isBasicConfigurationNeeded() {
        return configurationManager.isBasicConfigurationNeeded();
    }

    @PostMapping(value = "/update")
    public boolean updateConfiguration(@RequestBody ConfigurationModification configuration, Principal principal) {
        accessService.ensureAdmin(principal);
        configurationManager.saveSystemConfiguration(ConfigurationKeys.fromString(configuration.getKey()), configuration.getValue());
        return true;
    }

    @PostMapping(value = "/update-bulk")
    public boolean updateConfiguration(@RequestBody Map<ConfigurationKeys.SettingCategory, List<ConfigurationModification>> input, Principal principal) {
        accessService.ensureAdmin(principal);
        List<ConfigurationModification> list = Objects.requireNonNull(input).values().stream().flatMap(Collection::stream).toList();
        configurationManager.saveAllSystemConfiguration(list);
        return true;
    }

    @GetMapping(value = "/organizations/{organizationId}/load")
    public Map<ConfigurationKeys.SettingCategory, List<Configuration>> loadOrganizationConfiguration(@PathVariable int organizationId, Principal principal) {
        accessService.checkOrganizationOwnership(principal, organizationId);
        return configurationManager.loadOrganizationConfig(organizationId, principal.getName());
    }

    @PostMapping(value = "/organizations/{organizationId}/update")
    public boolean updateOrganizationConfiguration(@PathVariable int organizationId,
                                                   @RequestBody Map<ConfigurationKeys.SettingCategory, List<ConfigurationModification>> input, Principal principal) {
        // id of input are not used, so not needed to check for consistency
        accessService.checkOrganizationOwnership(principal, organizationId);
        //
        configurationManager.saveAllOrganizationConfiguration(organizationId, input.values().stream().flatMap(Collection::stream).toList(), principal.getName());
        return true;
    }

    @GetMapping(value = "/events/{eventId}/load")
    public Map<ConfigurationKeys.SettingCategory, List<Configuration>> loadEventConfiguration(@PathVariable int eventId,
                                                                                              Principal principal) {
        accessService.checkEventOwnership(principal, eventId);
        return configurationManager.loadEventConfig(eventId, principal.getName());
    }

    @GetMapping("/events/{eventName}/single/{key}")
    public ResponseEntity<String> getSingleConfigForEvent(@PathVariable("eventName") String eventShortName,
                                                         @PathVariable String key,
                                                         Principal principal) {
        accessService.checkEventOwnership(principal, eventShortName);

        var optionalEvent = eventManager.getOptionalByName(eventShortName, principal.getName());

        if(optionalEvent.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var event = optionalEvent.get();
        String singleConfigForEvent = configurationManager.getSingleConfigForEvent(event.getId(), key, principal.getName());
        if(singleConfigForEvent == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(singleConfigForEvent);
    }

    @GetMapping("/organizations/{organizationId}/single/{key}")
    public ResponseEntity<String> getSingleConfigForOrganization(@PathVariable int organizationId,
                                                                 @PathVariable String key,
                                                                 Principal principal) {
        accessService.checkOrganizationOwnership(principal, organizationId);

        String config = configurationManager.getSingleConfigForOrganization(organizationId, key, principal.getName());
        if(config == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(config);
    }

    @PostMapping(value = "/organizations/{organizationId}/events/{eventId}/update")
    public boolean updateEventConfiguration(@PathVariable int organizationId, @PathVariable int eventId,
                                            @RequestBody Map<ConfigurationKeys.SettingCategory, List<ConfigurationModification>> input, Principal principal) {
        // id of input are not used, so not needed to check for consistency
        accessService.checkEventOwnership(principal, eventId, organizationId);
        //
        configurationManager.saveAllEventConfiguration(eventId, organizationId, input.values().stream().flatMap(Collection::stream).toList(), principal.getName());
        return true;
    }

    @PostMapping(value = "/events/{eventId}/categories/{categoryId}/update")
    public boolean updateCategoryConfiguration(@PathVariable int categoryId, @PathVariable int eventId,
                                                    @RequestBody Map<ConfigurationKeys.SettingCategory, List<ConfigurationModification>> input, Principal principal) {
        // id of input are not used, so no needed to check for consistency
        accessService.checkCategoryOwnership(principal, eventId, categoryId);
        //
        configurationManager.saveCategoryConfiguration(categoryId, eventId, input.values().stream().flatMap(Collection::stream).toList(), principal.getName());
        return true;
    }

    @GetMapping(value = "/events/{eventId}/categories/{categoryId}/load")
    public Map<ConfigurationKeys.SettingCategory, List<Configuration>> loadCategoryConfiguration(@PathVariable int eventId, @PathVariable int categoryId, Principal principal) {
        accessService.checkCategoryOwnership(principal, eventId, categoryId);
        return configurationManager.loadCategoryConfig(eventId, categoryId, principal.getName());
    }

    @DeleteMapping(value = "/organization/{organizationId}/key/{key}")
    public boolean deleteOrganizationLevelKey(@PathVariable int organizationId, @PathVariable ConfigurationKeys key, Principal principal) {
        accessService.checkOrganizationOwnership(principal, organizationId);
        configurationManager.deleteOrganizationLevelByKey(key.getValue(), organizationId, principal.getName());
        return true;
    }

    @DeleteMapping(value = "/event/{eventId}/key/{key}")
    public boolean deleteEventLevelKey(@PathVariable int eventId, @PathVariable ConfigurationKeys key, Principal principal) {
        accessService.checkEventOwnership(principal, eventId);
        configurationManager.deleteEventLevelByKey(key.getValue(), eventId, principal.getName());
        return true;
    }

    @DeleteMapping(value = "/event/{eventId}/category/{categoryId}/key/{key}")
    public boolean deleteCategoryLevelKey(@PathVariable int eventId, @PathVariable int categoryId, @PathVariable ConfigurationKeys key, Principal principal) {
        accessService.checkCategoryOwnership(principal, eventId, categoryId);
        configurationManager.deleteCategoryLevelByKey(key.getValue(), eventId, categoryId, principal.getName());
        return true;
    }

    @DeleteMapping(value = "/key/{key}")
    public boolean deleteKey(@PathVariable String key, Principal principal) {
        accessService.ensureAdmin(principal);
        configurationManager.deleteKey(key);
        return true;
    }

    @GetMapping(value = "/eu-countries")
    public List<Pair<String, String>> loadEUCountries() {
        return TicketHelper.getLocalizedEUCountriesForVat(Locale.ENGLISH, configurationManager.getForSystem(ConfigurationKeys.EU_COUNTRIES_LIST).getRequiredValue());
    }

    @GetMapping(value = "/instance-settings")
    public InstanceSettings loadInstanceSettings() {
        var settings = configurationManager.getFor(EnumSet.of(DESCRIPTION_MAXLENGTH, BASE_URL), ConfigurationLevel.system());
        return new InstanceSettings(settings.get(DESCRIPTION_MAXLENGTH).getValueAsIntOrDefault(4096), settings.get(BASE_URL).getRequiredValue());
    }

    @GetMapping(value = "/platform-mode/status/{organizationId}")
    public Map<String, Boolean> loadPlatformModeStatus(@PathVariable int organizationId, Principal principal) {
        accessService.checkOrganizationOwnership(principal, organizationId);
        Map<String, Boolean> result = new HashMap<>();
        boolean platformModeEnabled = configurationManager.getForSystem(PLATFORM_MODE_ENABLED).getValueAsBooleanOrDefault();
        result.put("enabled", platformModeEnabled);
        if(platformModeEnabled) {
            var options = configurationManager.getFor(List.of(STRIPE_CONNECTED_ID, MOLLIE_CONNECT_REFRESH_TOKEN), ConfigurationLevel.organization(organizationId));
            result.put("stripeConnected", options.get(STRIPE_CONNECTED_ID).isPresent());
            result.put("mollieConnected", options.get(MOLLIE_CONNECT_REFRESH_TOKEN).isPresent());
        }
        return result;
    }

    @GetMapping("/setting-categories")
    public Collection<ConfigurationKeys.SettingCategory> getSettingCategories() {
        return EnumSet.allOf(ConfigurationKeys.SettingCategory.class);
    }

    @GetMapping(value = "/event/{eventId}/invoice-first-date")
    public ResponseEntity<ZonedDateTime> getFirstInvoiceDate(@PathVariable Integer eventId, Principal principal) {
        accessService.checkEventOwnership(principal, eventId);
        return ResponseEntity.of(optionally(() -> eventManager.getSingleEventById(eventId, principal.getName()))
            .map(event -> billingDocumentManager.findFirstInvoiceDate(event.getId()).orElseGet(() -> ZonedDateTime.now(clockProvider.getClock().withZone(event.getZoneId())))));
    }

    @GetMapping(value = "/event/{eventId}/matching-invoices")
    public ResponseEntity<List<Integer>> getMatchingInvoicesForEvent(@PathVariable Integer eventId,
                                                                     @RequestParam("from") long fromInstant,
                                                                     @RequestParam("to") long toInstant,
                                                                     Principal principal) {
        accessService.checkEventOwnership(principal, eventId);
        var eventOptional = optionally(() -> eventManager.getSingleEventById(eventId, principal.getName()));
        if(eventOptional.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        var zoneId = eventOptional.get().getZoneId();
        var from = ZonedDateTime.ofInstant(Instant.ofEpochMilli(fromInstant), zoneId);
        var to = ZonedDateTime.ofInstant(Instant.ofEpochMilli(toInstant), zoneId);
        if(from.isAfter(to)) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(billingDocumentManager.findMatchingInvoiceIds(eventId, from, to));
    }

    @PostMapping(value = "/event/{eventId}/regenerate-invoices")
    public ResponseEntity<Boolean> regenerateInvoices(@PathVariable Integer eventId,
                                                      @RequestBody List<Long> documentIds,
                                                      Principal principal) {
        //
        accessService.checkBillingDocumentsOwnership(principal, eventId, documentIds);
        //
        if(!eventManager.eventExistsById(eventId) || documentIds.isEmpty()) {
            // implicit check done by the Row Level Security
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(adminJobManager.scheduleExecution(AdminJobExecutor.JobName.REGENERATE_INVOICES, Map.of(
            "username", principal.getName(),
            "eventId", eventId,
            "ids", documentIds.stream().map(String::valueOf).collect(Collectors.joining(","))
        )));
    }

    @PutMapping("/generate-tickets-for-subscriptions")
    public ResponseEntity<Boolean> generateTicketsForSubscriptions(@RequestParam(value = "eventId", required = false) Integer eventId,
                                                                   @RequestParam(value = "organizationId", required = false) Integer organizationId,
                                                                   Principal principal) {
        boolean admin = RequestUtils.isAdmin(principal);
        Map<String, Object> jobMetadata = null;

        if (!admin && (organizationId == null || !userManager.isOwnerOfOrganization(principal.getName(), organizationId))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (eventId != null && organizationId != null) {
            accessService.checkEventOwnership(principal, eventId, organizationId);
            jobMetadata = Map.of(AssignTicketToSubscriberJobExecutor.EVENT_ID, eventId, AssignTicketToSubscriberJobExecutor.ORGANIZATION_ID, organizationId);
        } else if (organizationId != null) {
            accessService.checkOrganizationOwnership(principal, organizationId);
            jobMetadata = Map.of(AssignTicketToSubscriberJobExecutor.ORGANIZATION_ID, organizationId);
        }

        return ResponseEntity.ok(adminJobManager.scheduleExecution(ASSIGN_TICKETS_TO_SUBSCRIBERS, requireNonNullElse(jobMetadata, Map.of())));
    }

    @GetMapping(value = "/global-translations-override")
    public Map<String, Map<String, String>> loadTranslations() {
        return configurationManager.getFor(TRANSLATION_OVERRIDE, ConfigurationLevel.system())
            .getValue()
            .map(v -> Json.fromJson(v, new TypeReference<Map<String, Map<String, String>>>() {}))
            .orElse(Map.of());
    }

    @GetMapping(value = "/organizations/{organizationId}/translations-override")
    public Map<String, Map<String, String>> loadTranslationsOverrideForOrganization(@PathVariable int organizationId, Principal principal) {
        accessService.checkOrganizationOwnership(principal, organizationId);
        return configurationManager.getFor(TRANSLATION_OVERRIDE, ConfigurationLevel.organization(organizationId))
            .getValue()
            .map(v -> Json.fromJson(v, new TypeReference<Map<String, Map<String, String>>>() {}))
            .orElse(Map.of());
    }

    @GetMapping(value = "/events/{eventId}/translations-override")
    public Map<String, Map<String, String>> loadTranslationsOverrideForEvent(@PathVariable int eventId, Principal principal) {
        var eventAndOrgId = accessService.checkEventOwnership(principal, eventId);
        return configurationManager.getFor(TRANSLATION_OVERRIDE, ConfigurationLevel.event(eventAndOrgId))
            .getValue()
            .map(v -> Json.fromJson(v, new TypeReference<Map<String, Map<String, String>>>() {}))
            .orElse(Map.of());
    }

    @PostMapping("/organizations/{organizationId}/payment-method")
    public ResponseEntity<String> createPaymentMethod(
        @PathVariable int organizationId,
        @RequestBody UserDefinedOfflinePaymentMethod paymentMethod,
        Principal principal
    ) throws CustomOfflinePaymentMethodAlreadyExistsException {
        accessService.checkOrganizationOwnership(principal, organizationId);

        try {
            customOfflineConfigurationManager.createOrganizationCustomOfflinePaymentMethod(organizationId, paymentMethod);
        } catch(CustomOfflinePaymentMethodAlreadyExistsException e) {
            throw new CustomOfflinePaymentMethodAlreadyExistsException(
                "A payment method with the passed ID already exists",
                e
            );
        }

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/organizations/{organizationId}/payment-method")
    public ResponseEntity<List<UserDefinedOfflinePaymentMethod>> getPaymentMethodsForOrganization(
        @PathVariable int organizationId,
        @RequestParam(required = false, defaultValue = "false") boolean includeDeleted,
        Principal principal
    ) {
        accessService.checkOrganizationOwnership(principal, organizationId);

        List<UserDefinedOfflinePaymentMethod> paymentMethods;
        if(includeDeleted) {
            paymentMethods = customOfflineConfigurationManager.getOrganizationCustomOfflinePaymentMethodsIncludingDeleted(
                organizationId
            );
        } else {
            paymentMethods = customOfflineConfigurationManager.getOrganizationCustomOfflinePaymentMethods(organizationId);
        }

        return ResponseEntity.ok(paymentMethods);
    }

    @PutMapping("/organizations/{organizationId}/payment-method/{paymentMethodId}")
    public ResponseEntity<String> updatePaymentMethod(
        @PathVariable int organizationId,
        @PathVariable String paymentMethodId,
        @RequestBody UserDefinedOfflinePaymentMethod paymentMethod,
        Principal principal
    ) throws CustomOfflinePaymentMethodDoesNotExistException {
        accessService.checkOrganizationOwnership(principal, organizationId);

        try {
            customOfflineConfigurationManager.updateOrganizationCustomOfflinePaymentMethod(organizationId, paymentMethod);
        } catch(CustomOfflinePaymentMethodDoesNotExistException e) {
            throw new CustomOfflinePaymentMethodDoesNotExistException(
                "Payment method with passed ID does not exist.", e
            );
        }

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @DeleteMapping("/organizations/{organizationId}/payment-method/{paymentMethodId}")
    public ResponseEntity<String> deletePaymentMethod(
        @PathVariable int organizationId,
        @PathVariable String paymentMethodId,
        Principal principal
    ) throws CustomOfflinePaymentMethodDoesNotExistException, CustomOfflinePaymentMethodIllegalDeletionStateException {
        accessService.checkOrganizationOwnership(principal, organizationId);

        UserDefinedOfflinePaymentMethod existingPaymentMethod;
        try {
            existingPaymentMethod = customOfflineConfigurationManager.getOrganizationCustomOfflinePaymentMethodById(
                organizationId,
                paymentMethodId
            );
        } catch (CustomOfflinePaymentMethodDoesNotExistException e) {
            throw new CustomOfflinePaymentMethodDoesNotExistException(
                "Payment method with passed ID does not exist.", e
            );
        }

        var isPaymentMethodActivelyUsed = customOfflineConfigurationManager
            .isPaymentMethodCurrentlyUsedInActiveEvent(organizationId, existingPaymentMethod);

        if(isPaymentMethodActivelyUsed) {
            throw new CustomOfflinePaymentMethodIllegalDeletionStateException(
                "You cannot delete a payment method which is currently in use by an active event."
            );
        }

        try {
            customOfflineConfigurationManager.deleteOrganizationCustomOfflinePaymentMethod(
                organizationId,
                existingPaymentMethod
            );
        } catch (CustomOfflinePaymentMethodDoesNotExistException e) {
            throw new CustomOfflinePaymentMethodDoesNotExistException(
                "Payment method with passed ID does not exist.", e
            );
        }

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/event/{eventId}/payment-method")
    public ResponseEntity<List<UserDefinedOfflinePaymentMethod>> getAllowedPaymentMethodsForEvent(
        @PathVariable Integer eventId,
        Principal principal
    ) throws PassedIdDoesNotExistException {
        var eventAndOrgId = accessService.checkEventOwnership(principal, eventId);

        var event = eventRepository.findById(eventAndOrgId.getId());
        if(event == null) {
            throw new PassedIdDoesNotExistException("Passed event ID does not exist.");
        }

        var eventSelectedMethods = customOfflineConfigurationManager.getAllowedCustomOfflinePaymentMethodsForEvent(event);
        return ResponseEntity.ok(eventSelectedMethods);
    }

    @PostMapping(value = "/event/{eventId}/payment-method")
    public ResponseEntity<Boolean> setEventAllowedPaymentMethods(
        @PathVariable Integer eventId,
        @RequestBody List<String> paymentMethodIds,
        Principal principal
    ) throws PassedIdDoesNotExistException, CustomOfflinePaymentMethodDoesNotExistException {
        var eventAndOrgId = accessService.checkEventOwnership(principal, eventId);

        var event = eventRepository.findById(eventAndOrgId.getId());
        if(event == null) {
            throw new PassedIdDoesNotExistException("Event matching passed ID does not exist.");
        }

        try {
            customOfflineConfigurationManager.setAllowedCustomOfflinePaymentMethodsForEvent(
                event,
                paymentMethodIds
            );
        } catch (CustomOfflinePaymentMethodDoesNotExistException e) {
            throw new CustomOfflinePaymentMethodDoesNotExistException(
                "One or more of the passed payment methods IDs do not exist.",
                e
            );
        }

        return ResponseEntity.ok(true);
    }

    @ExceptionHandler({
        CustomOfflinePaymentMethodAlreadyExistsException.class,
        CustomOfflinePaymentMethodDoesNotExistException.class,
        CustomOfflinePaymentMethodIllegalDeletionStateException.class,
        PassedIdDoesNotExistException.class
    })
    public ResponseEntity<String> handleResponseException(RuntimeException ex) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.TEXT_PLAIN)
            .body(ex.getMessage());
    }

    @Data
    static class OrganizationConfig {
        private final Organization organization;
        private final Map<ConfigurationKeys.SettingCategory, List<Configuration>> config;
    }

    @Data
    static class InstanceSettings {
        private final int descriptionMaxLength;
        private final String baseUrl;
    }
}
