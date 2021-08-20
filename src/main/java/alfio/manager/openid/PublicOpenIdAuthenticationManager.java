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
package alfio.manager.openid;

import alfio.config.authentication.support.OpenIdAlfioUser;
import alfio.manager.ExtensionManager;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.user.User;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.repository.user.join.UserOrganizationRepository;
import alfio.util.Json;
import com.auth0.jwt.interfaces.Claim;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static alfio.model.system.ConfigurationKeys.OPENID_CONFIGURATION_JSON;

public class PublicOpenIdAuthenticationManager extends BaseOpenIdAuthenticationManager {

    private final ConfigurationManager configurationManager;
    private final ExtensionManager extensionManager;

    public PublicOpenIdAuthenticationManager(HttpClient httpClient,
                                             ConfigurationManager configurationManager,
                                             UserManager userManager,
                                             UserRepository userRepository,
                                             AuthorityRepository authorityRepository,
                                             OrganizationRepository organizationRepository,
                                             UserOrganizationRepository userOrganizationRepository,
                                             NamedParameterJdbcTemplate jdbcTemplate,
                                             PasswordEncoder passwordEncoder,
                                             Json json,
                                             ExtensionManager extensionManager) {
        super(httpClient, userManager, userRepository, authorityRepository, organizationRepository, userOrganizationRepository, jdbcTemplate, passwordEncoder, json);
        this.configurationManager = configurationManager;
        this.extensionManager = extensionManager;
    }


    @Override
    protected OpenIdAlfioUser fromToken(String idToken, String subject, String email, Map<String, Claim> claims) {
        return new OpenIdAlfioUser(idToken, subject, email, getUserType(), Set.of(), Map.of());
    }

    @Override
    protected void internalOnUserCreated(User user) {
        extensionManager.handlePublicUserSignUp(user);
    }

    @Override
    protected OpenIdConfiguration openIdConfiguration() {
        return Json.fromJson(configurationManager.getPublicOpenIdConfiguration().get(OPENID_CONFIGURATION_JSON).getValueOrNull(), OpenIdConfiguration.class);
    }

    @Override
    protected List<String> getScopes() {
        return List.of("openid", "email", "profile");
    }

    @Override
    protected User.Type getUserType() {
        return User.Type.PUBLIC;
    }

    @Override
    protected boolean syncRoles() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return configurationManager.isPublicOpenIdEnabled();
    }
}
