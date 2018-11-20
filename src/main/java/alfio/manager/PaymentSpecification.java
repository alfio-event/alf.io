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
package alfio.manager;

import alfio.model.CustomerName;
import alfio.model.Event;
import alfio.model.OrderSummary;
import alfio.model.PriceContainer;
import alfio.model.transaction.PaymentContext;
import alfio.model.transaction.PaymentToken;
import lombok.Getter;

import java.util.Locale;

@Getter
public class PaymentSpecification {
    private final String reservationId;
    private final PaymentToken gatewayToken;
    private final int priceWithVAT;
    private final Event event;
    private final String email;
    private final CustomerName customerName;
    private final String billingAddress;
    private final String customerReference;
    private final Locale locale;
    private final boolean invoiceRequested;
    private final boolean postponeAssignment;
    private final OrderSummary orderSummary;
    private final String vatCountryCode;
    private final String vatNr;
    private final PriceContainer.VatStatus vatStatus;
    private final boolean tcAccepted;
    private final boolean privacyAccepted;

    public PaymentSpecification( String reservationId,
                                 PaymentToken gatewayToken,
                                 int priceWithVAT,
                                 Event event,
                                 String email,
                                 CustomerName customerName,
                                 String billingAddress,
                                 String customerReference,
                                 Locale locale,
                                 boolean invoiceRequested,
                                 boolean postponeAssignment,
                                 OrderSummary orderSummary,
                                 String vatCountryCode,
                                 String vatNr,
                                 PriceContainer.VatStatus vatStatus,
                                 boolean tcAccepted,
                                 boolean privacyAccepted) {
        this.reservationId = reservationId;
        this.gatewayToken = gatewayToken;
        this.priceWithVAT = priceWithVAT;
        this.event = event;
        this.email = email;
        this.customerName = customerName;
        this.billingAddress = billingAddress;
        this.customerReference = customerReference;
        this.locale = locale;
        this.invoiceRequested = invoiceRequested;
        this.postponeAssignment = postponeAssignment;
        this.orderSummary = orderSummary;
        this.vatCountryCode = vatCountryCode;
        this.vatNr = vatNr;
        this.vatStatus = vatStatus;
        this.tcAccepted = tcAccepted;
        this.privacyAccepted = privacyAccepted;
    }

    PaymentSpecification( String reservationId, PaymentToken gatewayToken, int priceWithVAT, Event event, String email, CustomerName customerName ) {
        this(reservationId, gatewayToken, priceWithVAT, event, email, customerName, null, null, null, false, false, null, null, null, null, false, false);
    }

    public PaymentContext getPaymentContext() {
        return new PaymentContext(event);
    }
}
