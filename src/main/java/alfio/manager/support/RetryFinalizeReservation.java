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
package alfio.manager.support;

import alfio.model.TicketReservation.TicketReservationStatus;
import alfio.model.system.command.FinalizeReservation;
import alfio.model.transaction.PaymentProxy;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RetryFinalizeReservation {

    private final String reservationId;
    private final PaymentProxy paymentProxy;
    private final boolean sendReservationConfirmationEmail;
    private final boolean sendTickets;
    private final String username;
    private final boolean tcAccepted;
    private final boolean privacyPolicyAccepted;
    private final TicketReservationStatus originalStatus;

    @JsonCreator
    public RetryFinalizeReservation(@JsonProperty("reservationId") String reservationId,
                                    @JsonProperty("paymentProxy") PaymentProxy paymentProxy,
                                    @JsonProperty("sendReservationConfirmationEmail") boolean sendReservationConfirmationEmail,
                                    @JsonProperty("sendTickets") boolean sendTickets,
                                    @JsonProperty("username") String username,
                                    @JsonProperty("tcAccepted") boolean tcAccepted,
                                    @JsonProperty("privacyPolicyAccepted") boolean privacyPolicyAccepted,
                                    @JsonProperty("originalStatus") TicketReservationStatus originalStatus) {
        this.reservationId = reservationId;
        this.paymentProxy = paymentProxy;
        this.sendReservationConfirmationEmail = sendReservationConfirmationEmail;
        this.sendTickets = sendTickets;
        this.username = username;
        this.tcAccepted = tcAccepted;
        this.privacyPolicyAccepted = privacyPolicyAccepted;
        this.originalStatus = originalStatus;
    }

    public String getReservationId() {
        return reservationId;
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

    public boolean isTcAccepted() {
        return tcAccepted;
    }

    public boolean isPrivacyPolicyAccepted() {
        return privacyPolicyAccepted;
    }

    public TicketReservationStatus getOriginalStatus() {
        return originalStatus;
    }

    public static RetryFinalizeReservation fromFinalizeReservation(FinalizeReservation finalizeReservation) {
        var paymentSpecification = finalizeReservation.getPaymentSpecification();
        return new RetryFinalizeReservation(paymentSpecification.getReservationId(),
            finalizeReservation.getPaymentProxy(),
            finalizeReservation.isSendReservationConfirmationEmail(),
            finalizeReservation.isSendTickets(),
            finalizeReservation.getUsername(),
            paymentSpecification.isTcAccepted(),
            paymentSpecification.isPrivacyAccepted(),
            finalizeReservation.getOriginalStatus()
        );
    }
}
