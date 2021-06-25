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
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.user.Role;
import alfio.model.user.User;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.repository.user.join.UserOrganizationRepository;
import alfio.util.Json;
import com.auth0.jwt.interfaces.Claim;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.concurrent.LazyInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.http.HttpClient;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Log4j2
public class AdminOpenIdAuthenticationManager extends BaseOpenIdAuthenticationManager {

    private final Logger logger = LoggerFactory.getLogger(AdminOpenIdAuthenticationManager.class);
    private static final String ALFIO_ADMIN = "ALFIO_ADMIN";

    private final LazyConfigurationContainer configurationContainer;

    public AdminOpenIdAuthenticationManager(Environment environment,
                                            HttpClient httpClient,
                                            ConfigurationManager configurationManager,
                                            UserManager userManager,
                                            UserRepository userRepository,
                                            AuthorityRepository authorityRepository,
                                            OrganizationRepository organizationRepository,
                                            UserOrganizationRepository userOrganizationRepository,
                                            NamedParameterJdbcTemplate jdbcTemplate,
                                            PasswordEncoder passwordEncoder,
                                            Json json) {
        super(httpClient,
            userManager,
            userRepository,
            authorityRepository,
            organizationRepository,
            userOrganizationRepository,
            jdbcTemplate,
            passwordEncoder,
            json);
        this.configurationContainer = new LazyConfigurationContainer(environment, configurationManager);
    }

    @Override
    protected OpenIdAlfioUser fromToken(String idToken, String subject, String email, Map<String, Claim> idTokenClaims) {
        List<String> groupsList = idTokenClaims.get(openIdConfiguration().getRolesParameter()).asList(String.class);
        log.trace("IdToken contains the following groups: {}", groupsList);
        List<String> groups = groupsList.stream().filter(group -> group.startsWith("ALFIO_")).collect(Collectors.toList());
        boolean isAdmin = groups.contains(ALFIO_ADMIN);

        if (isAdmin) {
            log.trace("User is admin");
            return new OpenIdAlfioUser(idToken, subject, email, getUserType(), Set.of(Role.ADMIN), null);
        }

        log.trace("User is NOT admin");

        if(groups.isEmpty()){
            String message = "Users must have at least a group called ALFIO_ADMIN or ALFIO_BACKOFFICE";
            logger.error(message);
            throw new RuntimeException(message);
        }

        List<String> alfioOrganizationAuthorizationsRaw = idTokenClaims.get(openIdConfiguration().getAlfioGroupsParameter()).asList(String.class);
        log.trace("IdToken contains the following alfioGroups: {}", alfioOrganizationAuthorizationsRaw);
        Map<String, Set<String>> alfioOrganizationAuthorizations = extractOrganizationRoles(alfioOrganizationAuthorizationsRaw);
        Set<Role> alfioRoles = extractAlfioRoles(alfioOrganizationAuthorizations);
        return new OpenIdAlfioUser(idToken, subject, email, getUserType(), alfioRoles, alfioOrganizationAuthorizations);
    }

    @SneakyThrows
    @Override
    protected OpenIdConfiguration openIdConfiguration() {
        return configurationContainer.get();
    }

    private Set<Role> extractAlfioRoles(Map<String, Set<String>> alfioOrganizationAuthorizations) {
        Set<Role> alfioRoles = new HashSet<>();
        //FIXME at the moment, the authorizations are NOT based on the organizations, they are global
        alfioOrganizationAuthorizations.keySet().stream()
            .map(alfioOrganizationAuthorizations::get)
            .forEach(authorizations ->
                authorizations.stream().map(auth -> Role.fromRoleName("ROLE_" + auth))
                    .forEach(alfioRoles::add));
        return alfioRoles;
    }

    public List<String> getScopes() {
        var openIdConfiguration = openIdConfiguration();
        return List.of(
            "openid",
            "email",
            "profile",
            openIdConfiguration.getRolesParameter(),
            openIdConfiguration.getAlfioGroupsParameter(),
            openIdConfiguration.getGivenNameClaim(),
            openIdConfiguration.getFamilyNameClaim()
        );
    }

    @Override
    protected User.Type getUserType() {
        return User.Type.INTERNAL;
    }

    @Override
    protected boolean syncRoles() {
        return true;
    }

    private Map<String, Set<String>> extractOrganizationRoles(List<String> alfioOrganizationAuthorizationsRaw) {
        Map<String, Set<String>> alfioOrganizationAuthorizations = new HashMap<>();

        for (String alfioOrgAuth : alfioOrganizationAuthorizationsRaw) {
            String[] orgRole = Pattern.compile("/").split(alfioOrgAuth);
            String organization = orgRole[1];
            String role = orgRole[2];

            if (alfioOrganizationAuthorizations.containsKey(organization)) {
                alfioOrganizationAuthorizations.get(organization).add(role);
                continue;
            }
            var roles = new HashSet<String>();
            roles.add(role);
            alfioOrganizationAuthorizations.put(organization, roles);
        }
        return alfioOrganizationAuthorizations;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    private static class LazyConfigurationContainer extends LazyInitializer<OpenIdConfiguration> {

        private final Environment environment;
        private final ConfigurationManager configurationManager;

        private LazyConfigurationContainer(Environment environment, ConfigurationManager configurationManager) {
            this.environment = environment;
            this.configurationManager = configurationManager;
        }

        @Override
        protected OpenIdConfiguration initialize() {
            return OpenIdConfiguration.from(environment, configurationManager);
        }
    }

}

