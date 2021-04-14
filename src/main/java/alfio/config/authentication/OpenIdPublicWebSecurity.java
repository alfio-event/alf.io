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
package alfio.config.authentication;

import alfio.config.authentication.support.OpenIdAuthenticationProvider;
import alfio.config.authentication.support.OpenIdPublicAuthenticationFilter;
import alfio.config.authentication.support.OpenIdPublicCallbackLoginFilter;
import alfio.config.authentication.support.RecaptchaLoginFilter;
import alfio.manager.openid.PublicOpenIdAuthenticationManager;
import alfio.manager.system.ConfigurationManager;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.repository.user.join.UserOrganizationRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@Order(2)
@Log4j2
public class OpenIdPublicWebSecurity extends WebSecurityConfigurerAdapter {

    private final PublicOpenIdAuthenticationManager openIdAuthenticationManager;
    private final ConfigurationManager configurationManager;

    public OpenIdPublicWebSecurity(Environment environment,
                                   ConfigurationManager configurationManager,
                                   PublicOpenIdAuthenticationManager openIdAuthenticationManager,
                                   UserRepository userRepository,
                                   AuthorityRepository authorityRepository,
                                   UserOrganizationRepository userOrganizationRepository,
                                   OrganizationRepository organizationRepository) {
        this.openIdAuthenticationManager = openIdAuthenticationManager;
        this.configurationManager = configurationManager;
    }

    @Override
    public void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.authenticationProvider(new OpenIdAuthenticationProvider());
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        var callbackLoginFilter = new OpenIdPublicCallbackLoginFilter(configurationManager);
        http.addFilterBefore(callbackLoginFilter, UsernamePasswordAuthenticationFilter.class);
        log.trace("adding openid filter");
        http.addFilterBefore(new OpenIdPublicAuthenticationFilter(openIdAuthenticationManager), RecaptchaLoginFilter.class);
    }
}
