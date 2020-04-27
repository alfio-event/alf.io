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

import alfio.model.system.ConfigurationKeys;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.core.env.Environment;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.removeEnd;

@Getter
@Setter
@AllArgsConstructor
public class OpenIdConfiguration {
    private final String domain;
    private final String clientId;
    private final String clientSecret;
    private final String callbackURI;
    private final String authenticationUrl;
    private final String tokenEndpoint;
    private final String contentType;
    private final String rolesParameter;
    private final String alfioGroupsParameter;
    private final String logoutUrl;
    private final String logoutRedirectUrl;

    public static OpenIdConfiguration from(Environment environment, ConfigurationManager configurationManager) {
        var baseUrl = removeEnd(configurationManager.getFor(ConfigurationKeys.BASE_URL, ConfigurationLevel.system()).getRequiredValue(), "/");
        return new OpenIdConfiguration(
            environment.getProperty("openid.domain"),
            environment.getProperty("openid.clientId"),
            environment.getProperty("openid.clientSecret"),
            environment.getProperty("openid.callbackURI",baseUrl + "/callback"),
            environment.getProperty("openid.authenticationUrl"),
            environment.getProperty("openid.tokenEndpoint", "/authorize"),
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
