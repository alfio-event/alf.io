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
package alfio.model.system.command;

import alfio.manager.payment.PaymentSpecification;
import alfio.model.TicketReservation.TicketReservationStatus;
import alfio.model.transaction.PaymentProxy;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class FinalizeReservation {
    private final PaymentSpecification paymentSpecification;
    private final PaymentProxy paymentProxy;
    private final boolean sendReservationConfirmationEmail;
    private final boolean sendTickets;
    private final String username;
    private final TicketReservationStatus originalStatus;

    @JsonCreator
    public FinalizeReservation(@JsonProperty("paymentSpecification") PaymentSpecification paymentSpecification,
                               @JsonProperty("paymentProxy") PaymentProxy paymentProxy,
                               @JsonProperty("sendReservationConfirmationEmail") boolean sendReservationConfirmationEmail,
                               @JsonProperty("sendTickets") boolean sendTickets,
                               @JsonProperty("username") String username,
                               @JsonProperty("originalStatus") TicketReservationStatus originalStatus) {
        this.paymentSpecification = paymentSpecification;
        this.paymentProxy = paymentProxy;
        this.sendReservationConfirmationEmail = sendReservationConfirmationEmail;
        this.sendTickets = sendTickets;
        this.username = username;
        this.originalStatus = originalStatus;
    }

    public PaymentSpecification getPaymentSpecification() {
        return paymentSpecification;
    }

    public PaymentProxy getPaymentProxy() {
        return paymentProxy;
    }

    public boolean isSendReservationConfirmationEmail() {
        return sendReservationConfirmationEmail;
    }

    public boolean isSendTickets() {
        return sendTickets;
    }

    public String getUsername() {
        return username;
    }

    public TicketReservationStatus getOriginalStatus() {
        return originalStatus;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FinalizeReservation)) {
            return false;
        }
        FinalizeReservation that = (FinalizeReservation) o;
        return sendReservationConfirmationEmail == that.sendReservationConfirmationEmail
            && sendTickets == that.sendTickets
            && paymentSpecification.equals(that.paymentSpecification)
            && paymentProxy == that.paymentProxy
            && Objects.equals(username, that.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(paymentSpecification, paymentProxy, sendReservationConfirmationEmail, sendTickets, username);
    }
}
