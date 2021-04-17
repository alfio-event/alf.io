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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

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
    public final OpenIdAlfioAuthentication authenticateUser(String code) {
        log.trace("Attempting to retrieve Access Token");
        var accessTokenResponse = retrieveAccessToken(code);
        String idToken = (String) accessTokenResponse.get(ID_TOKEN);

        Map<String, Claim> idTokenClaims = JWT.decode(idToken).getClaims();
        String subject = idTokenClaims.get(SUBJECT).asString();
        String email = idTokenClaims.get(EMAIL).asString();

        var userInfo = fromToken(idToken, subject, email, idTokenClaims);
        return createOrRetrieveUser(userInfo);
    }

    private OpenIdAlfioAuthentication createOrRetrieveUser(OpenIdAlfioUser user) {
        if (!userManager.usernameExists(user.getEmail())) {
            userRepository.create(user.getEmail(),
                passwordEncoder.encode(PasswordGenerator.generateRandomPassword()),
                user.getEmail(),
                user.getEmail(),
                user.getEmail(),
                true,
                getUserType(),
                null,
                null);
        }

        if(syncRoles()) {
            updateRoles(user.getAlfioRoles(), user.getEmail());
            updateOrganizations(user);
        }

        List<GrantedAuthority> authorities = user.getAlfioRoles().stream().map(Role::getRoleName)
            .map(SimpleGrantedAuthority::new).collect(Collectors.toList());
        return new OpenIdAlfioAuthentication(authorities, user.getIdToken(), user.getSubject(), user.getEmail(), buildLogoutUrl());
    }

    private void updateOrganizations(OpenIdAlfioUser alfioUser) {
        int userId = userRepository.findIdByUserName(alfioUser.getEmail()).orElseThrow();
        var databaseOrganizationIds = organizationRepository.findAllForUser(alfioUser.getEmail()).stream()
            .map(Organization::getId).collect(Collectors.toSet());

        if (alfioUser.isAdmin()) {
            userOrganizationRepository.removeOrganizationUserLinks(userId, databaseOrganizationIds);
            return;
        }

        Set<Integer> organizationIds = alfioUser.getAlfioOrganizationAuthorizations().keySet().stream()
            .map(organizationRepository::findByNameOpenId)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .filter(Objects::nonNull)
            .map(Organization::getId)
            .collect(Collectors.toSet());

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
            .map(r -> new MapSqlParameterSource("username", username).addValue("role", r))
            .toArray(MapSqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(authorityRepository.grantAll(), rolesToAdd);
    }

    protected abstract OpenIdAlfioUser fromToken(String idToken, String subject, String email, Map<String, Claim> claims);
    protected abstract OpenIdConfiguration openIdConfiguration();
    protected abstract List<String> getScopes();
    protected abstract User.Type getUserType();
    protected abstract boolean syncRoles();

    @Override
    public String buildAuthorizeUrl() {
        log.trace("buildAuthorizeUrl, configuration: {}", this::openIdConfiguration);
        String scopeParameter = String.join("+", getScopes());

        UriComponents uri = UriComponentsBuilder.newInstance()
            .scheme("https")
            .host(openIdConfiguration().getDomain())
            .path(openIdConfiguration().getAuthenticationUrl())
            .queryParam("redirect_uri", openIdConfiguration().getCallbackURI())
            .queryParam("client_id", openIdConfiguration().getClientId())
            .queryParam("scope", scopeParameter)
            .queryParam("response_type", "code")
            .build();
        return uri.toUriString();
    }

    @Override
    public String buildClaimsRetrieverUrl() {
        UriComponents uri = UriComponentsBuilder.newInstance()
            .scheme("https")
            .host(openIdConfiguration().getDomain())
            .path(openIdConfiguration().getTokenEndpoint())
            .build();
        return uri.toUriString();
    }

    @Override
    public String buildLogoutUrl() {
        UriComponents uri = UriComponentsBuilder.newInstance()
            .scheme("https")
            .host(openIdConfiguration().getDomain())
            .path(openIdConfiguration().getLogoutUrl())
            .queryParam("redirect_uri", openIdConfiguration().getLogoutRedirectUrl())
            .build();
        return uri.toString();
    }

    @Override
    public String buildRetrieveClaimsUrlBody(String code) throws JsonProcessingException {
        var contentType = openIdConfiguration().getContentType();
        if (contentType.equals(APPLICATION_JSON)) {
            return buildAccessTokenUrlJson(code);
        }
        if (contentType.equals(APPLICATION_FORM_URLENCODED)) {
            return buildAccessTokenUrlForm(code);
        }
        throw new RuntimeException("the Content-Type specified is not supported");
    }

    private String buildAccessTokenUrlJson(String code) {
        Map<String, String> body = Map.of(
            "grant_type", "authorization_code",
            "code", code,
            "client_id", openIdConfiguration().getClientId(),
            "client_secret", openIdConfiguration().getClientSecret(),
            "redirect_uri", openIdConfiguration().getCallbackURI()
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
                return Json.fromJson(response.body(), new TypeReference<>() {
                });
            } else {
                log.warn("cannot retrieve access token");
                throw new IllegalStateException("cannot retrieve access token. Response from server: " +response.body());
            }
        } catch (Exception e) {
            log.error("There has been an error retrieving the access token from the idp using the authorization code", e);
            throw new RuntimeException(e);
        }
    }
}
