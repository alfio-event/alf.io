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
import alfio.config.authentication.support.OpenIdAlfioUser;
import alfio.config.authentication.support.OpenIdPrincipal;
import alfio.manager.RecaptchaService;
import alfio.manager.openid.OpenIdConfiguration;
import alfio.manager.openid.PublicOpenIdAuthenticationManager;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.user.Organization;
import alfio.model.user.Role;
import alfio.model.user.User;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.repository.user.join.UserOrganizationRepository;
import alfio.util.PasswordGenerator;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import javax.sql.DataSource;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Profile(Initializer.PROFILE_OPENID)
@Configuration(proxyBeanMethods = false)
@Order(1)
public class OpenIdAdminWebSecurity extends AbstractFormBasedWebSecurity {

    private static final Logger log = LoggerFactory.getLogger(OpenIdAdminWebSecurity.class);
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final AuthorityRepository authorityRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public OpenIdAdminWebSecurity(Environment environment,
                                  UserManager userManager,
                                  RecaptchaService recaptchaService,
                                  ConfigurationManager configurationManager,
                                  CsrfTokenRepository csrfTokenRepository,
                                  DataSource dataSource,
                                  PasswordEncoder passwordEncoder,
                                  PublicOpenIdAuthenticationManager openIdAuthenticationManager,
                                  SpringSessionBackedSessionRegistry<?> sessionRegistry,
                                  UserRepository userRepository,
                                  OrganizationRepository organizationRepository,
                                  AuthorityRepository authorityRepository,
                                  UserOrganizationRepository userOrganizationRepository,
                                  NamedParameterJdbcTemplate jdbcTemplate,
                                  PlatformTransactionManager transactionManager) {
        super(environment,
            userManager,
            recaptchaService,
            configurationManager,
            csrfTokenRepository,
            dataSource,
            passwordEncoder,
            openIdAuthenticationManager,
            sessionRegistry);
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.authorityRepository = authorityRepository;
        this.userOrganizationRepository = userOrganizationRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    protected void setupAuthenticationEndpoint(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
        http.oauth2Login(oauth -> oauth.loginProcessingUrl("/callback").userInfoEndpoint(uie -> {
            uie.oidcUserService(oidcUserService(OpenIdConfiguration.from(environment(), configurationManager())));
        }));
    }

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        return new InMemoryClientRegistrationRepository(oauthClientRegistration());
    }

    private ClientRegistration oauthClientRegistration() {
        var openIdConfiguration = OpenIdConfiguration.from(environment(), configurationManager());
        var baseURI = UriComponentsBuilder.newInstance()
            .scheme("https")
            .host(openIdConfiguration.domain());
        return ClientRegistration.withRegistrationId("alfio-admin-idp")
            .clientId(openIdConfiguration.clientId())
            .clientSecret(openIdConfiguration.clientSecret())
            .redirectUri("{baseUrl}/callback")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .scope(List.of(
                "openid",
                "email",
                "profile",
                openIdConfiguration.rolesParameter(),
                openIdConfiguration.alfioGroupsParameter(),
                openIdConfiguration.givenNameClaim(),
                openIdConfiguration.familyNameClaim()
            ))
            .authorizationUri(baseURI.replacePath(openIdConfiguration.authenticationUrl()).toUriString())
            .jwkSetUri(baseURI.replacePath(openIdConfiguration.jwksPath()).toUriString())
            .tokenUri(baseURI.replacePath(openIdConfiguration.tokenEndpoint()).toUriString())
            .build();
    }

    private OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService(OpenIdConfiguration openIdConfiguration) {
        final OidcUserService delegate = new OidcUserService();

        return userRequest -> {
            OidcUser oidcUser = delegate.loadUser(userRequest);

            final OpenIdPrincipal principal;
            List<String> groupsList = oidcUser.getClaim(openIdConfiguration.rolesParameter());
            log.trace("IdToken contains the following groups: {}", groupsList);
            List<String> groups = groupsList.stream().filter(group -> group.startsWith("ALFIO_")).toList();
            boolean isAdmin = groups.contains("ALFIO_ADMIN");

            if (isAdmin) {
                log.trace("User is admin");
                principal = new OpenIdPrincipal(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")), oidcUser.getIdToken(), oidcUser.getUserInfo(), new OpenIdAlfioUser(null, oidcUser.getSubject(), oidcUser.getEmail(), User.Type.INTERNAL, Set.of(Role.ADMIN), null), buildLogoutUrl(openIdConfiguration));
            } else {
                principal = parsePrincipal(openIdConfiguration, groups, oidcUser);
            }
            syncUser(oidcUser, principal.user(), openIdConfiguration);
            return principal;
        };
    }

    private static OpenIdPrincipal parsePrincipal(OpenIdConfiguration openIdConfiguration, List<String> groups, OidcUser oidcUser) {
        final OpenIdPrincipal principal;
        log.trace("User is NOT admin");

        if(groups.isEmpty()){
            String message = "Users must have at least a group called ALFIO_ADMIN or ALFIO_BACKOFFICE";
            log.error(message);
            throw new RuntimeException(message);
        }

        List<String> alfioOrganizationAuthorizationsRaw = oidcUser.getClaim(openIdConfiguration.alfioGroupsParameter());
        log.trace("IdToken contains the following alfioGroups: {}", alfioOrganizationAuthorizationsRaw);
        Map<String, Set<String>> alfioOrganizationAuthorizations = extractOrganizationRoles(alfioOrganizationAuthorizationsRaw);
        Set<Role> alfioRoles = extractAlfioRoles(alfioOrganizationAuthorizations);

        var mappedAuthorities = alfioRoles.stream().map(r -> new SimpleGrantedAuthority(r.getRoleName()))
            .collect(Collectors.toSet());

        // check if user exists
        var alfioUser = new OpenIdAlfioUser(null, oidcUser.getSubject(), oidcUser.getEmail(), User.Type.INTERNAL, alfioRoles, alfioOrganizationAuthorizations);
        principal = new OpenIdPrincipal(mappedAuthorities, oidcUser.getIdToken(), oidcUser.getUserInfo(), alfioUser, buildLogoutUrl(openIdConfiguration));
        return principal;
    }

    private void syncUser(OidcUser oidcUser,
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
                    User.Type.INTERNAL,
                    null,
                    null);
                Validate.isTrue(result.getAffectedRowCount() == 1, "Error while creating user");
                // TODO send notification event "user created"
            }

            updateRoles(internalUser.alfioRoles(), email);
            updateOrganizations(internalUser);
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

    private static String buildLogoutUrl(OpenIdConfiguration openIdConfiguration) {
        UriComponents uri = UriComponentsBuilder.newInstance()
            .scheme("https")
            .host(openIdConfiguration.domain())
            .path(openIdConfiguration.logoutUrl())
            .queryParam("redirect_uri", openIdConfiguration.logoutRedirectUrl())
            .build();
        return uri.toString();
    }

    private static Map<String, Set<String>> extractOrganizationRoles(List<String> alfioOrganizationAuthorizationsRaw) {
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

    private static Set<Role> extractAlfioRoles(Map<String, Set<String>> alfioOrganizationAuthorizations) {
        Set<Role> alfioRoles = new HashSet<>();
        //FIXME at the moment, the authorizations are NOT based on the organizations, they are global
        alfioOrganizationAuthorizations.keySet().stream()
            .map(alfioOrganizationAuthorizations::get)
            .forEach(authorizations ->
                authorizations.stream().map(auth -> Role.fromRoleName("ROLE_" + auth))
                    .forEach(alfioRoles::add));
        return alfioRoles;
    }
}
