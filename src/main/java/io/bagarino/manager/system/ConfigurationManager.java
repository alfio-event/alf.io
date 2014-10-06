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
import io.bagarino.repository.system.ConfigurationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ConfigurationManager {

    private final ConfigurationRepository configurationRepository;

    @Autowired
    public ConfigurationManager(ConfigurationRepository configurationRepository) {
        this.configurationRepository = configurationRepository;
    }

    public int getIntConfigValue(String key, int defaultValue) {
        try {
            Optional<String> value = Optional.ofNullable(configurationRepository.findByKey(key))
                    .map(Configuration::getValue);
            if(value.isPresent()) {
                return Integer.parseInt(value.get());
            }
            return defaultValue;
        } catch (NumberFormatException | EmptyResultDataAccessException e) {
            return defaultValue;
        }
    }
}
