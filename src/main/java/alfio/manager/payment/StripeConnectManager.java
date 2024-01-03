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
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.repository.TicketRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.util.Json;
import alfio.util.oauth2.AccessTokenResponseDetails;
import alfio.util.oauth2.AuthorizationRequestDetails;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.builder.api.DefaultApi20;
import com.github.scribejava.core.oauth.OAuth20Service;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static alfio.model.system.ConfigurationKeys.*;

@Component
@Log4j2
public class StripeConnectManager implements OAuthPaymentProviderConnector {

    public static final String STRIPE_CONNECT_REDIRECT_PATH = "/admin/configuration/payment/stripe/authorize";
    private final ExtensionManager extensionManager;
    private final ConfigurationManager configurationManager;
    private final BaseStripeManager baseStripeManager;

    public StripeConnectManager(ExtensionManager extensionManager,
                                ConfigurationManager configurationManager,
                                ConfigurationRepository configurationRepository,
                                TicketRepository ticketRepository,
                                Environment environment) {
        this.extensionManager = extensionManager;
        this.configurationManager = configurationManager;
        this.baseStripeManager = new BaseStripeManager(configurationManager, configurationRepository, ticketRepository, environment);
    }

    @Override
    public AuthorizationRequestDetails getConnectURL(int organizationId) {
        var options = configurationManager.getFor(Set.of(STRIPE_CONNECT_CLIENT_ID, STRIPE_CONNECT_CALLBACK, BASE_URL), ConfigurationLevel.organization(organizationId));
        String clientId = options.get(STRIPE_CONNECT_CLIENT_ID).getRequiredValue();
        String callbackURL = options.get(STRIPE_CONNECT_CALLBACK).getValueOrDefault(options.get(BASE_URL).getRequiredValue() + STRIPE_CONNECT_REDIRECT_PATH);
        String state = extensionManager.generateOAuth2StateParam(organizationId).orElse(UUID.randomUUID().toString());
        return new AuthorizationRequestDetails(new StripeConnectApi()
            .getAuthorizationUrl("code", clientId, callbackURL, "read_write", state, Collections.emptyMap()), state);
    }

    @Override
    public AccessTokenResponseDetails storeConnectedAccountId(String code, int organizationId) {
        try {
            String clientSecret = baseStripeManager.getSystemSecretKey();
            OAuth20Service service = new ServiceBuilder(clientSecret).apiSecret(clientSecret).build(new StripeConnectApi());
            Map<String, String> token = Json.fromJson(service.getAccessToken(code).getRawResponse(), new TypeReference<>() {});
            String accountId = token.get("stripe_user_id");
            if(accountId != null) {
                configurationManager.saveConfig(Configuration.from(organizationId, ConfigurationKeys.STRIPE_CONNECTED_ID), accountId);
            }
            return new AccessTokenResponseDetails(accountId, null, token.get("error_message"), accountId != null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Request interrupted while retrieving access token", e);
            return new AccessTokenResponseDetails(null, null, e.getMessage(), false);
        } catch (Exception e) {
            log.error("cannot retrieve account ID", e);
            return new AccessTokenResponseDetails(null, null, e.getMessage(), false);
        }
    }

    private static class StripeConnectApi extends DefaultApi20 {

        @Override
        public String getAccessTokenEndpoint() {
            return "https://connect.stripe.com/oauth/token";
        }

        @Override
        protected String getAuthorizationBaseUrl() {
            return "https://connect.stripe.com/oauth/authorize";
        }
    }
}
