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

import alfio.controller.api.support.AdditionalField;
import alfio.controller.api.support.AdditionalServiceWithData;
import alfio.controller.api.support.BookingInfoTicket;
import alfio.model.BillingDetails;
import alfio.model.OrderSummary;
import alfio.model.ReservationMetadata;
import alfio.model.SummaryRow.SummaryType;
import alfio.model.TicketCategory;
import alfio.model.TicketReservation.TicketReservationStatus;
import alfio.model.api.v1.admin.subscription.SubscriptionConfiguration;
import alfio.model.subscription.UsageDetails;
import alfio.model.transaction.PaymentMethod;
import alfio.model.transaction.PaymentProxy;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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

    private final List<SubscriptionInfo> subscriptionInfos;

    private final ReservationMetadata metadata;

    private final List<AdditionalServiceWithData> additionalServiceWithData;


    @AllArgsConstructor
    @Getter
    public static class TicketsByTicketCategory {
        private final String name;
        private final TicketCategory.TicketAccessType ticketAccessType;
        private final List<BookingInfoTicket> tickets;
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
                .map(s -> new ReservationInfoOrderSummaryRow(s.getName(), s.getAmount(), s.getPrice(), s.getSubTotal(), s.getType(), s.getTaxPercentage()))
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
        private final String taxPercentage;
    }

    @Getter
    public static class SubscriptionInfo {
        private final UUID id;
        private final String pin;
        private final UsageDetails usageDetails;
        private final SubscriptionOwner owner;
        private final SubscriptionConfiguration configuration;
        private final List<AdditionalField> additionalFields;

        public SubscriptionInfo(UUID id, String pin, UsageDetails usageDetails, SubscriptionOwner owner, SubscriptionConfiguration configuration, List<AdditionalField> additionalFields) {
            this.id = id;
            this.pin = pin;
            this.usageDetails = usageDetails;
            this.owner = owner;
            this.configuration = configuration;
            this.additionalFields = Objects.requireNonNullElse(additionalFields, List.of());
        }

        public List<AdditionalField> getFieldConfigurationBeforeStandard() {
            return additionalFields.stream().filter(AdditionalField::isBeforeStandardFields).collect(Collectors.toList());
        }

        public List<AdditionalField> getFieldConfigurationAfterStandard() {
            return additionalFields.stream().filter(tv -> !tv.isBeforeStandardFields()).collect(Collectors.toList());
        }
    }

    @AllArgsConstructor
    @Getter
    public static class SubscriptionOwner {
        private final String firstName;
        private final String lastName;
        private final String email;
    }

}
