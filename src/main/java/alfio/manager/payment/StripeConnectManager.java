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
import alfio.manager.payment.stripe.StripeConnectResult;
import alfio.manager.payment.stripe.StripeConnectURL;
import alfio.manager.system.ConfigurationManager;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.repository.TicketRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.util.Json;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.builder.api.DefaultApi20;
import com.github.scribejava.core.model.OAuthConfig;
import com.github.scribejava.core.oauth.OAuth20Service;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static alfio.manager.payment.stripe.StripeConnectURL.CONNECT_REDIRECT_PATH;
import static alfio.model.system.ConfigurationKeys.*;

@Component
@Log4j2
public class StripeConnectManager {

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

    public StripeConnectURL getConnectURL(int organizationId) {
        var keyResolver = Configuration.from(organizationId);
        String secret = configurationManager.getRequiredValue(keyResolver.apply(STRIPE_SECRET_KEY));
        String clientId = configurationManager.getRequiredValue(keyResolver.apply(STRIPE_CONNECT_CLIENT_ID));
        String callbackURL = configurationManager.getStringConfigValue(keyResolver.apply(STRIPE_CONNECT_CALLBACK), configurationManager.getRequiredValue(keyResolver.apply(BASE_URL)) + CONNECT_REDIRECT_PATH);
        String state = extensionManager.generateStripeConnectStateParam(organizationId).orElse(UUID.randomUUID().toString());
        String code = UUID.randomUUID().toString();
        OAuthConfig config = new OAuthConfig(clientId, secret, callbackURL, "read_write", null, state, "code", null, null, null);
        return new StripeConnectURL(new StripeConnectApi().getAuthorizationUrl(config, Collections.emptyMap()), state, code);
    }

    public StripeConnectResult storeConnectedAccountId(String code, Function<ConfigurationKeys, Configuration.ConfigurationPathKey> keyResolver) {
        try {
            String clientSecret = baseStripeManager.getSystemApiKey();
            OAuth20Service service = new ServiceBuilder(clientSecret).apiSecret(clientSecret).build(new StripeConnectApi());
            Map<String, String> token = Json.fromJson(service.getAccessToken(code).getRawResponse(), new TypeReference<>() {
            });
            String accountId = token.get("stripe_user_id");
            if(accountId != null) {
                configurationManager.saveConfig(keyResolver.apply(ConfigurationKeys.STRIPE_CONNECTED_ID), accountId);
            }
            return new StripeConnectResult(accountId, accountId != null, token.get("error_message"));
        } catch (Exception e) {
            log.error("cannot retrieve account ID", e);
            return new StripeConnectResult(null, false, e.getMessage());
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
