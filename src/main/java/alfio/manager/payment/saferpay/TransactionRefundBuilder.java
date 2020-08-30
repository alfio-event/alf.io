package alfio.manager.payment.saferpay;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TransactionRefundBuilder {
    private final String captureId;
    private final int retryIndicator;
    private String customerId;
    private String requestId;

    public TransactionRefundBuilder addAuthentication(String customerId, String requestId) {
        this.customerId = customerId;
        this.requestId = requestId;
        return this;
    }

    public String build(String amountToRefund, String currencyCode) {
        return "{\n" +
            new RequestHeaderBuilder(customerId, requestId, retryIndicator).build() + ",\n"+
            "\"Refund\": {\n" +
            "  \"Amount\": {\n" +
            "    \"Value\": \""+amountToRefund+"\",\n" +
            "    \"CurrencyCode\": \""+currencyCode+"\"\n" +
            "  }\n" +
            "},\n" +
            "\"CaptureReference\": {\n" +
            "  \"CaptureId\": \""+captureId+"\"\n" +
            " }"+
        "}";
    }
}
