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
package alfio.model.modification;

import alfio.model.TicketCategory;
import alfio.model.TicketReservationInvoicingAdditionalInfo;
import alfio.model.transaction.PaymentProxy;
import alfio.util.ClockProvider;
import alfio.util.Json;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

@Getter
public class AdminReservationModification implements Serializable {

    private final DateTimeModification expiration;
    private final CustomerData customerData;
    private final List<TicketsInfo> ticketsInfo;
    private final String language;
    private final boolean updateContactData;
    private final boolean updateAdvancedBillingOptions;
    private final AdvancedBillingOptions advancedBillingOptions;
    private final Notification notification;
    private final SubscriptionDetails subscriptionDetails;
    private final UUID linkedSubscriptionId;

    @JsonCreator
    public AdminReservationModification(@JsonProperty("expiration") DateTimeModification expiration,
                                        @JsonProperty("customerData") CustomerData customerData,
                                        @JsonProperty("ticketsInfo") List<TicketsInfo> ticketsInfo,
                                        @JsonProperty("language") String language,
                                        @JsonProperty("updateContactData") Boolean updateContactData,
                                        @JsonProperty("updateAdvancedBillingOptions") Boolean updateAdvancedBillingOptions,
                                        @JsonProperty("advancedBillingOptions") AdvancedBillingOptions advancedBillingOptions,
                                        @JsonProperty("notification") Notification notification,
                                        @JsonProperty("subscriptionDetails") SubscriptionDetails subscriptionDetails,
                                        @JsonProperty("linkedSubscriptionId") UUID linkedSubscriptionId) {
        this.expiration = expiration;
        this.customerData = customerData;
        this.ticketsInfo = ticketsInfo;
        this.language = language;
        this.updateContactData = Boolean.TRUE.equals(updateContactData);
        this.updateAdvancedBillingOptions = Boolean.TRUE.equals(updateAdvancedBillingOptions);
        this.advancedBillingOptions = advancedBillingOptions;
        this.notification = notification;
        this.subscriptionDetails = subscriptionDetails;
        this.linkedSubscriptionId = linkedSubscriptionId;
    }

    @Getter
    public static class CustomerData {
        private final String firstName;
        private final String lastName;
        private final String emailAddress;
        private final String billingAddress;
        private final String userLanguage;
        private final String customerReference;
        private final String vatNr;
        private final String vatCountryCode;
        private final TicketReservationInvoicingAdditionalInfo invoicingAdditionalInfo;
        // null: only the free text "billingAddress" is updated
        private final CustomerBillingDetails billingDetails;

        public CustomerData(String firstName,
                            String lastName,
                            String emailAddress,
                            String billingAddress,
                            String userLanguage,
                            String customerReference,
                            String vatNr,
                            String vatCountryCode,
                            TicketReservationInvoicingAdditionalInfo invoicingAdditionalInfo) {
            this(firstName, lastName, emailAddress, billingAddress, userLanguage, customerReference, vatNr, vatCountryCode, invoicingAdditionalInfo, null);
        }

        @JsonCreator
        public CustomerData(@JsonProperty("firstName") String firstName,
                            @JsonProperty("lastName") String lastName,
                            @JsonProperty("emailAddress") String emailAddress,
                            @JsonProperty("billingAddress") String billingAddress,
                            @JsonProperty("userLanguage") String userLanguage,
                            @JsonProperty("customerReference") String customerReference,
                            @JsonProperty("vatNr") String vatNr,
                            @JsonProperty("vatCountryCode") String vatCountryCode,
                            @JsonProperty("invoicingAdditionalInfo") TicketReservationInvoicingAdditionalInfo invoicingAdditionalInfo,
                            @JsonProperty("billingDetails") CustomerBillingDetails billingDetails) {
            this.firstName = trimToEmpty(firstName);
            this.lastName = trimToEmpty(lastName);
            this.emailAddress = trimToEmpty(emailAddress);
            this.billingAddress = billingAddress;
            this.userLanguage = userLanguage;
            this.customerReference = customerReference;
            this.vatNr = vatNr;
            this.vatCountryCode = vatCountryCode;
            this.invoicingAdditionalInfo = invoicingAdditionalInfo;
            this.billingDetails = billingDetails;
        }

        public String getFullName() {
            return firstName + " " + lastName;
        }
    }

    /**
     * Structured billing information, as collected by the checkout: whether the customer is a company, and its address
     */
    @Getter
    public static class CustomerBillingDetails implements Serializable {
        private final boolean company;
        private final String companyName;
        private final String addressLine1;
        private final String addressLine2;
        private final String zip;
        private final String city;
        private final String state;

        @JsonCreator
        public CustomerBillingDetails(@JsonProperty("company") boolean company,
                              @JsonProperty("companyName") String companyName,
                              @JsonProperty("addressLine1") String addressLine1,
                              @JsonProperty("addressLine2") String addressLine2,
                              @JsonProperty("zip") String zip,
                              @JsonProperty("city") String city,
                              @JsonProperty("state") String state) {
            this.company = company;
            this.companyName = StringUtils.trimToNull(companyName);
            this.addressLine1 = StringUtils.trimToNull(addressLine1);
            this.addressLine2 = StringUtils.trimToNull(addressLine2);
            this.zip = StringUtils.trimToNull(zip);
            this.city = StringUtils.trimToNull(city);
            this.state = StringUtils.trimToNull(state);
        }

        @JsonIgnore
        public String getEffectiveCompanyName() {
            if (company) {
                return companyName;
            }
            return null;
        }

        /**
         * @return true if the address has been entered field by field (otherwise the free text "billingAddress" is used)
         */
        public boolean hasStructuredAddress() {
            return StringUtils.isNotBlank(addressLine1) || StringUtils.isNotBlank(zip) || StringUtils.isNotBlank(city);
        }

        private CustomerBillingDetails summary() {
            return new CustomerBillingDetails(company, placeholderIfNotEmpty(companyName), placeholderIfNotEmpty(addressLine1),
                placeholderIfNotEmpty(addressLine2), placeholderIfNotEmpty(zip), placeholderIfNotEmpty(city), placeholderIfNotEmpty(state));
        }
    }

    @Getter
    public static class AdvancedBillingOptions {
        private final boolean vatApplied;

        @JsonCreator
        public AdvancedBillingOptions(@JsonProperty("vatApplied") String vatApplied) {
            this.vatApplied = "Y".equals(vatApplied);
        }
    }

    @Getter
    public static class TicketsInfo {
        private final Category category;
        private final List<Attendee> attendees;
        private final boolean addSeatsIfNotAvailable;
        private final boolean updateAttendees;

        @JsonCreator
        public TicketsInfo(@JsonProperty("category") Category category,
                           @JsonProperty("attendees") List<Attendee> attendees,
                           @JsonProperty("addSeatsIfNotAvailable") boolean addSeatsIfNotAvailable,
                           @JsonProperty("updateAttendees") Boolean updateAttendees) {
            this.category = category;
            this.attendees = attendees;
            this.addSeatsIfNotAvailable = addSeatsIfNotAvailable;
            this.updateAttendees = Optional.ofNullable(updateAttendees).orElse(false);
        }
    }

    @Getter
    public static class Category {
        private final Integer existingCategoryId;
        private final String name;
        private final BigDecimal price;
        private final TicketCategory.TicketAccessType ticketAccessType;

        @JsonCreator
        public Category(@JsonProperty("existingCategoryId") Integer existingCategoryId,
                        @JsonProperty("name") String name,
                        @JsonProperty("price") BigDecimal price,
                        @JsonProperty("ticketAccessType") TicketCategory.TicketAccessType ticketAccessType) {
            this.existingCategoryId = existingCategoryId;
            this.name = name;
            this.price = price;
            this.ticketAccessType = Objects.requireNonNullElse(ticketAccessType, TicketCategory.TicketAccessType.INHERIT);
        }

        public boolean isExisting() {
            return existingCategoryId != null;
        }
    }

    @Getter
    public static class Attendee {
        private final Integer ticketId;
        private final String firstName;
        private final String lastName;
        private final String emailAddress;
        private final String language;
        private final boolean reassignmentForbidden;
        private final String reference;
        private final UUID subscriptionId;
        private final Map<String, List<String>> additionalInfo;
        private final Map<String, String> metadata;

        @JsonCreator
        public Attendee(@JsonProperty("ticketId") Integer ticketId,
                        @JsonProperty("firstName") String firstName,
                        @JsonProperty("lastName") String lastName,
                        @JsonProperty("emailAddress") String emailAddress,
                        @JsonProperty("language") String language,
                        @JsonProperty("forbidReassignment") Boolean reassignmentForbidden,
                        @JsonProperty("reference") String reference,
                        @JsonProperty("subscriptionId") UUID subscriptionId,
                        @JsonProperty("additionalInfo") Map<String, List<String>> additionalInfo,
                        @JsonProperty("metadata") Map<String, String> metadata) {
            this.ticketId = ticketId;
            this.firstName = trimToEmpty(firstName);
            this.lastName = trimToEmpty(lastName);
            this.emailAddress = trimToEmpty(emailAddress);
            this.language = language;
            this.reassignmentForbidden = Objects.requireNonNullElse(reassignmentForbidden, false);
            this.reference = reference;
            this.subscriptionId = subscriptionId;
            this.additionalInfo = Objects.requireNonNullElse(additionalInfo, Map.of());
            this.metadata = Objects.requireNonNullElse(metadata, Map.of());
        }

        public boolean isEmpty() {
            return StringUtils.isAnyBlank(firstName, lastName, emailAddress);
        }

        public String getFullName() {
            return firstName + " " + lastName;
        }
    }

    @Getter
    public static class Update {
        private final DateTimeModification expiration;
        private final Notification notification;

        public Update(@JsonProperty("expiration") DateTimeModification expiration,
                      @JsonProperty("notification") Notification notification) {
            this.expiration = expiration;
            this.notification = notification;
        }
    }

    @Getter
    public static class Notification {

        public static final Notification EMPTY = new Notification(false, false);

        private final boolean customer;
        private final boolean attendees;

        @JsonCreator
        public Notification(@JsonProperty("customer") boolean customer,
                            @JsonProperty("attendees") boolean attendees) {
            this.customer = customer;
            this.attendees = attendees;
        }

        public static Notification orEmpty(Notification notification) {
            return notification != null ? notification : EMPTY;
        }
    }

    public static class TransactionDetails {

        private final String id;
        private final BigDecimal paidAmount;
        private final LocalDateTime timestamp;
        private final String notes;
        private final PaymentProxy paymentProvider;

        @JsonCreator
        public TransactionDetails(@JsonProperty("id") String id,
                                  @JsonProperty("paidAmount") BigDecimal paidAmount,
                                  @JsonProperty("timestamp") LocalDateTime timestamp,
                                  @JsonProperty("notes") String notes,
                                  @JsonProperty("paymentProvider") PaymentProxy paymentProvider) {
            this.id = id;
            this.paidAmount = paidAmount;
            this.timestamp = timestamp;
            this.notes = notes;
            this.paymentProvider = paymentProvider;
        }

        public String getId() {
            return id;
        }

        public BigDecimal getPaidAmount() {
            return paidAmount;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public String getNotes() {
            return notes;
        }

        public PaymentProxy getPaymentProvider() {
            return paymentProvider;
        }

        public static TransactionDetails admin() {
            return new TransactionDetails(null, null, LocalDateTime.now(ClockProvider.clock()), null, PaymentProxy.ADMIN);
        }
    }

    @Getter
    public static class SubscriptionDetails {
        private final String firstName;
        private final String lastName;
        private final String email;
        private final Integer maxAllowed;
        private final DateTimeModification validityFrom;
        private final DateTimeModification validityTo;

        public SubscriptionDetails(@JsonProperty("firstName") String firstName,
                                   @JsonProperty("lastName") String lastName,
                                   @JsonProperty("email") String email,
                                   @JsonProperty("maxAllowed") Integer maxAllowed,
                                   @JsonProperty("validityFrom") DateTimeModification validityFrom,
                                   @JsonProperty("validityTo") DateTimeModification validityTo) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
            this.maxAllowed = maxAllowed;
            this.validityFrom = validityFrom;
            this.validityTo = validityTo;
        }
    }

    public static String summary(AdminReservationModification src) {
        try {
            List<TicketsInfo> ticketsInfo = src.ticketsInfo.stream().map(ti -> {
                List<Attendee> attendees = ti.getAttendees()
                    .stream()
                    .map(a -> new Attendee(a.ticketId,
                        placeholderIfNotEmpty(a.firstName),
                        placeholderIfNotEmpty(a.lastName),
                        placeholderIfNotEmpty(a.emailAddress),
                        a.language,
                        a.reassignmentForbidden,
                        a.reference,
                        a.subscriptionId,
                        singletonMap("hasAdditionalInfo", singletonList(String.valueOf(a.additionalInfo.isEmpty()))),
                        Map.of())).collect(toList());
                return new TicketsInfo(ti.getCategory(), attendees, ti.isAddSeatsIfNotAvailable(), ti.isUpdateAttendees());
            }).collect(toList());
            return Json.toJson(new AdminReservationModification(src.expiration,
                summaryForCustomerData(src.customerData),
                ticketsInfo,
                src.getLanguage(),
                src.updateContactData,
                src.updateAdvancedBillingOptions,
                src.advancedBillingOptions,
                src.notification,
                src.subscriptionDetails,
                src.linkedSubscriptionId));
        } catch(Exception e) {
            return e.toString();
        }
    }

    private static CustomerData summaryForCustomerData(CustomerData in) {
        if(in != null) {
            return new CustomerData(placeholderIfNotEmpty(in.firstName),
                placeholderIfNotEmpty(in.lastName),
                placeholderIfNotEmpty(in.emailAddress),
                placeholderIfNotEmpty(in.billingAddress),
                placeholderIfNotEmpty(in.userLanguage),
                placeholderIfNotEmpty(in.customerReference),
                placeholderIfNotEmpty(in.vatNr),
                placeholderIfNotEmpty(in.vatCountryCode),
                in.invoicingAdditionalInfo,
                summaryForBillingDetails(in.billingDetails));
        }
        else return null;
    }

    private static CustomerBillingDetails summaryForBillingDetails(CustomerBillingDetails in) {
        if (in == null) {
            return null;
        }
        return in.summary();
    }

    private static String placeholderIfNotEmpty(String in) {
        return StringUtils.isNotEmpty(in) ? "xxx" : null;
    }
}

