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
package alfio.model.support;

import alfio.model.transaction.PaymentProxy;
import alfio.util.MonetaryUtil;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class ReservationInfo {
    private final String id;
    private final String invoiceNumber;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final PaymentProxy paymentType;
    private final Integer finalPriceCts;
    private final String currency;
    private final String confirmationTimestamp;
    private final List<TicketInfo> tickets;

    @JsonCreator
    ReservationInfo(@JsonProperty("id") String id,
                    @JsonProperty("invoiceNumber") String invoiceNumber,
                    @JsonProperty("firstName") String firstName,
                    @JsonProperty("lastName") String lastName,
                    @JsonProperty("email") String email,
                    @JsonProperty("paymentType") PaymentProxy paymentType,
                    @JsonProperty("finalPriceCts") Integer finalPriceCts,
                    @JsonProperty("currency") String currency,
                    @JsonProperty("confirmationTimestamp") String confirmationTimestamp,
                    @JsonProperty("tickets") List<TicketInfo> tickets) {
        this.id = id;
        this.invoiceNumber = invoiceNumber;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.paymentType = paymentType;
        this.finalPriceCts = finalPriceCts;
        this.currency = currency;
        this.confirmationTimestamp = confirmationTimestamp;
        this.tickets = tickets;
    }

    public String getId() {
        return id;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public PaymentProxy getPaymentType() {
        return paymentType;
    }

    public String getFormattedAmount() {
        if (finalPriceCts == null) {
            return "";
        }
        return MonetaryUtil.formatCents(finalPriceCts, currency);
    }

    public String getCurrency() {
        return currency;
    }

    public String getConfirmationTimestamp() {
        return confirmationTimestamp;
    }

    public List<TicketInfo> getTickets() {
        return tickets;
    }
}
