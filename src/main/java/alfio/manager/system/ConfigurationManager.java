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

import alfio.model.modification.ConfigurationModification;
import alfio.model.system.Configuration;
import alfio.model.system.Configuration.*;
import alfio.model.system.ConfigurationKeys;
import alfio.model.system.ConfigurationPathLevel;
import alfio.repository.system.ConfigurationRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static alfio.util.OptionalWrapper.optionally;

@Component
@Transactional
@Log4j2
public class ConfigurationManager {

    private final ConfigurationRepository configurationRepository;

    @Autowired
    public ConfigurationManager(ConfigurationRepository configurationRepository) {
        this.configurationRepository = configurationRepository;
    }

    //TODO: refactor, not the most beautiful code, find a better solution...
    private Configuration findByConfigurationPathAndKey(ConfigurationPath path, ConfigurationKeys key) {
        switch (path.pathLevel()) {
            case SYSTEM: return configurationRepository.findByKey(key.getValue());
            case ORGANIZATION: {
                OrganizationConfigurationPath o = from(path);
                return selectPath(configurationRepository.findByOrganizationAndKey(o.getId(), key.getValue()));
            }
            case EVENT: {
                EventConfigurationPath o = from(path);
                return selectPath(configurationRepository.findByEventAndKey(o.getOrganizationId(),
                        o.getId(), key.getValue()));
            }
            case TICKET_CATEGORY: {
                TicketCategoryConfigurationPath o = from(path);
                return selectPath(configurationRepository.findByTicketCategoryAndKey(o.getOrganizationId(),
                        o.getEventId(), o.getId(), key.getValue()));
            }
        }
        throw new IllegalStateException("Can't reach here");
    }

    /**
     * Select the most "precise" configuration in the given list.
     *
     * @param conf
     * @return
     */
    private Configuration selectPath(List<Configuration> conf) {
        return conf.size() == 1 ? conf.get(0) : conf.stream()
                .sorted(Comparator.comparing(Configuration::getConfigurationPathLevel).reversed())
                .findFirst().orElse(null);
    }

    //meh
    @SuppressWarnings("unchecked")
    private static <T> T from(ConfigurationPath c) {
        return (T) c;
    }

    public int getIntConfigValue(ConfigurationPathKey pathKey, int defaultValue) {
        try {
            return Optional.ofNullable(findByConfigurationPathAndKey(pathKey.getPath(), pathKey.getKey()))
                    .map(Configuration::getValue)
                    .map(Integer::parseInt).orElse(defaultValue);
        } catch (NumberFormatException | EmptyResultDataAccessException e) {
            return defaultValue;
        }
    }

    public boolean getBooleanConfigValue(ConfigurationPathKey pathKey, boolean defaultValue) {
        return getStringConfigValue(pathKey)
                .map(Boolean::parseBoolean)
                .orElse(defaultValue);
    }


    public String getStringConfigValue(ConfigurationPathKey pathKey, String defaultValue) {
        return getStringConfigValue(pathKey).orElse(defaultValue);
    }
    
    public Optional<String> getStringConfigValue(ConfigurationPathKey pathKey) {
        return optionally(() -> findByConfigurationPathAndKey(pathKey.getPath(), pathKey.getKey())).map(Configuration::getValue);
    }

    public String getRequiredValue(ConfigurationPathKey pathKey) {
        return getStringConfigValue(pathKey)
                .orElseThrow(() -> new IllegalArgumentException("Mandatory configuration key " + pathKey.getKey() + " not present"));
    }

    // begin SYSTEM related configuration methods

    public void saveAllSystemConfiguration(List<ConfigurationModification> list) {
        list.forEach(c -> saveSystemConfiguration(ConfigurationKeys.fromValue(c.getKey()), c.getValue()));
    }

    public void saveSystemConfiguration(ConfigurationKeys key, String value) {
        Optional<Configuration> conf = optionally(() -> findByConfigurationPathAndKey(Configuration.system(), key));
        Optional<String> valueOpt = Optional.ofNullable(value);
        if(!conf.isPresent()) {
            valueOpt.ifPresent(v -> configurationRepository.insert(key.getValue(), v, key.getDescription()));
        } else {
            configurationRepository.update(key.getValue(), value);
        }
    }

    /**
     * Checks if the basic options have been already configured:
     * <ul>
     *     <li>Google maps' api keys</li>
     *     <li>Base application URL</li>
     *     <li>E-Mail</li>
     * </ul>
     * @return {@code true} if there are missing options, {@code true} otherwise
     */
    public boolean isBasicConfigurationNeeded() {
        return ConfigurationKeys.basic().stream()
            .anyMatch(key -> {
                boolean absent = !configurationRepository.findOptionalByKey(key.getValue()).isPresent();
                if(absent) {
                    log.warn("cannot find a value for "+key.getValue());
                }
                return absent;
            });
    }


    public Map<ConfigurationKeys.SettingCategory, List<Configuration>> loadAllSystemConfigurationIncludingMissing() {
        final List<Configuration> existing = configurationRepository.findAll()
                .stream()
                .filter(c -> !ConfigurationKeys.fromValue(c.getKey()).isInternal())
                .collect(Collectors.toList());
        final List<Configuration> missing = Arrays.stream(ConfigurationKeys.visible())
                .filter(k -> existing.stream().noneMatch(c -> c.getKey().equals(k.getValue())))
                .map(k -> new Configuration(-1, k.getValue(), null, k.getDescription(), ConfigurationPathLevel.SYSTEM))
                .collect(Collectors.toList());
        List<Configuration> result = new LinkedList<>(existing);
        result.addAll(missing);
        return result.stream().collect(Collectors.groupingBy(c -> c.getConfigurationKey().getCategory()));
    }

    public void deleteKey(String key) {
        configurationRepository.deleteByKey(key);
    }
}
