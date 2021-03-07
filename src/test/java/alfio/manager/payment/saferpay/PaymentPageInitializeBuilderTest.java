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
import alfio.model.Event;
import alfio.model.PurchaseContext;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static alfio.manager.payment.saferpay.PaymentPageInitializeRequestBuilder.SUPPORTED_METHODS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@DisplayName("Payment page initialize")
@ExtendWith(MockitoExtension.class)
class PaymentPageInitializeBuilderTest {
    @Mock
    private PaymentSpecification paymentSpecification;
    @Mock
    private Event event;

    @BeforeEach
    public void init() {
        System.out.println("init");
        when(paymentSpecification.getPurchaseContext()).thenReturn(event);
        when(paymentSpecification.getReservationId()).thenReturn("reservationId");
        when(event.getPublicIdentifier()).thenReturn("shortName");
        when(event.getType()).thenReturn(PurchaseContext.PurchaseContextType.event);
    }

    @Test
    void buildInitializeRequest() {
        var prb = new PaymentPageInitializeRequestBuilder("http://localhost", paymentSpecification)
            .addAuthentication("customerId", "requestId", "terminalId")
            .addOrderInformation("orderId", "1", "CHF", "description", 1)
            .build();

        var parsedJson = JsonParser.parseString(prb).getAsJsonObject();

        assertEquals("terminalId", parsedJson.get("TerminalId").getAsString());
        var payment = parsedJson.get("Payment").getAsJsonObject();
        assertNotNull(payment);

        var amount = payment.get("Amount").getAsJsonObject();

        assertNotNull(amount);
        assertEquals("1", amount.get("Value").getAsString());
        assertEquals("CHF", amount.get("CurrencyCode").getAsString());

        assertEquals("orderId", payment.get("OrderId").getAsString());
        assertEquals("description", payment.get("Description").getAsString());

        // return URLs
        var returnUrls = parsedJson.get("ReturnUrls").getAsJsonObject();
        assertEquals("http://localhost/event/shortName/reservation/reservationId/book", returnUrls.get("Success").getAsString());
        assertEquals("http://localhost/event/shortName/reservation/reservationId/payment/saferpay/cancel", returnUrls.get("Fail").getAsString());

        // Notifications
        var notification = parsedJson.get("Notification").getAsJsonObject();
        assertNotNull(notification);
        assertEquals("http://localhost/api/payment/webhook/saferpay/reservation/reservationId/success", notification.get("NotifyUrl").getAsString());

        // payment methods
        var paymentMethods = parsedJson.get("PaymentMethods").getAsJsonArray();
        for (int i = 0; i < paymentMethods.size(); i++) {
            var element = paymentMethods.get(i);
            assertTrue(SUPPORTED_METHODS.contains(element.getAsString()));
        }

    }
}
