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
package alfio.manager.system;

import alfio.config.support.auth.OpenIdAlfioUser;
import alfio.model.user.Role;
import alfio.util.HttpUtils;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.Claim;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.concurrent.LazyInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static alfio.util.HttpUtils.APPLICATION_FORM_URLENCODED;
import static alfio.util.HttpUtils.APPLICATION_JSON;

@Profile("openid")
@Component
@Log4j2
public class OpenIdAuthenticationManager {
    public static final String CODE = "code";
    private static final String ID_TOKEN = "id_token";
    private static final String SUBJECT = "sub";
    private static final String EMAIL = "email";
    private final Logger logger = LoggerFactory.getLogger(OpenIdAuthenticationManager.class);
    private static final String ALFIO_ADMIN = "ALFIO_ADMIN";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final LazyConfigurationContainer configurationContainer;

    public OpenIdAuthenticationManager(Environment environment,
                                       HttpClient httpClient,
                                       ObjectMapper objectMapper,
                                       ConfigurationManager configurationManager) {
        this.configurationContainer = new LazyConfigurationContainer(environment, configurationManager);
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public OpenIdAlfioUser retrieveUserInfo(String code) {
        log.trace("Attempting to retrieve Access Token");
        var accessTokenResponse = retrieveAccessToken(code);
        String idToken = (String) accessTokenResponse.get(ID_TOKEN);

        Map<String, Claim> idTokenClaims = JWT.decode(idToken).getClaims();
        String subject = idTokenClaims.get(SUBJECT).asString();
        String email = idTokenClaims.get(EMAIL).asString();
        List<String> groupsList = idTokenClaims.get(openIdConfiguration().getRolesParameter()).asList(String.class);
        log.trace("IdToken contains the following groups: {}", groupsList);
        List<String> groups = groupsList.stream().filter(group -> group.startsWith("ALFIO_")).collect(Collectors.toList());
        boolean isAdmin = groups.contains(ALFIO_ADMIN);

        if (isAdmin) {
            log.trace("User is admin");
            return new OpenIdAlfioUser(idToken, subject, email, true, Set.of(Role.ADMIN), null);
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
        return new OpenIdAlfioUser(idToken, subject, email, false, alfioRoles, alfioOrganizationAuthorizations);
    }

    @SneakyThrows
    private OpenIdConfiguration openIdConfiguration() {
        return configurationContainer.get();
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
                logger.trace("Access Token successfully retrieved");
                return objectMapper.readValue(response.body(), new TypeReference<>() {});
            } else {
                logger.warn("cannot retrieve access token");
                throw new IllegalStateException("cannot retrieve access token. Response from server: " +response.body());
            }
        } catch (Exception e) {
            logger.error("There has been an error retrieving the access token from the idp using the authorization code", e);
            throw new RuntimeException(e);
        }
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

    public String buildClaimsRetrieverUrl() {
        UriComponents uri = UriComponentsBuilder.newInstance()
                .scheme("https")
                .host(openIdConfiguration().getDomain())
                .path(openIdConfiguration().getTokenEndpoint())
                .build();
        return uri.toUriString();
    }

    public String buildLogoutUrl() {
        UriComponents uri = UriComponentsBuilder.newInstance()
                .scheme("https")
                .host(openIdConfiguration().getDomain())
                .path(openIdConfiguration().getLogoutUrl())
                .queryParam("redirect_uri", openIdConfiguration().getLogoutRedirectUrl())
                .build();
        return uri.toString();
    }

    public String buildRetrieveClaimsUrlBody(String code) throws JsonProcessingException {
        var contentType = openIdConfiguration().getContentType();
        if (contentType.equals(APPLICATION_JSON)) {
            return buildAccessTokenUrlJson(code);
        }
        if (contentType.equals(APPLICATION_FORM_URLENCODED)) {
            return buildAccessTokenUrlForm(code);
        }

        String message = "the Content-Type specified is not supported";
        logger.error(message);
        throw new RuntimeException(message);
    }

    private String buildAccessTokenUrlJson(String code) throws JsonProcessingException {
        Map<String, String> body = Map.of(
                "grant_type", "authorization_code",
                "code", code,
                "client_id", openIdConfiguration().getClientId(),
                "client_secret", openIdConfiguration().getClientSecret(),
                "redirect_uri", openIdConfiguration().getCallbackURI()
        );

        ObjectMapper objectMapper = new ObjectMapper();

        return objectMapper.writeValueAsString(body);
    }

    private String buildAccessTokenUrlForm(String code) {
        return "grant_type=authorization_code" +
            "&code=" + code +
            "&client_id=" + openIdConfiguration().getClientId() +
            "&client_secret=" + openIdConfiguration().getClientSecret() +
            "&redirect_uri=" + openIdConfiguration().getCallbackURI();
    }

    public List<String> getScopes() {
        return List.of("openid", "email", "profile", openIdConfiguration().getRolesParameter(), openIdConfiguration().getAlfioGroupsParameter());
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
            alfioOrganizationAuthorizations.put(organization, Set.of(role));
        }
        return alfioOrganizationAuthorizations;
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

