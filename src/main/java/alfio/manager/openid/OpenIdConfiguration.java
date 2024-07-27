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

import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.model.system.ConfigurationKeys;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNullElse;
import static org.apache.commons.lang3.StringUtils.removeEnd;

public record OpenIdConfiguration(
    String domain,
    String clientId,
    String clientSecret,
    String callbackURI,
    String authenticationUrl,
    String tokenEndpoint,
    String givenNameClaim,
    String familyNameClaim,
    String contentType,
    String rolesParameter,
    String alfioGroupsParameter,
    String logoutUrl,
    String logoutRedirectUrl,
    String jwksPath
) {
    @JsonCreator
    public OpenIdConfiguration(@JsonProperty("domain") String domain,
                               @JsonProperty("clientId") String clientId,
                               @JsonProperty("clientSecret") String clientSecret,
                               @JsonProperty("callbackURI") String callbackURI,
                               @JsonProperty("authenticationUrl") String authenticationUrl,
                               @JsonProperty("tokenEndpoint") String tokenEndpoint,
                               @JsonProperty("givenNameClaim") String givenNameClaim,
                               @JsonProperty("familyNameClaim") String familyNameClaim,
                               @JsonProperty("contentType") String contentType,
                               @JsonProperty("rolesParameter") String rolesParameter,
                               @JsonProperty("alfioGroupsParameter") String alfioGroupsParameter,
                               @JsonProperty("logoutUrl") String logoutUrl,
                               @JsonProperty("logoutRedirectUrl") String logoutRedirectUrl,
                               @JsonProperty("jwksPath") String jwksPath) {
        this.domain = domain;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.callbackURI = callbackURI;
        this.authenticationUrl = authenticationUrl;
        this.tokenEndpoint = tokenEndpoint;
        this.givenNameClaim = requireNonNullElse(givenNameClaim, "given_name");
        this.familyNameClaim = requireNonNullElse(familyNameClaim, "family_name");
        this.contentType = requireNonNullElse(contentType, "application/x-www-form-urlencoded");
        this.rolesParameter = rolesParameter;
        this.alfioGroupsParameter = alfioGroupsParameter;
        this.logoutUrl = logoutUrl;
        this.logoutRedirectUrl = logoutRedirectUrl;
        this.jwksPath = requireNonNullElse(jwksPath, "/.well-known/jwks.json");
    }

    public ClientRegistration toClientRegistration(String registrationId,
                                                   String redirectUri,
                                                   boolean fullScopeList) {
        var baseURI = UriComponentsBuilder.newInstance()
            .scheme("https")
            .host(domain);
        var scopes = new ArrayList<>(List.of("openid", "email", "profile"));
        if (fullScopeList) {
            scopes.add("openid");
            scopes.add(rolesParameter);
            scopes.add(alfioGroupsParameter);
            scopes.add(givenNameClaim);
            scopes.add(familyNameClaim);
        }

        return ClientRegistration.withRegistrationId(registrationId)
            .clientId(clientId)
            .clientSecret(clientSecret)
            .redirectUri(redirectUri)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .scope(scopes)
            .authorizationUri(baseURI.replacePath(authenticationUrl).toUriString())
            .jwkSetUri(baseURI.replacePath(jwksPath).toUriString())
            .tokenUri(baseURI.replacePath(tokenEndpoint).toUriString())
            .build();
    }

    public static OpenIdConfiguration from(Environment environment, ConfigurationManager configurationManager) {
        var baseUrl = removeEnd(configurationManager.getFor(ConfigurationKeys.BASE_URL, ConfigurationLevel.system()).getRequiredValue(), "/");
        return new OpenIdConfiguration(
            environment.getProperty("openid.domain"),
            environment.getProperty("openid.clientId"),
            environment.getProperty("openid.clientSecret"),
            environment.getProperty("openid.callbackURI", baseUrl + "/callback"),
            environment.getProperty("openid.authenticationUrl"),
            environment.getProperty("openid.tokenEndpoint", "/authorize"),
            environment.getProperty("openid.givenNameClaim"),
            environment.getProperty("openid.familyNameClaim"),
            environment.getProperty("openid.contentType", "application/x-www-form-urlencoded"),
            environment.getProperty("openid.rolesParameter"),
            environment.getProperty("openid.alfioGroupsParameter"),
            environment.getProperty("openid.logoutUrl"),
            environment.getProperty("openid.logoutRedirectUrl", baseUrl + "/admin"),
            environment.getProperty("openid.jwksPath")
        );
    }
}
