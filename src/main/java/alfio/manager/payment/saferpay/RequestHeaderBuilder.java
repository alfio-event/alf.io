package alfio.manager.payment.saferpay;

import com.google.gson.stream.JsonWriter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
class RequestHeaderBuilder {
    private static final String SPEC_VERSION = "1.18";
    private final String customerId;
    private final String requestId;
    private final Integer retryIndicator;

    String build() {
        return
            "  \"RequestHeader\": {\n" + //
            "    \"SpecVersion\": \""+SPEC_VERSION+"\",\n" + //
            "    \"CustomerId\": \""+customerId+"\",\n" + //
            "    \"RequestId\": \""+requestId+"\",\n" + //
            (retryIndicator != null ? "    \"RetryIndicator\": "+retryIndicator+"\n" : "") + //
            "  }";
    }

    @SneakyThrows
    JsonWriter appendTo(JsonWriter writer) {
        return writer.name("RequestHeader").beginObject() //
            .name("SpecVersion").value(SPEC_VERSION) //
            .name("CustomerId").value(customerId) //
            .name("RequestId").value(requestId) //
            .name("RetryIndicator").value(retryIndicator) //
        .endObject();
    }

    @Override
    public String toString() {
        return build();
    }
}
