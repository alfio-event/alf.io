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
package alfio.controller.api.v1.admin;

import alfio.manager.AccessService;
import alfio.manager.PurchaseContextManager;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.PurchaseContext;
import alfio.model.modification.ConfigurationModification;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.model.system.ConfigurationPathLevel;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/configuration")
public class ConfigurationApiV1Controller {

    private final ConfigurationManager configurationManager;
    private final AccessService accessService;
    private final PurchaseContextManager purchaseContextManager;
    private final UserManager userManager;

    public ConfigurationApiV1Controller(ConfigurationManager configurationManager,
                                        PurchaseContextManager purchaseContextManager,
                                        UserManager userManager,
                                        AccessService accessService) {
        this.configurationManager = configurationManager;
        this.accessService = accessService;
        this.purchaseContextManager = purchaseContextManager;
        this.userManager = userManager;
    }

    @PutMapping("/organization/{organizationId}")
    public ResponseEntity<String> saveConfigurationForOrganization(@PathVariable("organizationId") int organizationId,
                                                                   @RequestBody Map<String, String> configurationKeyValues,
                                                                   Principal principal) {
        accessService.checkOrganizationOwnership(principal, organizationId);
        var configurationKeys = configurationKeyValues.keySet().stream()
            .map(ConfigurationKeys::safeValueOf)
            .collect(Collectors.toSet());
        var validationErrorOptional = validateInput(ConfigurationPathLevel.ORGANIZATION, configurationKeyValues, configurationKeys);
        if (validationErrorOptional.isPresent()) {
            return validationErrorOptional.get();
        }
        var existingIds = configurationManager.loadOrganizationConfig(organizationId, principal.getName()).values().stream()
            .flatMap(List::stream)
            .filter(c -> configurationKeys.contains(c.getConfigurationKey()))
            .collect(Collectors.toMap(Configuration::getKey, Configuration::getId));
        var toSave = configurationKeyValues.entrySet().stream()
                .map(ckv -> new ConfigurationModification(existingIds.get(ckv.getKey()), ckv.getKey(), ckv.getValue()))
                .collect(Collectors.toList());
        configurationManager.saveAllOrganizationConfiguration(organizationId, toSave, principal.getName());
        return ResponseEntity.ok().body("OK");
    }

    @PutMapping("/organization/{organizationId}/{purchaseContextType}/{publicIdentifier}")
    public ResponseEntity<String> saveConfigurationForPurchaseContext(@PathVariable("organizationId") int organizationId,
                                                                      @PathVariable("purchaseContextType") PurchaseContext.PurchaseContextType purchaseContextType,
                                                                      @PathVariable("publicIdentifier") String publicIdentifier,
                                                                      @RequestBody Map<String, String> configurationKeyValues,
                                                                      Principal principal) {
        var configurationKeys = configurationKeyValues.keySet().stream()
            .map(ConfigurationKeys::safeValueOf)
            .collect(Collectors.toSet());

        accessService.checkOrganizationOwnership(principal, organizationId);

        var validationErrorOptional = validateInput(ConfigurationPathLevel.PURCHASE_CONTEXT, configurationKeyValues, configurationKeys);

        if (validationErrorOptional.isPresent()) {
            return validationErrorOptional.get();
        }

        var purchaseContextOptional = purchaseContextManager.findBy(purchaseContextType, publicIdentifier);

        if (purchaseContextOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        var purchaseContext = purchaseContextOptional.get();

        if (purchaseContext.getOrganizationId() != organizationId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (purchaseContext.ofType(PurchaseContext.PurchaseContextType.event)) {
            int eventId = ((Event) purchaseContext).getId();
            var existingIds = configurationManager.loadEventConfig(eventId, principal.getName()).values().stream()
                .flatMap(List::stream)
                .filter(c -> configurationKeys.contains(c.getConfigurationKey()))
                .collect(Collectors.toMap(Configuration::getKey, Configuration::getId));
            var toSave = configurationKeyValues.entrySet().stream()
                .map(ckv -> new ConfigurationModification(existingIds.get(ckv.getKey()), ckv.getKey(), ckv.getValue()))
                .collect(Collectors.toList());
            configurationManager.saveAllEventConfiguration(eventId, organizationId, toSave, principal.getName());
        } else {
            var sd = (SubscriptionDescriptor) purchaseContext;
            var existingIds = configurationManager.loadSubscriptionDescriptorConfig(sd, principal.getName()).values().stream()
                .flatMap(List::stream)
                .filter(c -> configurationKeys.contains(c.getConfigurationKey()))
                .collect(Collectors.toMap(Configuration::getKey, Configuration::getId));
            var toSave = configurationKeyValues.entrySet().stream()
                .map(ckv -> new ConfigurationModification(existingIds.get(ckv.getKey()), ckv.getKey(), ckv.getValue()))
                .collect(Collectors.toList());
            configurationManager.saveAllSubscriptionDescriptorConfiguration(sd, toSave, principal.getName());
        }


        return ResponseEntity.ok().body("OK");
    }

    static class ConfigurationKeyValue {

        private final ConfigurationKeys key;
        private final String value;

        @JsonCreator
        ConfigurationKeyValue(@JsonProperty("key") ConfigurationKeys key, @JsonProperty("value") String value) {
            this.key = key;
            this.value = value;
        }

        public ConfigurationKeys getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }
    }

    private Optional<ResponseEntity<String>> validateInput(ConfigurationPathLevel configurationPathLevel,
                                                           Map<String, String> configurationKeyValues,
                                                           Set<ConfigurationKeys> configurationKeys) {
        if (configurationKeys.size() != configurationKeyValues.size()) {
            return Optional.of(ResponseEntity.badRequest().body("Request contains duplicate keys"));
        }

        if (configurationKeys.contains(ConfigurationKeys.NOT_RECOGNIZED)) {
            return Optional.of(ResponseEntity.badRequest().body("Request contains unrecognized keys"));
        }

        if (configurationKeys.stream().anyMatch(c -> c.isInternal() || !c.supports(configurationPathLevel))) {
            return Optional.of(ResponseEntity.badRequest().body("Request contains internal settings"));
        }

        return Optional.empty();
    }
}
