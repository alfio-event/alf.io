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
package alfio.config;

import alfio.manager.system.ConfigurationManager;
import alfio.manager.system.ExternalConfiguration;
import alfio.manager.user.UserManager;
import alfio.model.system.ConfigurationKeys;
import alfio.repository.EventRepository;
import alfio.repository.system.ConfigurationRepository;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class BaseConfiguration {

    @Bean
    ConfigurationManager configurationManager(ConfigurationRepository configurationRepository,
                                              UserManager userManager,
                                              EventRepository eventRepository,
                                              ExternalConfiguration externalConfiguration,
                                              Environment environment) {
        Cache<Set<ConfigurationKeys>, Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration>> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .build();
        return new ConfigurationManager(configurationRepository,
            userManager,
            eventRepository,
            externalConfiguration,
            environment,
            cache);
    }

    @Bean
    ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return mapper;
    }
}
