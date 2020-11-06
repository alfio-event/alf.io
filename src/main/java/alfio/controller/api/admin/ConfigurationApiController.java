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
import alfio.manager.BillingDocumentManager;
import alfio.manager.EventManager;
import alfio.manager.MultipartFileSender;
import alfio.manager.system.AdminJobExecutor;
import alfio.manager.system.AdminJobManager;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.model.metadata.VideoFile;
import alfio.model.modification.ConfigurationModification;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.model.user.Organization;
import alfio.util.ClockProvider;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.security.Principal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static alfio.model.system.ConfigurationKeys.*;
import static alfio.util.Wrappers.optionally;

@RestController
@RequestMapping("/admin/api/configuration")
@AllArgsConstructor
public class ConfigurationApiController {

    private final ConfigurationManager configurationManager;
    private final BillingDocumentManager billingDocumentManager;
    private final AdminJobManager adminJobManager;
    private final EventManager eventManager;
    private final ClockProvider clockProvider;

    @GetMapping(value = "/load")
    public Map<ConfigurationKeys.SettingCategory, List<Configuration>> loadConfiguration(Principal principal) {
        return configurationManager.loadAllSystemConfigurationIncludingMissing(principal.getName());
    }

    @GetMapping("/basic-configuration-needed")
    public boolean isBasicConfigurationNeeded() {
        return configurationManager.isBasicConfigurationNeeded();
    }

    @PostMapping(value = "/update")
    public boolean updateConfiguration(@RequestBody ConfigurationModification configuration) {
        configurationManager.saveSystemConfiguration(ConfigurationKeys.fromString(configuration.getKey()), configuration.getValue());
        return true;
    }

    @PostMapping(value = "/update-bulk")
    public boolean updateConfiguration(@RequestBody Map<ConfigurationKeys.SettingCategory, List<ConfigurationModification>> input) {
        List<ConfigurationModification> list = Objects.requireNonNull(input).values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        configurationManager.saveAllSystemConfiguration(list);
        return true;
    }

    @GetMapping(value = "/organizations/{organizationId}/load")
    public Map<ConfigurationKeys.SettingCategory, List<Configuration>> loadOrganizationConfiguration(@PathVariable("organizationId") int organizationId, Principal principal) {
        return configurationManager.loadOrganizationConfig(organizationId, principal.getName());
    }

    @PostMapping(value = "/organizations/{organizationId}/update")
    public boolean updateOrganizationConfiguration(@PathVariable("organizationId") int organizationId,
                                                                     @RequestBody Map<ConfigurationKeys.SettingCategory, List<ConfigurationModification>> input, Principal principal) {
        configurationManager.saveAllOrganizationConfiguration(organizationId, input.values().stream().flatMap(Collection::stream).collect(Collectors.toList()), principal.getName());
        return true;
    }

    @GetMapping(value = "/events/{eventId}/load")
    public Map<ConfigurationKeys.SettingCategory, List<Configuration>> loadEventConfiguration(@PathVariable("eventId") int eventId,
                                                                                              Principal principal) {
        return configurationManager.loadEventConfig(eventId, principal.getName());
    }

    @GetMapping("/events/{eventId}/single/{key}")
    public ResponseEntity<String> getSingleConfigForEvent(@PathVariable("eventId") int eventId,
                                                         @PathVariable("key") String key,
                                                         Principal principal) {

        String singleConfigForEvent = configurationManager.getSingleConfigForEvent(eventId, key, principal.getName());
        if(singleConfigForEvent == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(singleConfigForEvent);
    }

    @PostMapping(value = "/organizations/{organizationId}/events/{eventId}/update")
    public boolean updateEventConfiguration(@PathVariable("organizationId") int organizationId, @PathVariable("eventId") int eventId,
                                                    @RequestBody Map<ConfigurationKeys.SettingCategory, List<ConfigurationModification>> input, Principal principal) {
        configurationManager.saveAllEventConfiguration(eventId, organizationId, input.values().stream().flatMap(Collection::stream).collect(Collectors.toList()), principal.getName());
        return true;
    }

    @PostMapping(value = "/events/{eventId}/categories/{categoryId}/update")
    public boolean updateCategoryConfiguration(@PathVariable("categoryId") int categoryId, @PathVariable("eventId") int eventId,
                                                    @RequestBody Map<ConfigurationKeys.SettingCategory, List<ConfigurationModification>> input, Principal principal) {
        configurationManager.saveCategoryConfiguration(categoryId, eventId, input.values().stream().flatMap(Collection::stream).collect(Collectors.toList()), principal.getName());
        return true;
    }

    @GetMapping(value = "/events/{eventId}/categories/{categoryId}/load")
    public Map<ConfigurationKeys.SettingCategory, List<Configuration>> loadCategoryConfiguration(@PathVariable("eventId") int eventId, @PathVariable("categoryId") int categoryId, Principal principal) {
        return configurationManager.loadCategoryConfig(eventId, categoryId, principal.getName());
    }

    @DeleteMapping(value = "/organization/{organizationId}/key/{key}")
    public boolean deleteOrganizationLevelKey(@PathVariable("organizationId") int organizationId, @PathVariable("key") ConfigurationKeys key, Principal principal) {
        configurationManager.deleteOrganizationLevelByKey(key.getValue(), organizationId, principal.getName());
        return true;
    }

    @DeleteMapping(value = "/event/{eventId}/key/{key}")
    public boolean deleteEventLevelKey(@PathVariable("eventId") int eventId, @PathVariable("key") ConfigurationKeys key, Principal principal) {
        configurationManager.deleteEventLevelByKey(key.getValue(), eventId, principal.getName());
        return true;
    }

    @DeleteMapping(value = "/event/{eventId}/category/{categoryId}/key/{key}")
    public boolean deleteCategoryLevelKey(@PathVariable("eventId") int eventId, @PathVariable("categoryId") int categoryId, @PathVariable("key") ConfigurationKeys key, Principal principal) {
        configurationManager.deleteCategoryLevelByKey(key.getValue(), eventId, categoryId, principal.getName());
        return true;
    }

    @DeleteMapping(value = "/key/{key}")
    public boolean deleteKey(@PathVariable("key") String key) {
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
    public Map<String, Boolean> loadPlatformModeStatus(@PathVariable("organizationId") int organizationId) {
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
    public ResponseEntity<ZonedDateTime> getFirstInvoiceDate(@PathVariable("eventId") Integer eventId, Principal principal) {
        return ResponseEntity.of(optionally(() -> eventManager.getSingleEventById(eventId, principal.getName()))
            .map(event -> billingDocumentManager.findFirstInvoiceDate(event.getId()).orElseGet(() -> ZonedDateTime.now(clockProvider.getClock().withZone(event.getZoneId())))));
    }

    @GetMapping(value = "/event/{eventId}/matching-invoices")
    public ResponseEntity<List<Integer>> getMatchingInvoicesForEvent(@PathVariable("eventId") Integer eventId,
                                                                     @RequestParam("from") long fromInstant,
                                                                     @RequestParam("to") long toInstant,
                                                                     Principal principal) {
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
    public ResponseEntity<Boolean> regenerateInvoices(@PathVariable("eventId") Integer eventId,
                                                      @RequestBody List<Long> documentIds,
                                                      Principal principal) {
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
