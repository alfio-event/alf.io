package alfio.manager.payment;

import alfio.util.oauth2.AccessTokenResponseDetails;
import alfio.util.oauth2.AuthorizationRequestDetails;

public interface OAuthPaymentProviderConnector {
    AuthorizationRequestDetails getConnectURL(int organizationId);
    AccessTokenResponseDetails storeConnectedAccountId(String code, int organizationId);
}
