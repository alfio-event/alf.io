package alfio.manager.payment.saferpay;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TransactionInquireRequestBuilder {
    private final String transactionId;
    private final int retryIndicator;
    private String customerId;
    private String requestId;

    public TransactionInquireRequestBuilder addAuthentication(String customerId, String requestId) {
        this.customerId = customerId;
        this.requestId = requestId;
        return this;
    }

    public String build() {
        return "{\n" +
            new RequestHeaderBuilder(customerId, requestId, retryIndicator).build() + ",\n"+
            "\"TransactionReference\": {" +
            "  \"TransactionId\": \""+transactionId+"\"\n" +
            "}" +
        "}";
    }
}
