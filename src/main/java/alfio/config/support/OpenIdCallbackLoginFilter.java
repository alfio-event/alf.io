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
package alfio.config.support;

import alfio.config.WebSecurityConfig;
import alfio.manager.OpenIdAuthenticationManager;
import alfio.manager.user.UserManager;
import alfio.model.user.Organization;
import alfio.model.user.Role;
import alfio.model.user.User;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.repository.user.join.UserOrganizationRepository;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.Claim;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class OpenIdCallbackLoginFilter extends AbstractAuthenticationProcessingFilter {
    private static final String ALFIO_ADMIN = "ALFIO_ADMIN";
    private static final String ALFIO_BACKOFFICE = "ALFIO_BACKOFFICE";

    private final RequestMatcher requestMatcher;
    private final UserRepository userRepository;
    private final AuthorityRepository authorityRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserManager userManager;
    private final UserOrganizationRepository userOrganizationRepository;
    private final OrganizationRepository organizationRepository;
    private final OpenIdAuthenticationManager openIdAuthenticationManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenIdCallbackLoginFilter(OpenIdAuthenticationManager openIdAuthenticationManager,
                                     AntPathRequestMatcher requestMatcher,
                                     AuthenticationManager authenticationManager,
                                     UserRepository userRepository,
                                     AuthorityRepository authorityRepository,
                                     PasswordEncoder passwordEncoder,
                                     UserManager userManager,
                                     UserOrganizationRepository userOrganizationRepository,
                                     OrganizationRepository organizationRepository) {
        super(requestMatcher);
        this.setAuthenticationManager(authenticationManager);
        this.userRepository = userRepository;
        this.authorityRepository = authorityRepository;
        this.passwordEncoder = passwordEncoder;
        this.userManager = userManager;
        this.userOrganizationRepository = userOrganizationRepository;
        this.organizationRepository = organizationRepository;
        this.requestMatcher = requestMatcher;
        this.openIdAuthenticationManager = openIdAuthenticationManager;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        if (requestMatcher.matches(req)) {
            super.doFilter(req, res, chain);
        }

        chain.doFilter(request, response);
    }

    private void updateOrganizations(OpenIdAlfioUser alfioUser, HttpServletResponse response) throws IOException {
        Optional<Integer> userId = userRepository.findIdByUserName(alfioUser.getEmail());
        if (userId.isEmpty()) {
            String message = "Error: user not saved into the database";
            logger.error(message);
            response.sendRedirect(openIdAuthenticationManager.buildLogoutUrl());
            return;
        }

        Set<Integer> databaseOrganizationIds = organizationRepository.findAllForUser(alfioUser.getEmail()).stream()
            .map(Organization::getId).collect(Collectors.toSet());

        if (alfioUser.isAdmin()) {
            databaseOrganizationIds.forEach(orgId -> userOrganizationRepository.removeOrganizationUserLink(userId.get(), orgId));
            return;
        }

        Set<Integer> organizationIds = alfioUser.getAlfioOrganizationAuthorizations().keySet().stream()
            .map(organizationRepository::findByNameOpenId)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .filter(Objects::nonNull)
            .map(Organization::getId)
            .collect(Collectors.toSet());

        databaseOrganizationIds.stream()
            .filter(orgId -> !organizationIds.contains(orgId))
            .forEach(orgId -> userOrganizationRepository.removeOrganizationUserLink(userId.get(), orgId));

        if (organizationIds.isEmpty()) {
            String message = "Error: The user needs to be ADMIN or to have at least one organization linked";
            logger.error(message);
            response.sendRedirect(openIdAuthenticationManager.buildLogoutUrl());
        }

        organizationIds.stream().filter(orgId -> !databaseOrganizationIds.contains(orgId))
            .forEach(orgId -> userOrganizationRepository.create(userId.get(), orgId));
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

    private void createUser(OpenIdAlfioUser user) {
        userRepository.create(user.getEmail(), passwordEncoder.encode(user.getSubject()), user.getEmail(), user.getEmail(), user.getEmail(), true, User.Type.INTERNAL, null, null);
    }

    private OpenIdAlfioUser extractUserInfoFrom(Map<String, Object> claims) {
        String idToken = (String) claims.get(openIdAuthenticationManager.getIdTokenNameParameter());

        Map<String, Claim> idTokenClaims = JWT.decode(idToken).getClaims();
        String subject = idTokenClaims.get(openIdAuthenticationManager.getSubjectNameParameter()).asString();
        String email = idTokenClaims.get(openIdAuthenticationManager.getEmailNameParameter()).asString();
        List<String> groupsList = idTokenClaims.get(openIdAuthenticationManager.getGroupsNameParameter()).asList(String.class);
        List<String> groups = groupsList.stream().filter(group -> group.startsWith("ALFIO_")).collect(Collectors.toList());
        boolean isAdmin = groups.contains(ALFIO_ADMIN);

        if (isAdmin) {
            return new OpenIdAlfioUser(idToken, subject, email, true, Set.of(Role.ADMIN), null);
        }

        if(groups.isEmpty()){
            String message = "Users must have at least a group called ALFIO_ADMIN or ALFIO_BACKOFFICE";
            logger.error(message);
            throw new RuntimeException(message);
        }

        List<String> alfioOrganizationAuthorizationsRaw = idTokenClaims.get(openIdAuthenticationManager.getAlfioGroupsNameParameter()).asList(String.class);
        Map<String, Set<String>> alfioOrganizationAuthorizations = new HashMap<>();

        for (String alfioOrgAuth : alfioOrganizationAuthorizationsRaw) {
            String[] orgRole = Pattern.compile("/").split(alfioOrgAuth);
            String organization = orgRole[1];
            String role = orgRole[2];

            if (alfioOrganizationAuthorizations.containsKey(organization)) {
                alfioOrganizationAuthorizations.get(organization).add(role);
                continue;
            }
            alfioOrganizationAuthorizations.put(organization, Set.of(role));
        }
        Set<Role> alfioRoles = extractAlfioRoles(alfioOrganizationAuthorizations);
        return new OpenIdAlfioUser(idToken, subject, email, false, alfioRoles, alfioOrganizationAuthorizations);
    }

    private Map<String, Object> retrieveClaims(String claimsUrl, String code){
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request;
        HttpResponse<String> response;
        Map<String, Object> map;

        try {
            request = HttpRequest.newBuilder()
                .uri(URI.create(claimsUrl))
                .header("Content-Type", openIdAuthenticationManager.getContentType())
                .POST(HttpRequest.BodyPublishers.ofString(openIdAuthenticationManager.buildRetrieveClaimsUrlBody(code)))
                .build();

            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            map = objectMapper.readValue(response.body(), new TypeReference<>() {});

        } catch (Exception e) {
            String message = "There has been an error retrieving the claims from the idp using the authorization code";
            logger.error(message);
            throw new RuntimeException(message);
        }

        return map;
    }

    private void updateRoles(Set<Role> roles, String username) {
        authorityRepository.revokeAll(username);
        roles.forEach(role -> authorityRepository.create(username, role.getRoleName()));
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException, IOException, ServletException {
        String code = request.getParameter(openIdAuthenticationManager.getCodeNameParameter());
        if (code == null) {
            throw new IllegalArgumentException("authorization code cannot be null");
        }

        String claimsUrl = openIdAuthenticationManager.buildClaimsRetrieverUrl();
        Map<String, Object> claims = retrieveClaims(claimsUrl, code);

        OpenIdAlfioUser alfioUser = extractUserInfoFrom(claims);

        if (!userManager.usernameExists(alfioUser.getEmail())) {
            createUser(alfioUser);
        }
        updateRoles(alfioUser.getAlfioRoles(), alfioUser.getEmail());
        updateOrganizations(alfioUser, response);

        List<GrantedAuthority> authorities = alfioUser.getAlfioRoles().stream().map(Role::getRoleName)
            .map(SimpleGrantedAuthority::new).collect(Collectors.toList());
        WebSecurityConfig.OpenIdAlfioAuthentication authentication = new WebSecurityConfig.OpenIdAlfioAuthentication(authorities, alfioUser.getIdToken(), alfioUser.getSubject(), alfioUser.getEmail(), openIdAuthenticationManager.buildLogoutUrl());
        return getAuthenticationManager().authenticate(authentication);
    }
}
