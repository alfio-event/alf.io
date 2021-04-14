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
import alfio.util.HttpUtils;
import alfio.util.Json;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.Claim;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static alfio.util.HttpUtils.APPLICATION_FORM_URLENCODED;
import static alfio.util.HttpUtils.APPLICATION_JSON;

@Log4j2
abstract class BaseOpenIdAuthenticationManager {
    protected static final String CODE = "code";
    protected static final String ID_TOKEN = "id_token";
    protected static final String SUBJECT = "sub";
    protected static final String EMAIL = "email";

    protected final HttpClient httpClient;

    protected BaseOpenIdAuthenticationManager(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public final OpenIdAlfioUser retrieveUserInfo(String code) {
        log.trace("Attempting to retrieve Access Token");
        var accessTokenResponse = retrieveAccessToken(code);
        String idToken = (String) accessTokenResponse.get(ID_TOKEN);

        Map<String, Claim> idTokenClaims = JWT.decode(idToken).getClaims();
        String subject = idTokenClaims.get(SUBJECT).asString();
        String email = idTokenClaims.get(EMAIL).asString();

        return fromToken(idToken, subject, email, idTokenClaims);
    }

    protected abstract OpenIdAlfioUser fromToken(String idToken, String subject, String email, Map<String, Claim> claims);

    protected abstract OpenIdConfiguration openIdConfiguration();

    protected abstract List<String> getScopes();

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
        throw new RuntimeException("the Content-Type specified is not supported");
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
}
