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
package alfio.model;

import alfio.util.MonetaryUtil;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;

import java.time.ZonedDateTime;

public class ReservationPaymentDetail {
    private final String id;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String paymentMethod;
    private final String paidAmount;
    private final String currencyCode;
    private final String transactionTimestamp;
    private final String transactionNotes;
    private final String invoiceNumber;

    public ReservationPaymentDetail(@Column("tr_id") String id,
                                    @Column("tr_first_name") String firstName,
                                    @Column("tr_last_name") String lastName,
                                    @Column("tr_email_address") String email,
                                    @Column("tr_payment_method") String paymentMethod,
                                    @Column("bt_price_cts") Integer paidAmount,
                                    @Column("bt_currency") String currencyCode,
                                    @Column("bt_t_timestamp") ZonedDateTime transactionTimestamp,
                                    @Column("bt_notes") String transactionNotes,
                                    @Column("tr_invoice_number") String invoiceNumber) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.paymentMethod = paymentMethod;
        this.paidAmount = MonetaryUtil.formatCents(paidAmount, currencyCode);
        this.currencyCode = currencyCode;
        this.transactionTimestamp = transactionTimestamp.toString();
        this.transactionNotes = transactionNotes;
        this.invoiceNumber = invoiceNumber;
    }

    public String getId() {
        return id;
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

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getPaidAmount() {
        return paidAmount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getTransactionTimestamp() {
        return transactionTimestamp;
    }

    public String getTransactionNotes() {
        return transactionNotes;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }
}
