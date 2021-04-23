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

import alfio.config.authentication.support.OpenIdAuthenticationFilter;
import alfio.config.authentication.support.OpenIdCallbackLoginFilter;
import alfio.config.authentication.support.OpenIdPublicAuthenticationFilter;
import alfio.manager.RecaptchaService;
import alfio.manager.openid.AdminOpenIdAuthenticationManager;
import alfio.manager.openid.PublicOpenIdAuthenticationManager;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import javax.sql.DataSource;

@Profile("openid")
@Configuration
@Order(1)
@Log4j2
public class OpenIdAdminWebSecurity extends AbstractFormBasedWebSecurity {

    private final AdminOpenIdAuthenticationManager adminOpenIdAuthenticationManager;

    public OpenIdAdminWebSecurity(Environment environment,
                                  UserManager userManager,
                                  RecaptchaService recaptchaService,
                                  ConfigurationManager configurationManager,
                                  CsrfTokenRepository csrfTokenRepository,
                                  DataSource dataSource,
                                  PasswordEncoder passwordEncoder,
                                  AdminOpenIdAuthenticationManager adminOpenIdAuthenticationManager,
                                  PublicOpenIdAuthenticationManager openIdAuthenticationManager) {
        super(environment,
            userManager,
            recaptchaService,
            configurationManager,
            csrfTokenRepository,
            dataSource,
            passwordEncoder,
            openIdAuthenticationManager);
        this.adminOpenIdAuthenticationManager = adminOpenIdAuthenticationManager;
    }

    @Override
    protected void addAdditionalFilters(HttpSecurity http) throws Exception {
        var callbackLoginFilter = new OpenIdCallbackLoginFilter(adminOpenIdAuthenticationManager,
            new AntPathRequestMatcher("/callback", "GET"),
            authenticationManager());
        http.addFilterBefore(callbackLoginFilter, UsernamePasswordAuthenticationFilter.class);
        log.trace("adding openid filter");
        http.addFilterAfter(new OpenIdAuthenticationFilter("/authentication", adminOpenIdAuthenticationManager, "/"), OpenIdPublicAuthenticationFilter.class);
    }
}
