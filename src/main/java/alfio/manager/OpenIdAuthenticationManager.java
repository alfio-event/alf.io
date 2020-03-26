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
package alfio.manager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

import static alfio.util.HttpUtils.APPLICATION_FORM_URLENCODED;
import static alfio.util.HttpUtils.APPLICATION_JSON;

@Component
@Profile("openid")
public class OpenIdAuthenticationManager {
    private final Logger logger = LoggerFactory.getLogger(OpenIdAuthenticationManager.class);

    private final String domain;
    private final String clientId;
    private final String clientSecret;
    private final String callbackURI;
    private final String authenticationUrl;
    private final String claimsUrl;
    private final String contentType;
    private final String groupsNameParameter;
    private final String alfioGroupsNameParameter;
    private final String logoutUrl;
    private final String logoutRedirectUrl;

    public OpenIdAuthenticationManager(@Value("${openid.domain}") String domain,
                                       @Value("${openid.clientId}") String clientId,
                                       @Value("${openid.clientSecret}") String clientSecret,
                                       @Value("${openid.callbackURI}") String callbackURI,
                                       @Value("${openid.authenticationUrl}") String authenticationUrl,
                                       @Value("${openid.claimsUrl}") String claimsUrl,
                                       @Value("${openid.contentType}") String contentType,
                                       @Value("${openid.groupsNameParameter}") String groupsNameParameter,
                                       @Value("${openid.alfioGroupsNameParameter}") String alfioGroupsNameParameter,
                                       @Value("${openid.logoutUrl}") String logoutUrl,
                                       @Value("${openid.logoutRedirectUrl}") String logoutRedirectUrl) {
        this.domain = domain;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.callbackURI = callbackURI;
        this.authenticationUrl = authenticationUrl;
        this.claimsUrl = claimsUrl;
        this.contentType = contentType;
        this.groupsNameParameter = groupsNameParameter;
        this.alfioGroupsNameParameter = alfioGroupsNameParameter;
        this.logoutUrl = logoutUrl;
        this.logoutRedirectUrl = logoutRedirectUrl;
    }

    public String buildAuthorizeUrl(List<String> scopes) {
        String scopeParameter = String.join("+", scopes);

        UriComponents uri = UriComponentsBuilder.newInstance()
                .scheme("https")
                .host(domain)
                .path(authenticationUrl)
                .queryParam("redirect_uri", callbackURI)
                .queryParam("client_id", clientId)
                .queryParam("scope", scopeParameter)
                .queryParam("response_type", "code")
                .build();
        return uri.toUriString();
    }

    public String buildClaimsRetrieverUrl() {
        UriComponents uri = UriComponentsBuilder.newInstance()
                .scheme("https")
                .host(domain)
                .path(claimsUrl)
                .build();
        return uri.toUriString();
    }

    public String buildLogoutUrl() {
        UriComponents uri = UriComponentsBuilder.newInstance()
                .scheme("https")
                .host(domain)
                .path(logoutUrl)
                .queryParam("redirect_uri", logoutRedirectUrl)
                .build();
        return uri.toString();
    }

    public String buildRetrieveClaimsUrlBody(String code) throws JsonProcessingException {
        if (contentType.equals(APPLICATION_JSON))
            return buildRetrieveClaimsUrlJsonBody(code);
        if (contentType.equals(APPLICATION_FORM_URLENCODED))
            return buildRetrieveClaimsUrlFormUrlEncodedBody(code);

        String message = "the Content-Type specified is not supported";
        logger.error(message);
        throw new RuntimeException(message);
    }

    private String buildRetrieveClaimsUrlJsonBody(String code) throws JsonProcessingException {
        Map<String, String> body = Map.of(
                "grant_type", "authorization_code",
                "code", code,
                "client_id", clientId,
                "client_secret", clientSecret,
                "redirect_uri", callbackURI
        );

        ObjectMapper objectMapper = new ObjectMapper();

        return objectMapper.writeValueAsString(body);
    }

    private String buildRetrieveClaimsUrlFormUrlEncodedBody(String code) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("grant_type=authorization_code");
        stringBuilder.append("&code=" + code);
        stringBuilder.append("&client_id=" + clientId);
        stringBuilder.append("&client_secret=" + clientSecret);
        stringBuilder.append("&redirect_uri=" + callbackURI);

        return stringBuilder.toString();
    }

    public List<String> getScopes() {
        return List.of("openid", "email", "profile", groupsNameParameter, alfioGroupsNameParameter);
    }

    public String getGroupsNameParameter() {
        return groupsNameParameter;
    }

    public String getAlfioGroupsNameParameter() {
        return alfioGroupsNameParameter;
    }

    public String getCodeNameParameter() {
        return "code";
    }

    public String getAccessTokenNameParameter() {
        return "access_token";
    }

    public String getIdTokenNameParameter() {
        return "id_token";
    }

    public String getSubjectNameParameter() {
        return "sub";
    }

    public String getEmailNameParameter() {
        return "email";
    }

    public String getContentType() {
        return contentType;
    }
}

