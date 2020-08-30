package alfio.manager.payment.saferpay;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TransactionCaptureRequestBuilder {

    private final String token;
    private final int retryIndicator;
    private String customerId;
    private String requestId;

    public TransactionCaptureRequestBuilder addAuthentication(String customerId, String requestId) {
        this.customerId = customerId;
        this.requestId = requestId;
        return this;
    }

    public String build() {
        return "{\n" +
            new RequestHeaderBuilder(customerId, requestId, retryIndicator).build() + ",\n"+
            "  \"TransactionReference\": {\n" +
            "    \"TransactionId\": \""+token+"\"\n" +
            "  }\n" +
            "}";
    }
}
