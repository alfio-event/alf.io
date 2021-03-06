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
package alfio.manager.payment.saferpay;

import alfio.manager.payment.PaymentSpecification;
import alfio.model.transaction.PaymentMethod;
import com.google.gson.stream.JsonWriter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.StringWriter;
import java.util.Set;

public class PaymentPageInitializeRequestBuilder {
    public static final String WEBHOOK_URL_TEMPLATE = "/api/payment/webhook/saferpay/reservation/{reservationId}/success";
    public static final String SUCCESS_URL_TEMPLATE = "/{purchaseContextType}/{purchaseContextIdentifier}/reservation/{reservationId}";
    public static final String CANCEL_URL_TEMPLATE = "/{purchaseContextType}/{purchaseContextIdentifier}/reservation/{reservationId}/payment/saferpay/cancel";

    static final Set<String> SUPPORTED_METHODS = Set.of(
        PaymentMethod.ALIPAY.name(),

        //CREDIT_CARD
        "AMEX",
        "VISA",
        "VPAY",
        "DINERS",
        "BONUS",
        "JCB",
        "MAESTRO",
        "MASTERCARD",
        "POSTCARD",

        PaymentMethod.POSTFINANCE.name(),
        PaymentMethod.TWINT.name()
    );

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
        var purchaseContextType = paymentSpecification.getPurchaseContext().getType().getUrlComponent();
        var purchaseContextIdentifier = paymentSpecification.getPurchaseContext().getPublicIdentifier();
        var reservationId = paymentSpecification.getReservationId();
        var eventUrl = cleanBaseUrl + expandUriTemplate(SUCCESS_URL_TEMPLATE, purchaseContextType, purchaseContextIdentifier, reservationId);
        this.successURL = eventUrl + "/book";
        this.failureURL = cleanBaseUrl + expandUriTemplate(CANCEL_URL_TEMPLATE, purchaseContextType, purchaseContextIdentifier, reservationId);
        this.notifyURL  = cleanBaseUrl + expandUriTemplate(WEBHOOK_URL_TEMPLATE, reservationId);
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
        var requestHeaderBuilder = new RequestHeaderBuilder(customerId, requestId, retryIndicator);
        try (var writer = new JsonWriter(out)) {
            // @formatter:off
            addPaymentMethods(requestHeaderBuilder.appendTo(writer.beginObject()) //
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
                .endObject() //
                .name("Notification").beginObject() //
                    .name("NotifyUrl").value(notifyURL) //
                .endObject()) //
            .endObject();
            // @formatter:on
        }
        return out.toString();
    }

    @SneakyThrows
    private JsonWriter addPaymentMethods(JsonWriter writer) {
        var array = writer.name("PaymentMethods").beginArray();
        for (String method : SUPPORTED_METHODS) {
            array.value(method);
        }
        return array.endArray();
    }

    private String expandUriTemplate(String template, String reservationId) {
        return UriComponentsBuilder.fromPath(template).buildAndExpand(reservationId).toUriString();
    }

    private String expandUriTemplate(String template, String purchaseContextType, String purchaseContextIdentifier, String reservationId) {
        return UriComponentsBuilder.fromPath(template).buildAndExpand(purchaseContextType, purchaseContextIdentifier, reservationId).toUriString();
    }

}
