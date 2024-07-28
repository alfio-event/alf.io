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

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import static alfio.config.authentication.support.UserProvidedClientRegistrationRepository.PUBLIC_REGISTRATION_ID;

public class PublicOpenIdRequestResolver implements OAuth2AuthorizationRequestResolver {
    public static final String OPENID_AUTHENTICATION_PATH = "/openid/authentication";
    private final AntPathRequestMatcher authorizationRequestMatcher;
    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public PublicOpenIdRequestResolver(UserProvidedClientRegistrationRepository repository) {
        this.authorizationRequestMatcher = new AntPathRequestMatcher(OPENID_AUTHENTICATION_PATH);
        delegate = new DefaultOAuth2AuthorizationRequestResolver(repository, OPENID_AUTHENTICATION_PATH);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        if (this.authorizationRequestMatcher.matches(request)) {
            return resolve(request, PUBLIC_REGISTRATION_ID);
        }
        return null;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return delegate.resolve(request, clientRegistrationId);
    }
}
