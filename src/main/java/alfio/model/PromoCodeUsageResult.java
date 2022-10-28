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

import alfio.model.transaction.PaymentProxy;
import alfio.util.Json;
import alfio.util.MonetaryUtil;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class PromoCodeUsageResult {

    private final String promoCode;
    private final EventInfo event;
    private final List<ReservationInfo> reservations;

    public PromoCodeUsageResult(@Column("promo_code") String promoCode,
                                @Column("event_short_name") String eventShortName,
                                @Column("event_display_name") String eventDisplayName,
                                @Column("reservations") String reservationsJson) {
        this.promoCode = promoCode;
        this.event = new EventInfo(eventShortName, eventDisplayName);
        List<ReservationInfo> parsed = Json.fromJson(reservationsJson, new TypeReference<>() {});
        this.reservations = parsed.stream()
            .sorted(Comparator.comparing(ReservationInfo::getConfirmationTimestamp))
            .collect(Collectors.toList());
    }

    public String getPromoCode() {
        return promoCode;
    }

    public EventInfo getEvent() {
        return event;
    }

    public List<ReservationInfo> getReservations() {
        return reservations;
    }

    public static class ReservationInfo {
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

    public static class EventInfo {
        private final String shortName;
        private final String displayName;

        @JsonCreator
        private EventInfo(@JsonProperty("shortName") String shortName,
                          @JsonProperty("displayName") String displayName) {
            this.shortName = shortName;
            this.displayName = displayName;
        }

        public String getShortName() {
            return shortName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getPublicIdentifier() {
            return getShortName();
        }
    }

    public static class TicketInfo {
        private final String id;
        private final String firstName;
        private final String lastName;
        private final String type;

        @JsonCreator
        private TicketInfo(@JsonProperty("id") String id,
                           @JsonProperty("firstName") String firstName,
                           @JsonProperty("lastName") String lastName,
                           @JsonProperty("type") String type) {
            this.id = id;
            this.firstName = firstName;
            this.lastName = lastName;
            this.type = type;
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

        public String getType() {
            return type;
        }
    }
}
