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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Getter
public class AdminReservationModification {

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

        @JsonCreator
        public CustomerData(@JsonProperty("firstName") String firstName,
                            @JsonProperty("lastName") String lastName,
                            @JsonProperty("emailAddress") String emailAddress) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.emailAddress = emailAddress;
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

        @JsonCreator
        public Attendee(@JsonProperty("ticketId") Integer ticketId,
                        @JsonProperty("firstName") String firstName,
                        @JsonProperty("lastName") String lastName,
                        @JsonProperty("emailAddress") String emailAddress) {
            this.ticketId = ticketId;
            this.firstName = firstName;
            this.lastName = lastName;
            this.emailAddress = emailAddress;
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
}

