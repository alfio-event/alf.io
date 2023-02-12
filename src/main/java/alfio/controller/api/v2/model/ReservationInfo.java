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
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

    public ReservationInfo(String id,
                           String shortId,
                           String firstName,
                           String lastName,
                           String email,
                           long validity,
                           List<TicketsByTicketCategory> ticketsByCategory,
                           ReservationInfoOrderSummary orderSummary,
                           TicketReservationStatus status,
                           boolean validatedBookingInformation,
                           Map<String, String> formattedExpirationDate,
                           String invoiceNumber,
                           boolean invoiceRequested,
                           boolean invoiceOrReceiptDocumentPresent,
                           boolean paid,
                           boolean tokenAcquired,
                           PaymentProxy paymentProxy,
                           Boolean addCompanyBillingDetails,
                           String customerReference,
                           Boolean skipVatNr,
                           String billingAddress,
                           BillingDetails billingDetails,
                           boolean containsCategoriesLinkedToGroups,
                           Map<PaymentMethod, PaymentProxyWithParameters> activePaymentMethods,
                           List<SubscriptionInfo> subscriptionInfos,
                           ReservationMetadata reservationMetadata) {
        this.id = id;
        this.shortId = shortId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.validity = validity;
        this.ticketsByCategory = ticketsByCategory;
        this.orderSummary = orderSummary;
        this.status = status;
        this.validatedBookingInformation = validatedBookingInformation;
        this.formattedExpirationDate = formattedExpirationDate;
        this.invoiceNumber = invoiceNumber;
        this.invoiceRequested = invoiceRequested;
        this.invoiceOrReceiptDocumentPresent = invoiceOrReceiptDocumentPresent;
        this.paid = paid;
        this.tokenAcquired = tokenAcquired;
        this.paymentProxy = paymentProxy;
        this.addCompanyBillingDetails = addCompanyBillingDetails;
        this.customerReference = customerReference;
        this.skipVatNr = skipVatNr;
        this.billingAddress = billingAddress;
        this.billingDetails = billingDetails;
        this.containsCategoriesLinkedToGroups = containsCategoriesLinkedToGroups;
        this.activePaymentMethods = activePaymentMethods;
        this.subscriptionInfos = subscriptionInfos;
        this.metadata = reservationMetadata;
    }


    public record TicketsByTicketCategory(String name, TicketCategory.TicketAccessType ticketAccessType,
                                          List<BookingInfoTicket> tickets) {
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
                .map(s -> new ReservationInfoOrderSummaryRow(s.name(), s.amount(), s.price(), s.subTotal(), s.type(), s.taxPercentage()))
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



    public record ReservationInfoOrderSummaryRow(String name,
                                                 int amount,
                                                 String price,
                                                 String subTotal,
                                                 SummaryType type,
                                                 String taxPercentage) {
    }


    public record SubscriptionInfo(UUID id, String pin, UsageDetails usageDetails, SubscriptionOwner owner, SubscriptionConfiguration configuration) {
    }


    public record SubscriptionOwner(String firstName, String lastName, String email) {
    }

}
