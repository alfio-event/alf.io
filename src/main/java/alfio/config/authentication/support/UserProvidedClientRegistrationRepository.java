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

import alfio.manager.openid.OpenIdConfiguration;
import alfio.manager.system.ConfigurationManager;
import alfio.util.Json;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import java.util.Objects;

import static alfio.model.system.ConfigurationKeys.OPENID_CONFIGURATION_JSON;

public class UserProvidedClientRegistrationRepository implements ClientRegistrationRepository {

    public static final String PUBLIC_REGISTRATION_ID = "alfio-public-openid";
    public static final String OPENID_CALLBACK_PATH = "/openid/callback";
    private final ConfigurationManager configurationManager;

    public UserProvidedClientRegistrationRepository(ConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
    }


    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        // lookup from configuration
        if (!configurationManager.isPublicOpenIdEnabled()) {
            throw new AuthenticationCredentialsNotFoundException("openid is not enabled");
        }
        var openIdConfiguration = Objects.requireNonNull(Json.fromJson(configurationManager.getPublicOpenIdConfiguration().get(OPENID_CONFIGURATION_JSON).getValueOrNull(), OpenIdConfiguration.class));
        return openIdConfiguration.toClientRegistration(PUBLIC_REGISTRATION_ID, "{baseUrl}"+ OPENID_CALLBACK_PATH, false);
    }
}
