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
import alfio.model.TicketCategory;
import alfio.model.TicketReservation.TicketReservationStatus;
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
                           List<SubscriptionInfo> subscriptionInfos) {
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
    }


    @Getter
    public static class TicketsByTicketCategory {
        private final String name;
        private final TicketCategory.TicketAccessType ticketAccessType;
        private final List<BookingInfoTicket> tickets;

        public TicketsByTicketCategory(String name, TicketCategory.TicketAccessType ticketAccessType, List<BookingInfoTicket> tickets) {
            this.name = name;
            this.ticketAccessType = ticketAccessType;
            this.tickets = tickets;
        }
    }



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
        private final boolean sendMailEnabled;
        private final boolean downloadEnabled;
        private final List<AdditionalField> ticketFieldConfiguration;
        private final Map<String, String> formattedOnlineCheckInDate;
        private final boolean onlineEventStarted;

        public BookingInfoTicket(String uuid,
                                 String firstName,
                                 String lastName,
                                 String email,
                                 String fullName,
                                 String userLanguage,
                                 boolean assigned,
                                 boolean locked,
                                 boolean acquired,
                                 boolean cancellationEnabled,
                                 boolean sendMailEnabled,
                                 boolean downloadEnabled,
                                 List<AdditionalField> ticketFieldConfiguration,
                                 Map<String, String> formattedOnlineCheckInDate,
                                 boolean onlineEventStarted) {
            this.uuid = uuid;
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
            this.fullName = fullName;
            this.userLanguage = userLanguage;
            this.assigned = assigned;
            this.locked = locked;
            this.acquired = acquired;
            this.cancellationEnabled = cancellationEnabled;
            this.sendMailEnabled = sendMailEnabled;
            this.downloadEnabled = downloadEnabled;
            this.ticketFieldConfiguration = ticketFieldConfiguration;
            this.formattedOnlineCheckInDate = formattedOnlineCheckInDate;
            this.onlineEventStarted = onlineEventStarted;
        }

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

        public Map<String, String> getFormattedOnlineCheckInDate() {
            return formattedOnlineCheckInDate;
        }

        public boolean isOnlineEventStarted() {
            return onlineEventStarted;
        }

        public boolean isSendMailEnabled() {
            return sendMailEnabled;
        }

        public boolean isDownloadEnabled() {
            return downloadEnabled;
        }
    }

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

        public AdditionalField(String name, String value, String type, boolean required, boolean editable, Integer minLength, Integer maxLength, List<String> restrictedValues, List<Field> fields, boolean beforeStandardFields, Map<String, Description> description) {
            this.name = name;
            this.value = value;
            this.type = type;
            this.required = required;
            this.editable = editable;
            this.minLength = minLength;
            this.maxLength = maxLength;
            this.restrictedValues = restrictedValues;
            this.fields = fields;
            this.beforeStandardFields = beforeStandardFields;
            this.description = description;
        }
    }


    @Getter
    public static class Description {
        private final String label;
        private final String placeholder;
        private final Map<String, String> restrictedValuesDescription;

        public Description(String label, String placeholder, Map<String, String> restrictedValuesDescription) {
            this.label = label;
            this.placeholder = placeholder;
            this.restrictedValuesDescription = restrictedValuesDescription;
        }
    }


    @Getter
    public static class Field {
        private final int fieldIndex;
        private final String fieldValue;

        public Field(int fieldIndex, String fieldValue) {
            this.fieldIndex = fieldIndex;
            this.fieldValue = fieldValue;
        }
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


    @Getter
    public static class ReservationInfoOrderSummaryRow {
        private final String name;
        private final int amount;
        private final String price;
        private final String subTotal;
        private final SummaryType type;
        private final String taxPercentage;

        public ReservationInfoOrderSummaryRow(String name, int amount, String price, String subTotal, SummaryType type, String taxPercentage) {
            this.name = name;
            this.amount = amount;
            this.price = price;
            this.subTotal = subTotal;
            this.type = type;
            this.taxPercentage = taxPercentage;
        }
    }

    @Getter
    public static class SubscriptionInfo {
        private final UUID id;
        private final String pin;
        private final UsageDetails usageDetails;
        private final SubscriptionOwner owner;

        public SubscriptionInfo(UUID id, String pin, UsageDetails usageDetails, SubscriptionOwner owner) {
            this.id = id;
            this.pin = pin;
            this.usageDetails = usageDetails;
            this.owner = owner;
        }
    }

    @Getter
    public static class SubscriptionOwner {
        private final String firstName;
        private final String lastName;
        private final String email;

        public SubscriptionOwner(String firstName, String lastName, String email) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
        }
    }

}
