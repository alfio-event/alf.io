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
/**
 * This file is part of alf.io.
 * <p>
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio;

import alfio.manager.system.ConfigurationManager;
import alfio.manager.system.ExternalConfiguration;
import alfio.manager.user.UserManager;
import alfio.model.system.ConfigurationKeys;
import alfio.repository.EventRepository;
import alfio.repository.PromoCodeDiscountRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.test.toolkit.PromoCodeDiscountIntegrationTestingToolkit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.session.FindByIndexNameSessionRepository;

import java.time.Duration;
import java.util.Map;
import java.util.Set;


@Configuration(proxyBeanMethods = false)
@Import(BaseTestConfiguration.class)
public class TestConfiguration {

    @Bean
    ConfigurationManager configurationManager(ConfigurationRepository configurationRepository,
                                              UserManager userManager,
                                              EventRepository eventRepository,
                                              ExternalConfiguration externalConfiguration,
                                              Environment environment,
                                              ObjectMapper objectMapper) {
        Cache<Set<ConfigurationKeys>, Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration>> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ZERO)
            .build();
        return new ConfigurationManager(configurationRepository,
            userManager,
            eventRepository,
            externalConfiguration,
            environment,
            cache,
            objectMapper);
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return new CookieCsrfTokenRepository();
    }

    @Bean
    FindByIndexNameSessionRepository<?> sessionsByPrincipalFinder() {
        return Mockito.mock(FindByIndexNameSessionRepository.class);
    }

    @Bean
    public PromoCodeDiscountIntegrationTestingToolkit promoCodeDiscountIntegrationTestingToolkit(final PromoCodeDiscountRepository promoCodeDiscountRepository, final NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        return new PromoCodeDiscountIntegrationTestingToolkit(promoCodeDiscountRepository, namedParameterJdbcTemplate);
    }
}
