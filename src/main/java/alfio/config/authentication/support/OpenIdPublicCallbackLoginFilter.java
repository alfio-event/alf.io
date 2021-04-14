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
package alfio.config.authentication.support;

import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static alfio.model.system.ConfigurationKeys.OPENID_PUBLIC_ENABLED;

public class OpenIdPublicCallbackLoginFilter extends AbstractAuthenticationProcessingFilter {

    private final ConfigurationManager configurationManager;

    public OpenIdPublicCallbackLoginFilter(ConfigurationManager configurationManager) {
        super(new AntPathRequestMatcher("/public/callback", "GET"));
        this.configurationManager = configurationManager;
    }

    @Override
    protected boolean requiresAuthentication(HttpServletRequest request, HttpServletResponse response) {
        return configurationManager.getFor(OPENID_PUBLIC_ENABLED, ConfigurationLevel.system()).getValueAsBooleanOrDefault();
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException, IOException, ServletException {
        return null;
    }
}
