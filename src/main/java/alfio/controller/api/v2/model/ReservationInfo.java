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
package alfio.controller.api.v2.model;

import alfio.model.BillingDetails;
import alfio.model.OrderSummary;
import alfio.model.SummaryRow.SummaryType;
import alfio.model.TicketReservation.TicketReservationStatus;
import alfio.model.transaction.PaymentMethod;
import alfio.model.transaction.PaymentProxy;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@AllArgsConstructor
@Getter
public class ReservationInfo {
    private final String id;
    private final String shortId;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final long validity;
    private final List<TicketsByTicketCategory> ticketsByCategory;
    private final ReservationInfoOrderSummary orderSummary;


    private final TicketReservationStatus status;
    private final boolean validatedBookingInformation;
    private final Map<String, String> formattedExpirationDate; // map of language -> formatted date

    private final String invoiceNumber;
    private final boolean invoiceRequested;


    private final boolean invoiceOrReceiptDocumentPresent;
    private final boolean paid;
    private final boolean tokenAcquired;
    private final PaymentProxy paymentProxy;


    //billing info from additional info
    private final Boolean addCompanyBillingDetails;
    //
    private final String customerReference;
    private final Boolean skipVatNr;

    private final String billingAddress;

    private final BillingDetails billingDetails;

    //reservation info group related info
    private final boolean containsCategoriesLinkedToGroups;
    //

    private final Map<PaymentMethod, PaymentProxyWithParameters> activePaymentMethods;


    @AllArgsConstructor
    @Getter
    public static class TicketsByTicketCategory {
        private final String name;
        private final List<BookingInfoTicket> tickets;
    }


    @AllArgsConstructor
    public static class BookingInfoTicket {
        private final String uuid;
        private final String firstName;
        private final String lastName;
        private final String email;
        private final String fullName;
        private final String userLanguage;
        private final boolean assigned;
        private final boolean locked;
        private final boolean acquired;
        private final boolean cancellationEnabled;
        private final List<AdditionalField> ticketFieldConfiguration;

        public String getEmail() {
            return email;
        }

        public String getFirstName() {
            return firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public String getUuid() {
            return uuid;
        }

        public String getFullName() {
            return fullName;
        }

        public boolean isAssigned() {
            return assigned;
        }

        public boolean isAcquired() {
            return acquired;
        }

        public String getUserLanguage() {
            return userLanguage;
        }

        public boolean isLocked() { return locked; }

        public boolean isCancellationEnabled() {
            return cancellationEnabled;
        }

        public List<AdditionalField> getTicketFieldConfigurationBeforeStandard() {
            return ticketFieldConfiguration.stream().filter(AdditionalField::isBeforeStandardFields).collect(Collectors.toList());
        }

        public List<AdditionalField> getTicketFieldConfigurationAfterStandard() {
            return ticketFieldConfiguration.stream().filter(tv -> !tv.isBeforeStandardFields()).collect(Collectors.toList());
        }
    }

    @AllArgsConstructor
    @Getter
    public static class AdditionalField {
        private final String name;
        private final String value;
        private final String type;
        private final boolean required;
        private final boolean editable;
        private final Integer minLength;
        private final Integer maxLength;
        private final List<String> restrictedValues;
        private final List<Field> fields;
        private final boolean beforeStandardFields;
        private final Map<String, Description> description;
    }

    @AllArgsConstructor
    @Getter
    public static class Description {
        private final String label;
        private final String placeholder;
        private final Map<String, String> restrictedValuesDescription;
    }

    @AllArgsConstructor
    @Getter
    public static class Field {
        private final int fieldIndex;
        private final String fieldValue;
    }


    @Getter
    public static class ReservationInfoOrderSummary {

        private final List<ReservationInfoOrderSummaryRow> summary;
        private final String totalPrice;
        private final boolean free;
        private final boolean displayVat;
        private final int priceInCents;
        private final String descriptionForPayment;
        private final String totalVAT;
        private final String vatPercentage;
        private final boolean notYetPaid;

        public ReservationInfoOrderSummary(OrderSummary orderSummary) {
            this.summary = orderSummary.getSummary()
                .stream()
                .map(s -> new ReservationInfoOrderSummaryRow(s.getName(), s.getAmount(), s.getPrice(), s.getSubTotal(), s.getType()))
                .collect(Collectors.toList());
            this.totalPrice = orderSummary.getTotalPrice();
            this.free = orderSummary.getFree();
            this.displayVat = orderSummary.getDisplayVat();
            this.priceInCents = orderSummary.getPriceInCents();
            this.descriptionForPayment = orderSummary.getDescriptionForPayment();
            this.totalVAT = orderSummary.getTotalVAT();
            this.vatPercentage = orderSummary.getVatPercentage();
            this.notYetPaid = orderSummary.getNotYetPaid();
        }
    }

    @AllArgsConstructor
    @Getter
    public static class ReservationInfoOrderSummaryRow {
        private final String name;
        private final int amount;
        private final String price;
        private final String subTotal;
        private final SummaryType type;
    }
}
