package alfio.manager.payment.saferpay;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PaymentPageAssertRequestBuilder {
    private String customerId;
    private String requestId;

    private final String token;
    private final int retryIndicator;

    public PaymentPageAssertRequestBuilder addAuthentication(String customerId, String requestId) {
        this.customerId = customerId;
        this.requestId = requestId;
        return this;
    }

    public String build() {
        return "{\n" +
            new RequestHeaderBuilder(customerId, requestId, retryIndicator).build() + ",\n"+
            "  \"Token\": \""+token+"\"\n" +
        "}";
    }

}
