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

import alfio.config.Initializer;
import alfio.manager.RecaptchaService;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.util.TemplateManager;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;
import org.springframework.session.web.http.CookieSerializer;

import javax.sql.DataSource;

/**
 * Default form based configuration.
 */
@Profile("!"+ Initializer.PROFILE_OPENID)
@Configuration(proxyBeanMethods = false)
@Order(100)
public class FormBasedWebSecurity extends AbstractFormBasedWebSecurity {
    public FormBasedWebSecurity(Environment environment,
                                UserManager userManager,
                                RecaptchaService recaptchaService,
                                ConfigurationManager configurationManager,
                                CsrfTokenRepository csrfTokenRepository,
                                DataSource dataSource,
                                PasswordEncoder passwordEncoder,
                                SpringSessionBackedSessionRegistry<?> sessionRegistry,
                                OpenIdUserSynchronizer openIdUserSynchronizer,
                                CookieSerializer cookieSerializer,
                                TemplateManager templateManager) {
        super(environment,
            userManager,
            recaptchaService,
            configurationManager,
            csrfTokenRepository,
            dataSource,
            passwordEncoder,
            sessionRegistry,
            openIdUserSynchronizer,
            cookieSerializer,
            templateManager);
    }
}
