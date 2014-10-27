/**
 * This file is part of bagarino.
 *
 * bagarino is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bagarino is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.bagarino.manager.system;

import io.bagarino.model.system.Configuration;
import io.bagarino.model.system.ConfigurationKeys;
import io.bagarino.repository.system.ConfigurationRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.bagarino.util.OptionalWrapper.optionally;

@Component
@Transactional
public class ConfigurationManager {

    private final ConfigurationRepository configurationRepository;

    @Autowired
    public ConfigurationManager(ConfigurationRepository configurationRepository) {
        this.configurationRepository = configurationRepository;
    }

    public int getIntConfigValue(ConfigurationKeys key, int defaultValue) {
        try {
            Optional<String> value = Optional.ofNullable(configurationRepository.findByKey(key.getValue()))
                    .map(Configuration::getValue);
            if(value.isPresent()) {
                return Integer.parseInt(value.get());
            }
            return defaultValue;
        } catch (NumberFormatException | EmptyResultDataAccessException e) {
            return defaultValue;
        }
    }

    public boolean getBooleanConfigValue(String key, boolean defaultValue) {
        return optionally(() -> Boolean.parseBoolean(configurationRepository.findByKey(key).getValue()))
                .orElse(defaultValue);
    }

    public void save(ConfigurationKeys key, String value) {
        Optional<Configuration> conf = optionally(() -> configurationRepository.findByKey(key.getValue()));
        if(!conf.isPresent()) {
            configurationRepository.insert(key.getValue(), value, key.getDescription());
        } else {
            configurationRepository.update(key.getValue(), value);
        }
    }

    public String getStringConfigValue(ConfigurationKeys key, String defaultValue) {
        return optionally(() -> configurationRepository.findByKey(key.getValue()))
                .map(Configuration::getValue)
                .orElse(defaultValue);
    }
    
    public Optional<String> getStringConfigValue(ConfigurationKeys key) {
    	return optionally(() -> configurationRepository.findByKey(key.getValue())).map(Configuration::getValue);
    }

    public String getRequiredValue(ConfigurationKeys key) {
        return optionally(() -> configurationRepository.findByKey(key.getValue()))
                .map(Configuration::getValue)
                .orElseThrow(IllegalArgumentException::new);
    }

    public List<Configuration> loadAllIncludingMissing() {
        final List<Configuration> existing = configurationRepository.findAll()
                .stream()
                .filter(c -> !ConfigurationKeys.fromValue(c.getKey()).isInternal())
                .collect(Collectors.toList());
        final List<Configuration> missing = Arrays.stream(ConfigurationKeys.visible())
                .filter(k -> existing.stream().noneMatch(c -> c.getKey().equals(k.getValue())))
                .map(k -> new Configuration(-1, k.getValue(), null, k.getDescription()))
                .collect(Collectors.toList());
        List<Configuration> result = new LinkedList<>(existing);
        result.addAll(missing);
        result.sort(Comparator.comparing(Configuration::getKey));
        return result;
    }

	public void deleteKey(String key) {
		configurationRepository.deleteByKey(key);
	}
}
