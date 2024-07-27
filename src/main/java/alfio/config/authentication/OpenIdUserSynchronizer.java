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

import alfio.config.authentication.support.OpenIdAlfioUser;
import alfio.manager.ExtensionManager;
import alfio.manager.openid.OpenIdConfiguration;
import alfio.manager.user.UserManager;
import alfio.model.user.Organization;
import alfio.model.user.Role;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.repository.user.join.UserOrganizationRepository;
import alfio.util.PasswordGenerator;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Transactional
public class OpenIdUserSynchronizer {
    public static final String USER_SIGNED_UP = "USER_SIGNED_UP";
    private final TransactionTemplate transactionTemplate;
    private final PasswordEncoder passwordEncoder;
    private final UserManager userManager;
    private final UserRepository userRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AuthorityRepository authorityRepository;
    private final OrganizationRepository organizationRepository;
    private final ExtensionManager extensionManager;

    public OpenIdUserSynchronizer(PlatformTransactionManager transactionManager,
                                  PasswordEncoder passwordEncoder,
                                  UserManager userManager,
                                  UserRepository userRepository,
                                  UserOrganizationRepository userOrganizationRepository,
                                  NamedParameterJdbcTemplate jdbcTemplate,
                                  AuthorityRepository authorityRepository,
                                  OrganizationRepository organizationRepository,
                                  ExtensionManager extensionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.passwordEncoder = passwordEncoder;
        this.userManager = userManager;
        this.userRepository = userRepository;
        this.userOrganizationRepository = userOrganizationRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.authorityRepository = authorityRepository;
        this.organizationRepository = organizationRepository;
        this.extensionManager = extensionManager;
    }

    public void syncUser(OidcUser oidcUser,
                         OpenIdAlfioUser internalUser,
                         OpenIdConfiguration configuration) {
        transactionTemplate.execute(tr -> {
            String email = oidcUser.getEmail();
            if (!userManager.usernameExists(email)) {
                var result = userRepository.create(email,
                    passwordEncoder.encode(PasswordGenerator.generateRandomPassword()),
                    retrieveClaimOrBlank(configuration.givenNameClaim(), oidcUser),
                    retrieveClaimOrBlank(configuration.familyNameClaim(), oidcUser),
                    email,
                    true,
                    internalUser.userType(),
                    null,
                    null);
                Validate.isTrue(result.getAffectedRowCount() == 1, "Error while creating user");
                // FIXME show tooltip when user signs up
//                var session = ((ServletRequestAttributes)RequestContextHolder.getRequestAttributes()).getRequest().getSession(false);
//                session.setAttribute(USER_SIGNED_UP, true);
                if (internalUser.isPublicUser()) {
                    extensionManager.handlePublicUserSignUp(userRepository.findById(result.getKey()));
                }
            }

            if (!internalUser.isPublicUser()) {
                updateRoles(internalUser.alfioRoles(), email);
                updateOrganizations(internalUser);
            }
            return null;
        });
    }

        private static String retrieveClaimOrBlank(String claim, OidcUser container) {
            if (claim == null) {
                return "";
            }
            return StringUtils.trim(container.getClaim(claim));
        }

    private void updateOrganizations(OpenIdAlfioUser alfioUser) {
        int userId = userRepository.findIdByUserName(alfioUser.email()).orElseThrow();
        var databaseOrganizationIds = organizationRepository.findAllForUser(alfioUser.email()).stream()
            .map(Organization::getId).collect(Collectors.toSet());

        if (alfioUser.isAdmin()) {
            if(!databaseOrganizationIds.isEmpty()) {
                userOrganizationRepository.removeOrganizationUserLinks(userId, databaseOrganizationIds);
            }
            return;
        }

        List<Integer> organizationIds;
        var userOrg = alfioUser.alfioOrganizationAuthorizations().keySet();
        if(!userOrg.isEmpty()) {
            organizationIds = organizationRepository.findOrganizationIdsByExternalId(userOrg);
        } else {
            organizationIds = List.of();
        }

        var organizationsToUnlink = databaseOrganizationIds.stream()
            .filter(orgId -> !organizationIds.contains(orgId))
            .collect(Collectors.toSet());

        if (!organizationsToUnlink.isEmpty()) {
            userOrganizationRepository.removeOrganizationUserLinks(userId, organizationsToUnlink);
        }

        if (organizationIds.isEmpty()) {
            throw new IllegalStateException("The user needs to be ADMIN or have at least one organization linked");
        }

        var params = organizationIds.stream().filter(orgId -> !databaseOrganizationIds.contains(orgId))
            .map(id -> new MapSqlParameterSource("userId", userId).addValue("organizationId", id))
            .toArray(MapSqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(userOrganizationRepository.bulkCreate(), params);
    }

    private void updateRoles(Set<Role> roles, String username) {
        authorityRepository.revokeAll(username);
        var rolesToAdd = roles.stream()
            .map(r -> new MapSqlParameterSource("username", username).addValue("role", r.getRoleName()))
            .toArray(MapSqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(authorityRepository.grantAll(), rolesToAdd);
    }

}
