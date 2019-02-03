package alfio.model.transaction.token;

import alfio.model.transaction.PaymentMethod;
import alfio.model.transaction.PaymentProxy;
import alfio.model.transaction.PaymentToken;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class StripeSCACreditCardToken implements PaymentToken {

    private final String paymentIntentId;
    private final String chargeId;
    private final String clientSecret;

    @Override
    public String getToken() {
        return chargeId;
    }

    public String getPaymentIntentId() {
        return paymentIntentId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    @Override
    public PaymentMethod getPaymentMethod() {
        return PaymentMethod.CREDIT_CARD;
    }

    @Override
    public PaymentProxy getPaymentProvider() {
        return PaymentProxy.STRIPE;
    }
}
