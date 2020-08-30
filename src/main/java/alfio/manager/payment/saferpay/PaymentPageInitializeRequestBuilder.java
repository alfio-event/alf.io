package alfio.manager.payment.saferpay;

import alfio.manager.payment.PaymentSpecification;
import com.google.gson.stream.JsonWriter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.StringWriter;

public class PaymentPageInitializeRequestBuilder {
    public static final String WEBHOOK_URL_TEMPLATE = "/api/payment/webhook/saferpay/event/{eventShortName}/reservation/{reservationId}/success";
    private static final String SUCCESS_URL_TEMPLATE = "/event/{eventShortName}/reservation/{reservationId}";
    private String customerId;
    private String requestId;
    private int retryIndicator;

    private String terminalId;
    private String formattedAmount;
    private String currencyCode;
    private String orderId;
    private String description;

    private final String successURL;
    private final String failureURL;
    private final String notifyURL;

    public PaymentPageInitializeRequestBuilder(String baseUrl, PaymentSpecification paymentSpecification) {
        var cleanBaseUrl = StringUtils.removeEnd(baseUrl, "/");
        var eventName = paymentSpecification.getEvent().getShortName();
        var reservationId = paymentSpecification.getReservationId();
        var eventUrl = cleanBaseUrl + expandUriTemplate(SUCCESS_URL_TEMPLATE, eventName, reservationId);
        this.successURL = eventUrl + "/book";
        this.failureURL = eventUrl + "/book";
        this.notifyURL  = cleanBaseUrl + expandUriTemplate(WEBHOOK_URL_TEMPLATE, eventName, reservationId);
    }

    public PaymentPageInitializeRequestBuilder addAuthentication(String customerId, String requestId, String terminalId) {
        this.customerId = customerId;
        this.requestId = requestId;
        this.terminalId = terminalId;
        return this;
    }

    public PaymentPageInitializeRequestBuilder addOrderInformation(String orderId,
                                                                   String formattedAmount,
                                                                   String currencyCode,
                                                                   String description,
                                                                   int numTry) {
        this.orderId = orderId;
        this.formattedAmount = formattedAmount;
        this.currencyCode = currencyCode;
        this.description = description;
        this.retryIndicator = numTry;
        return this;
    }

    @SneakyThrows
    public String build() {
        var out = new StringWriter();
        try (var writer = new JsonWriter(out)) {
            new RequestHeaderBuilder(customerId, requestId, retryIndicator).appendTo(writer.beginObject()) //
                .name("TerminalId").value(terminalId) //
                .name("Payment").beginObject() //
                    .name("Amount").beginObject() //
                        .name("Value").value(formattedAmount) //
                        .name("CurrencyCode").value(currencyCode) //
                    .endObject() //
                    .name("OrderId").value(orderId) //
                    .name("Description").value(description) //
                .endObject() //
                .name("ReturnUrls").beginObject() //
                    .name("Success").value(successURL) //
                    .name("Fail").value(failureURL) //
                .endObject()
                .name("Notification").beginObject()
                    .name("NotifyUrl").value(notifyURL)
                .endObject()
            .endObject();
        }
        return out.toString();
    }

    private String expandUriTemplate(String template, String eventName, String reservationId) {
        return UriComponentsBuilder.fromPath(template).buildAndExpand(eventName, reservationId).toUriString();
    }

}
