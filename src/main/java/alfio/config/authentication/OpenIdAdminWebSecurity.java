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
import alfio.config.authentication.support.OpenIdLoginSuccessHandler;
import alfio.config.authentication.support.OpenIdPrincipal;
import alfio.config.authentication.support.PreAuthCookieWriterFilter;
import alfio.manager.RecaptchaService;
import alfio.manager.openid.OpenIdConfiguration;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.user.Role;
import alfio.model.user.User;
import alfio.util.TemplateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;
import org.springframework.session.web.http.CookieSerializer;

import javax.sql.DataSource;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Profile(Initializer.PROFILE_OPENID)
@Configuration(proxyBeanMethods = false)
@Order(1)
public class OpenIdAdminWebSecurity extends AbstractFormBasedWebSecurity {

    private static final Logger log = LoggerFactory.getLogger(OpenIdAdminWebSecurity.class);
    private static final String ALFIO_ADMIN_IDP = "alfio-admin-idp";
    private static final String ADMIN_LOGIN_REDIRECT_PATH = "/oauth2/authorization/"+ ALFIO_ADMIN_IDP;
    private static final String ADMIN_OPENID_CALLBACK_PATH = "/callback";

    public OpenIdAdminWebSecurity(Environment environment,
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

    @Override
    protected void setupAuthenticationEndpoint(HttpSecurity http) throws Exception {
        var clientRegistrationRepository = new InMemoryClientRegistrationRepository(OpenIdConfiguration.from(environment(), configurationManager())
            .toClientRegistration(ALFIO_ADMIN_IDP, "{baseUrl}/callback", true));
        http.oauth2Login(oauth -> oauth.loginProcessingUrl(ADMIN_OPENID_CALLBACK_PATH)
            .clientRegistrationRepository(clientRegistrationRepository)
            .userInfoEndpoint(uie -> uie.oidcUserService(oidcUserService(OpenIdConfiguration.from(environment(), configurationManager()))))
            .successHandler(new OpenIdLoginSuccessHandler(templateManager, cookieSerializer))
        ).addFilterBefore(new PreAuthCookieWriterFilter(cookieSerializer, PathPatternRequestMatcher.withDefaults().matcher(ADMIN_LOGIN_REDIRECT_PATH)), OAuth2AuthorizationRequestRedirectFilter.class);
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
                principal = new OpenIdPrincipal(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")), oidcUser.getIdToken(), oidcUser.getUserInfo(), new OpenIdAlfioUser(null, oidcUser.getSubject(), oidcUser.getEmail(), User.Type.INTERNAL, Set.of(Role.ADMIN), null), buildLogoutUrl(openIdConfiguration), false);
            } else {
                principal = parsePrincipal(openIdConfiguration, groups, oidcUser);
            }
            openIdUserSynchronizer.syncUser(oidcUser, principal.user(), openIdConfiguration);
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
        principal = new OpenIdPrincipal(mappedAuthorities, oidcUser.getIdToken(), oidcUser.getUserInfo(), alfioUser, buildLogoutUrl(openIdConfiguration), false);
        return principal;
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
