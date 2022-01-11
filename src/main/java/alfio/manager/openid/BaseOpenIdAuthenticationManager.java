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

import alfio.config.authentication.support.OpenIdAlfioAuthentication;
import alfio.config.authentication.support.OpenIdAlfioUser;
import alfio.manager.user.UserManager;
import alfio.model.user.Organization;
import alfio.model.user.Role;
import alfio.model.user.User;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.repository.user.join.UserOrganizationRepository;
import alfio.util.HttpUtils;
import alfio.util.Json;
import alfio.util.PasswordGenerator;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.Claim;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;

import static alfio.util.HttpUtils.APPLICATION_FORM_URLENCODED;
import static alfio.util.HttpUtils.APPLICATION_JSON;

@Log4j2
abstract class BaseOpenIdAuthenticationManager implements OpenIdAuthenticationManager {
    protected static final String CODE = "code";
    protected static final String ID_TOKEN = "id_token";
    protected static final String SUBJECT = "sub";
    protected static final String EMAIL = "email";
    private static final String HTTPS = "https";
    private static final String REDIRECT_URI = "redirect_uri";

    protected final HttpClient httpClient;
    private final UserManager userManager;
    private final UserRepository userRepository;
    private final AuthorityRepository authorityRepository;
    private final OrganizationRepository organizationRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final Json json;

    protected BaseOpenIdAuthenticationManager(HttpClient httpClient,
                                              UserManager userManager,
                                              UserRepository userRepository,
                                              AuthorityRepository authorityRepository,
                                              OrganizationRepository organizationRepository,
                                              UserOrganizationRepository userOrganizationRepository,
                                              NamedParameterJdbcTemplate jdbcTemplate,
                                              PasswordEncoder passwordEncoder,
                                              Json json) {
        this.httpClient = httpClient;
        this.userManager = userManager;
        this.userRepository = userRepository;
        this.authorityRepository = authorityRepository;
        this.organizationRepository = organizationRepository;
        this.userOrganizationRepository = userOrganizationRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.json = json;
    }

    @Override
    public final OpenIdAlfioAuthentication authenticateUser(String code, HttpSession session) {
        try {
            log.trace("Attempting to retrieve Access Token");
            var accessTokenResponse = retrieveAccessToken(code);
            String idToken = (String) accessTokenResponse.get(ID_TOKEN);

            // implementation note:
            //
            // the JWT token is not intended to be propagated to the client, which is not aware of the
            // authentication method used.
            //
            // In addition to that, since we consider the Token Provider a trusted entity (specified by the admin),
            // we don't *strictly* need to verify the token.
            // This might change in the future if we decide to propagate the token to the client
            Map<String, Claim> idTokenClaims = JWT.decode(idToken).getClaims();
            String subject = idTokenClaims.get(SUBJECT).asString();
            String email = idTokenClaims.get(EMAIL).asString();

            var userInfo = fromToken(idToken, subject, email, idTokenClaims);
            return createOrRetrieveUser(userInfo, idTokenClaims, session);
        } catch (Exception e) {
            log.error("Error while decoding token", e);
            throw e;
        }
    }

    private OpenIdAlfioAuthentication createOrRetrieveUser(OpenIdAlfioUser user,
                                                           Map<String, Claim> idTokenClaims,
                                                           HttpSession session) {
        if (!userManager.usernameExists(user.getEmail())) {
            var configuration = openIdConfiguration();
            var result = userRepository.create(user.getEmail(),
                passwordEncoder.encode(PasswordGenerator.generateRandomPassword()),
                retrieveClaimOrBlank(idTokenClaims, configuration.getGivenNameClaim()),
                retrieveClaimOrBlank(idTokenClaims, configuration.getFamilyNameClaim()),
                user.getEmail(),
                true,
                getUserType(),
                null,
                null);
            onUserCreated(userRepository.findById(result.getKey()), session);
        }

        if(syncRoles()) {
            updateRoles(user.getAlfioRoles(), user.getEmail());
            updateOrganizations(user);
        }

        List<GrantedAuthority> authorities = user.getAlfioRoles().stream().map(Role::getRoleName)
            .map(SimpleGrantedAuthority::new).collect(Collectors.toList());
        return new OpenIdAlfioAuthentication(authorities, user.getIdToken(), user.getSubject(), user.getEmail(), buildLogoutUrl(), user.isPublicUser());
    }

    private void onUserCreated(User user, HttpSession session) {
        session.setAttribute(USER_SIGNED_UP, true);
        this.internalOnUserCreated(user);
    }

    protected void internalOnUserCreated(User user) {
        // default implementation does nothing
    }

    private static String retrieveClaimOrBlank(Map<String, Claim> claims, String name) {
        String claimValue = null;
        if(claims.containsKey(name)) {
            claimValue = claims.get(name).asString();
        }
        return StringUtils.trimToEmpty(claimValue);
    }

    private void updateOrganizations(OpenIdAlfioUser alfioUser) {
        int userId = userRepository.findIdByUserName(alfioUser.getEmail()).orElseThrow();
        var databaseOrganizationIds = organizationRepository.findAllForUser(alfioUser.getEmail()).stream()
            .map(Organization::getId).collect(Collectors.toSet());

        if (alfioUser.isAdmin()) {
            if(!databaseOrganizationIds.isEmpty()) {
                userOrganizationRepository.removeOrganizationUserLinks(userId, databaseOrganizationIds);
            }
            return;
        }

        List<Integer> organizationIds;
        var userOrg = alfioUser.getAlfioOrganizationAuthorizations().keySet();
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

    protected abstract OpenIdAlfioUser fromToken(String idToken, String subject, String email, Map<String, Claim> claims);
    protected abstract OpenIdConfiguration openIdConfiguration();
    protected abstract List<String> getScopes();
    protected abstract User.Type getUserType();
    protected abstract boolean syncRoles();

    @Override
    public String buildAuthorizeUrl(String state) {
        log.trace("buildAuthorizeUrl, configuration: {}", this::openIdConfiguration);
        String scopeParameter = String.join("+", getScopes());

        UriComponents uri = UriComponentsBuilder.newInstance()
            .scheme(HTTPS)
            .host(openIdConfiguration().getDomain())
            .path(openIdConfiguration().getAuthenticationUrl())
            .queryParam(REDIRECT_URI, openIdConfiguration().getCallbackURI())
            .queryParam("client_id", openIdConfiguration().getClientId())
            .queryParam("state", state)
            .queryParam("scope", scopeParameter)
            .queryParam("response_type", "code")
            .build();
        return uri.toUriString();
    }

    @Override
    public String buildClaimsRetrieverUrl() {
        UriComponents uri = UriComponentsBuilder.newInstance()
            .scheme(HTTPS)
            .host(openIdConfiguration().getDomain())
            .path(openIdConfiguration().getTokenEndpoint())
            .build();
        return uri.toUriString();
    }

    @Override
    public String buildLogoutUrl() {
        UriComponents uri = UriComponentsBuilder.newInstance()
            .scheme(HTTPS)
            .host(openIdConfiguration().getDomain())
            .path(openIdConfiguration().getLogoutUrl())
            .queryParam(REDIRECT_URI, openIdConfiguration().getLogoutRedirectUrl())
            .build();
        return uri.toString();
    }

    @Override
    public String buildRetrieveClaimsUrlBody(String code) {
        var contentType = openIdConfiguration().getContentType();
        if (contentType.equals(APPLICATION_JSON)) {
            return buildAccessTokenUrlJson(code);
        }
        if (contentType.equals(APPLICATION_FORM_URLENCODED)) {
            return buildAccessTokenUrlForm(code);
        }
        throw new OpenIdAuthenticationException("the Content-Type specified is not supported");
    }

    private String buildAccessTokenUrlJson(String code) {
        Map<String, String> body = Map.of(
            "grant_type", "authorization_code",
            "code", code,
            "client_id", openIdConfiguration().getClientId(),
            "client_secret", openIdConfiguration().getClientSecret(),
            REDIRECT_URI, openIdConfiguration().getCallbackURI()
        );
        return json.asJsonString(body);
    }

    private String buildAccessTokenUrlForm(String code) {
        return "grant_type=authorization_code" +
            "&code=" + code +
            "&client_id=" + openIdConfiguration().getClientId() +
            "&client_secret=" + openIdConfiguration().getClientSecret() +
            "&redirect_uri=" + openIdConfiguration().getCallbackURI();
    }

    private Map<String, Object> retrieveAccessToken(String code){
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(buildClaimsRetrieverUrl()))
                .header("Content-Type", openIdConfiguration().getContentType())
                .POST(HttpRequest.BodyPublishers.ofString(buildRetrieveClaimsUrlBody(code)))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if(HttpUtils.callSuccessful(response)) {
                log.trace("Access Token successfully retrieved");
                return Json.fromJson(response.body(), new TypeReference<>() {});
            } else {
                log.warn("cannot retrieve access token");
                throw new OpenIdAuthenticationException("cannot retrieve access token. Response from server: " +response.body());
            }
        } catch(OpenIdAuthenticationException e) {
            throw e;
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Request was interrupted while retrieving access token", e);
            throw new OpenIdAuthenticationException(e);
        } catch (Exception e) {
            log.error("There has been an error retrieving the access token from the idp using the authorization code", e);
            throw new OpenIdAuthenticationException(e);
        }
    }
}
