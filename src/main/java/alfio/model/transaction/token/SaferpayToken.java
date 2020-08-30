package alfio.model.transaction.token;

import alfio.model.transaction.PaymentMethod;
import alfio.model.transaction.PaymentProxy;
import alfio.model.transaction.PaymentToken;

public class SaferpayToken implements PaymentToken {

    private final String token;

    public SaferpayToken(String token) {
        this.token = token;
    }

    @Override
    public String getToken() {
        return token;
    }

    @Override
    public PaymentMethod getPaymentMethod() {
        return PaymentMethod.CREDIT_CARD;
    }

    @Override
    public PaymentProxy getPaymentProvider() {
        return PaymentProxy.SAFERPAY;
    }
}
