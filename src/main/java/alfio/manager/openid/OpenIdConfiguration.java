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
import lombok.Getter;
import lombok.Setter;
import org.springframework.core.env.Environment;

import static java.util.Objects.requireNonNullElse;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.removeEnd;

@Getter
@Setter
public class OpenIdConfiguration {
    private final String domain;
    private final String clientId;
    private final String clientSecret;
    private final String callbackURI;
    private final String authenticationUrl;
    private final String tokenEndpoint;
    private final String givenNameClaim;
    private final String familyNameClaim;
    private final String contentType;
    private final String rolesParameter;
    private final String alfioGroupsParameter;
    private final String logoutUrl;
    private final String logoutRedirectUrl;

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
                               @JsonProperty("logoutRedirectUrl") String logoutRedirectUrl) {
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
    }

    public static OpenIdConfiguration from(Environment environment, ConfigurationManager configurationManager) {
        var baseUrl = removeEnd(configurationManager.getFor(ConfigurationKeys.BASE_URL, ConfigurationLevel.system()).getRequiredValue(), "/");
        return new OpenIdConfiguration(
            environment.getProperty("openid.domain"),
            environment.getProperty("openid.clientId"),
            environment.getProperty("openid.clientSecret"),
            environment.getProperty("openid.callbackURI",baseUrl + "/callback"),
            environment.getProperty("openid.authenticationUrl"),
            environment.getProperty("openid.tokenEndpoint", "/authorize"),
            environment.getProperty("openid.givenNameClaim"),
            environment.getProperty("openid.familyNameClaim"),
            environment.getProperty("openid.contentType", "application/x-www-form-urlencoded"),
            environment.getProperty("openid.rolesParameter"),
            environment.getProperty("openid.alfioGroupsParameter"),
            environment.getProperty("openid.logoutUrl"),
            environment.getProperty("openid.logoutRedirectUrl", baseUrl + "/admin")
        );
    }

    public String toString() {
        return "OpenIdConfiguration(domain=" + this.getDomain()
            + ", clientId=" + (isNotBlank(this.getClientId()) ? "<redacted>" : "missing")
            + ", clientSecret=" + (isNotBlank(this.getClientSecret()) ? "<redacted>" : "missing")
            + ", callbackURI=" + this.getCallbackURI()
            + ", authenticationUrl=" + this.getAuthenticationUrl()
            + ", tokenEndpoint=" + this.getTokenEndpoint()
            + ", contentType=" + this.getContentType()
            + ", rolesParameter=" + this.getRolesParameter()
            + ", alfioGroupsParameter=" + this.getAlfioGroupsParameter()
            + ", logoutUrl=" + this.getLogoutUrl()
            + ", logoutRedirectUrl=" + this.getLogoutRedirectUrl() + ")";
    }
}
