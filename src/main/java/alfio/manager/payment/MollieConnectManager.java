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
package alfio.manager.payment;

import alfio.manager.ExtensionManager;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.system.ConfigurationManager.MaybeConfiguration;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.util.oauth2.AccessTokenResponseDetails;
import alfio.util.oauth2.AuthorizationRequestDetails;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.builder.api.DefaultApi20;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.oauth.OAuth20Service;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static alfio.model.system.ConfigurationKeys.*;

@AllArgsConstructor
@Log4j2
@Component
public class MollieConnectManager implements OAuthPaymentProviderConnector {
    public static final String MOLLIE_CONNECT_REDIRECT_PATH = "/admin/configuration/payment/mollie/authorize";
    private static final String SCOPES = "payments.read payments.write refunds.read refunds.write";
    private final ExtensionManager extensionManager;
    private final ConfigurationManager configurationManager;
    private final HttpClient httpClient;

    @Override
    public AuthorizationRequestDetails getConnectURL(int organizationId) {
        var options = configurationManager.getFor(Set.of(MOLLIE_API_KEY, MOLLIE_CONNECT_CLIENT_ID, MOLLIE_CONNECT_CALLBACK, BASE_URL), ConfigurationLevel.organization(organizationId));
        String callbackURL = options.get(MOLLIE_CONNECT_CALLBACK).getValueOrDefault(options.get(BASE_URL).getRequiredValue() + MOLLIE_CONNECT_REDIRECT_PATH);
        String state = extensionManager.generateOAuth2StateParam(organizationId).orElse(UUID.randomUUID().toString());
        return new AuthorizationRequestDetails(new MollieConnectApi()
            .getAuthorizationUrl("code", options.get(MOLLIE_CONNECT_CLIENT_ID).getRequiredValue(), callbackURL, SCOPES, state, Collections.emptyMap()), state);
    }

    @Override
    public AccessTokenResponseDetails storeConnectedAccountId(String code, int organizationId) {
        try {
            ConfigurationLevel configurationLevel = ConfigurationLevel.organization(organizationId);
            var options = configurationManager.getFor(Set.of(MOLLIE_API_KEY, MOLLIE_CONNECT_CLIENT_ID, MOLLIE_CONNECT_CLIENT_SECRET, MOLLIE_CONNECT_CALLBACK, BASE_URL), configurationLevel);
            OAuth20Service service = new ServiceBuilder(options.get(MOLLIE_CONNECT_CLIENT_ID).getRequiredValue())
                .apiSecret(options.get(MOLLIE_CONNECT_CLIENT_SECRET).getRequiredValue())
                .callback(options.get(MOLLIE_CONNECT_CALLBACK).getRequiredValue())
                .build(new MollieConnectApi());
            OAuth2AccessToken accessTokenResponse = service.getAccessToken(code);
            var refreshToken = accessTokenResponse.getRefreshToken();
            if(refreshToken != null) {
                configurationManager.saveConfig(Configuration.from(organizationId, MOLLIE_CONNECT_REFRESH_TOKEN), refreshToken);
            }
            return new AccessTokenResponseDetails(accessTokenResponse.getAccessToken(), refreshToken, null, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Request interrupted while retrieving access token", e);
            return new AccessTokenResponseDetails(null, null, e.getMessage(), false);
        } catch (Exception e) {
            log.warn("Got exception while retrieving access token", e);
            return new AccessTokenResponseDetails(null, null, e.getMessage(), false);
        }
    }

    public AccessTokenResponseDetails refreshAccessToken(Map<ConfigurationKeys, MaybeConfiguration> options) {
        try {
            OAuth20Service service = new ServiceBuilder(options.get(MOLLIE_CONNECT_CLIENT_ID).getRequiredValue())
                .apiSecret(options.get(MOLLIE_CONNECT_CLIENT_SECRET).getRequiredValue())
                .callback(options.get(MOLLIE_CONNECT_CALLBACK).getRequiredValue())
                .build(new MollieConnectApi());
            String refreshToken = options.get(MOLLIE_CONNECT_REFRESH_TOKEN).getRequiredValue();
            OAuth2AccessToken accessTokenResponse = service.refreshAccessToken(refreshToken);
            return new AccessTokenResponseDetails(accessTokenResponse.getAccessToken(), refreshToken, null, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Request interrupted while retrieving access token", e);
            return new AccessTokenResponseDetails(null, null, e.getMessage(), false);
        } catch (Exception e) {
            log.warn("Got exception while retrieving access token", e);
            return new AccessTokenResponseDetails(null, null, e.getMessage(), false);
        }
    }

    private static class MollieConnectApi extends DefaultApi20 {

        @Override
        public String getAccessTokenEndpoint() {
            return "https://api.mollie.com/oauth2/tokens";
        }

        @Override
        protected String getAuthorizationBaseUrl() {
            return "https://www.mollie.com/oauth2/authorize";
        }
    }
}
