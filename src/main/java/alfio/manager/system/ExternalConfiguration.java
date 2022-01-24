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
package alfio.manager.system;

import alfio.config.Initializer;
import alfio.manager.support.extension.ExtensionCapability;
import alfio.manager.support.extension.ExtensionEvent;
import alfio.model.ExtensionCapabilitySummary;
import alfio.model.ExtensionCapabilitySummary.ExtensionCapabilityDetails;
import alfio.model.ExtensionSupport;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeyValuePathLevel;
import alfio.model.system.ConfigurationPathLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNullElse;
import static java.util.stream.Collectors.*;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
@Profile("!"+ Initializer.PROFILE_INTEGRATION_TEST)
@ConfigurationProperties("alfio.override.system")
@Getter
@Setter
public class ExternalConfiguration {
    private static final String EXTERNAL_EXTENSION_PATH = "::EXTERNAL::";
    private static final int EXTERNAL_CONFIGURATION_ID = Integer.MIN_VALUE;
    private Map<String, String> settings = new HashMap<>();
    private List<ExtensionOverride> extensions = new ArrayList<>();

    public List<Configuration> load(String key) {
        return getSingle(key).map(List::of).orElse(List.of());
    }

    public Optional<Configuration> getSingle(String key) {
        return Optional.ofNullable(settings.get(key)).map(value -> new Configuration(EXTERNAL_CONFIGURATION_ID, key, value, ConfigurationPathLevel.EXTERNAL));
    }

    public List<ConfigurationKeyValuePathLevel> getAll(Collection<String> keys) {
        return keys.stream()
            .map(this::getSingle)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(c -> new ConfigurationKeyValuePathLevel(c.getKey(), c.getValue(), c.getConfigurationPathLevel()))
            .collect(Collectors.toList());
    }

    public Optional<String> getScript(String path, String name) {
        if(EXTERNAL_EXTENSION_PATH.equals(path)) {
            return extensions.stream().filter(e -> e.isValid() && e.id.equals(name))
                .map(ExtensionOverride::getContent)
                .findFirst();
        }
        return Optional.empty();
    }

    public List<ExtensionSupport.ScriptPathNameHash> getAllExtensionsFor(String event, boolean async) {
        return extensions.stream()
            .filter(e -> e.async == async && e.isValid() && e.getEvents().contains(event))
            .map(e -> new ExtensionSupport.ScriptPathNameHash(EXTERNAL_EXTENSION_PATH, e.getId(), DigestUtils.sha256Hex(e.file)))
            .collect(Collectors.toList());
    }

    public List<ExtensionSupport.ScriptPathNameHash> getAllExtensionsForCapability(ExtensionCapability capability) {
        var eventsAsString = capability.getCompatibleEvents().stream()
            .map(ExtensionEvent::name).collect(Collectors.toList());
        return extensions.stream()
            .filter(e -> e.isValid() && CollectionUtils.containsAny(e.events, eventsAsString) && e.getCapabilities().contains(capability.name()))
            .map(e -> new ExtensionSupport.ScriptPathNameHash(EXTERNAL_EXTENSION_PATH, e.getId(), DigestUtils.sha256Hex(e.file)))
            .collect(Collectors.toList());
    }

    public Set<ExtensionCapabilitySummary> getSupportedCapabilities(Set<ExtensionCapability> requested) {
        return extensions.stream()
            .map(e -> Pair.of(e, e.getCapabilityDetails().stream().filter(cd -> requested.contains(cd.key)).collect(Collectors.toList())))
            .filter(p -> !p.getRight().isEmpty())
            .flatMap(p -> {
                Map<ExtensionCapability, List<ExtensionCapabilityDetails>> byCapability = p.getRight().stream()
                    .collect(groupingBy(ExtensionCapabilityDetailsOverride::getKey, mapping(cd -> new ExtensionCapabilityDetails(cd.label, cd.description, cd.selector), toList())));
                return byCapability.entrySet().stream()
                    .map(e -> new ExtensionCapabilitySummary(e.getKey(), e.getValue()));
            }).collect(Collectors.toSet());
    }

    public Map<String, String> getParametersForExtension(String id) {
        return extensions.stream().filter(ExtensionOverride::isValid)
            .filter(extensionOverride -> extensionOverride.id.equals(id))
            .map(ExtensionOverride::getParams)
            .findFirst()
            .orElse(Map.of());
    }

    @Data
    public static class ExtensionOverride {
        private String id;
        private String file;
        private List<String> events;
        private boolean async;
        private Map<String, String> params;
        private String type = "plain"; // plain or base64
        private List<ExtensionCapabilityDetailsOverride> capabilityDetails;

        boolean isValid() {
            return isNotBlank(id)
                && isNotBlank(file)
                && events != null && !events.isEmpty();
        }

        Map<String, String> getParams() {
            return requireNonNullElse(params, Map.of());
        }

        String getContent() {
            if("base64".equals(type) && isNotBlank(file)) {
                return new String(Base64.getDecoder().decode(file), StandardCharsets.UTF_8);
            }
            return file;
        }

        Set<String> getCapabilities() {
            return getCapabilityDetails().stream()
                .map(ec -> ec.key.name())
                .collect(Collectors.toSet());
        }

        List<ExtensionCapabilityDetailsOverride> getCapabilityDetails() {
            return requireNonNullElse(capabilityDetails, List.of());
        }
    }

    @Data
    public static class ExtensionCapabilityDetailsOverride {
        private ExtensionCapability key;
        private String label;
        private String description;
        private String selector;
    }

    public static boolean isExternalPath(String path) {
        return EXTERNAL_EXTENSION_PATH.equals(path);
    }


}
