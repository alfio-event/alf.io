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

import alfio.util.Json;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;

@Getter
public class AdminReservationModification implements Serializable {

    private final DateTimeModification expiration;
    private final CustomerData customerData;
    private final List<TicketsInfo> ticketsInfo;
    private final String language;
    private final boolean updateContactData;
    private final Notification notification;

    @JsonCreator
    public AdminReservationModification(@JsonProperty("expiration") DateTimeModification expiration,
                                        @JsonProperty("customerData") CustomerData customerData,
                                        @JsonProperty("ticketsInfo") List<TicketsInfo> ticketsInfo,
                                        @JsonProperty("language") String language,
                                        @JsonProperty("updateContactData") boolean updateContactData,
                                        @JsonProperty("notification") Notification notification) {
        this.expiration = expiration;
        this.customerData = customerData;
        this.ticketsInfo = ticketsInfo;
        this.language = language;
        this.updateContactData = Optional.ofNullable(updateContactData).orElse(false);
        this.notification = notification;
    }

    @Getter
    public static class CustomerData {
        private final String firstName;
        private final String lastName;
        private final String emailAddress;
        private final String billingAddress;
        private final String userLanguage;
        private final String customerReference;

        @JsonCreator
        public CustomerData(@JsonProperty("firstName") String firstName,
                            @JsonProperty("lastName") String lastName,
                            @JsonProperty("emailAddress") String emailAddress,
                            @JsonProperty("billingAddress") String billingAddress,
                            @JsonProperty("userLanguage") String userLanguage,
                            @JsonProperty("customerReference") String customerReference) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.emailAddress = emailAddress;
            this.billingAddress = billingAddress;
            this.userLanguage = userLanguage;
            this.customerReference = customerReference;
        }

        public String getFullName() {
            return firstName + " " + lastName;
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

        @JsonCreator
        public Category(@JsonProperty("existingCategoryId") Integer existingCategoryId,
                        @JsonProperty("name") String name,
                        @JsonProperty("price") BigDecimal price) {
            this.existingCategoryId = existingCategoryId;
            this.name = name;
            this.price = price;
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
        private final Map<String, List<String>> additionalInfo;

        @JsonCreator
        public Attendee(@JsonProperty("ticketId") Integer ticketId,
                        @JsonProperty("firstName") String firstName,
                        @JsonProperty("lastName") String lastName,
                        @JsonProperty("emailAddress") String emailAddress,
                        @JsonProperty("language") String language,
                        @JsonProperty("forbidReassignment") Boolean reassignmentForbidden,
                        @JsonProperty("reference") String reference,
                        @JsonProperty("additionalInfo") Map<String, List<String>> additionalInfo) {
            this.ticketId = ticketId;
            this.firstName = firstName;
            this.lastName = lastName;
            this.emailAddress = emailAddress;
            this.language = language;
            this.reassignmentForbidden = Optional.ofNullable(reassignmentForbidden).orElse(false);
            this.reference = reference;
            this.additionalInfo = Optional.ofNullable(additionalInfo).orElse(Collections.emptyMap());
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
        private final boolean customer;
        private final boolean attendees;

        @JsonCreator
        public Notification(@JsonProperty("customer") boolean customer,
                            @JsonProperty("attendees") boolean attendees) {
            this.customer = customer;
            this.attendees = attendees;
        }
    }

    public static String summary(AdminReservationModification src) {
        try {
            List<TicketsInfo> ticketsInfo = src.ticketsInfo.stream().map(ti -> {
                List<Attendee> attendees = ti.getAttendees()
                    .stream()
                    .map(a -> new Attendee(a.ticketId, placeholderIfNotEmpty(a.firstName), placeholderIfNotEmpty(a.lastName), placeholderIfNotEmpty(a.emailAddress), a.language, a.reassignmentForbidden, a.reference,singletonMap("hasAdditionalInfo", singletonList(String.valueOf(a.additionalInfo.isEmpty()))))).collect(toList());
                return new TicketsInfo(ti.getCategory(), attendees, ti.isAddSeatsIfNotAvailable(), ti.isUpdateAttendees());
            }).collect(toList());
            return Json.toJson(new AdminReservationModification(src.expiration, summaryForCustomerData(src.customerData), ticketsInfo, src.getLanguage(), src.updateContactData, src.notification));
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
                placeholderIfNotEmpty(in.customerReference));
        }
        else return null;
    }

    private static String placeholderIfNotEmpty(String in) {
        return StringUtils.isNotEmpty(in) ? "xxx" : null;
    }
}

