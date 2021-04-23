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

import alfio.manager.openid.AdminOpenIdAuthenticationManager;
import alfio.manager.openid.PublicOpenIdAuthenticationManager;
import alfio.manager.system.ConfigurationManager;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

import java.net.http.HttpClient;

@Configuration
@EnableWebSecurity
@Log4j2
public class WebSecurityConfig {

    public static final String CSRF_PARAM_NAME = "_csrf";
    private static final String CSRF_SESSION_ATTRIBUTE = "CSRF_SESSION_ATTRIBUTE";

    @Bean
    public CsrfTokenRepository getCsrfTokenRepository() {
        HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
        repository.setSessionAttributeName(CSRF_SESSION_ATTRIBUTE);
        repository.setParameterName(CSRF_PARAM_NAME);
        return repository;
    }

    @Bean
    @Profile("openid")
    public AdminOpenIdAuthenticationManager adminOpenIdAuthenticationManager(Environment environment,
                                                                             HttpClient httpClient,
                                                                             ConfigurationManager configurationManager) {
        return new AdminOpenIdAuthenticationManager(environment, httpClient, configurationManager);
    }

    @Bean
    public PublicOpenIdAuthenticationManager publicOpenIdAuthenticationManager(HttpClient httpClient,
                                                                               ConfigurationManager configurationManager) {
        return new PublicOpenIdAuthenticationManager(httpClient, configurationManager);
    }

}
