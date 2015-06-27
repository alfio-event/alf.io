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
import alfio.model.system.Configuration.ConfigurationPath;
import alfio.model.system.ConfigurationKeys;
import alfio.repository.system.ConfigurationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static alfio.util.OptionalWrapper.optionally;

@Component
@Transactional
public class ConfigurationManager {

    private final ConfigurationRepository configurationRepository;

    @Autowired
    public ConfigurationManager(ConfigurationRepository configurationRepository) {
        this.configurationRepository = configurationRepository;
    }

    private Configuration findByConfigurationPathAndKey(ConfigurationPath path, ConfigurationKeys key) {
        return configurationRepository.findByKey(key.getValue());
    }

    public int getIntConfigValue(ConfigurationPath path, ConfigurationKeys key, int defaultValue) {
        try {
            return Optional.ofNullable(findByConfigurationPathAndKey(path, key))
                    .map(Configuration::getValue)
                    .map(Integer::parseInt).orElse(defaultValue);
        } catch (NumberFormatException | EmptyResultDataAccessException e) {
            return defaultValue;
        }
    }

    public boolean getBooleanConfigValue(ConfigurationPath path, ConfigurationKeys key, boolean defaultValue) {
        return optionally(() -> Boolean.parseBoolean(findByConfigurationPathAndKey(path, key).getValue()))
                .orElse(defaultValue);
    }


    public String getStringConfigValue(ConfigurationPath path, ConfigurationKeys key, String defaultValue) {
        return optionally(() -> findByConfigurationPathAndKey(path, key))
                .map(Configuration::getValue)
                .orElse(defaultValue);
    }
    
    public Optional<String> getStringConfigValue(ConfigurationPath path, ConfigurationKeys key) {
    	return optionally(() -> findByConfigurationPathAndKey(path, key)).map(Configuration::getValue);
    }

    public String getRequiredValue(ConfigurationPath path, ConfigurationKeys key) {
        return optionally(() -> findByConfigurationPathAndKey(path, key))
                .map(Configuration::getValue)
                .orElseThrow(() -> new IllegalArgumentException("Mandatory configuration key " + key + " not present"));
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


    public Map<ConfigurationKeys.SettingCategory, List<Configuration>> loadAllSystemConfigurationIncludingMissing() {
        final List<Configuration> existing = configurationRepository.findAll()
                .stream()
                .filter(c -> !ConfigurationKeys.fromValue(c.getKey()).isInternal())
                .collect(Collectors.toList());
        final List<Configuration> missing = Arrays.stream(ConfigurationKeys.visible())
                .filter(k -> existing.stream().noneMatch(c -> c.getKey().equals(k.getValue())))
                .map(k -> new Configuration(-1, k.getValue(), null, k.getDescription(), Configuration.ConfigurationPathLevel.SYSTEM))
                .collect(Collectors.toList());
        List<Configuration> result = new LinkedList<>(existing);
        result.addAll(missing);
        return result.stream().collect(Collectors.groupingBy(c -> c.getConfigurationKey().getCategory()));
    }

	public void deleteKey(String key) {
		configurationRepository.deleteByKey(key);
	}
}
