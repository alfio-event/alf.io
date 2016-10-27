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

@Getter
public class AdminReservationModification {

    private final DateTimeModification expiration;
    private final CustomerData customerData;
    private final List<TicketsInfo> ticketsInfo;
    private final String language;

    @JsonCreator
    public AdminReservationModification(@JsonProperty DateTimeModification expiration,
                                        @JsonProperty CustomerData customerData,
                                        @JsonProperty List<TicketsInfo> ticketsInfo,
                                        @JsonProperty String language) {
        this.expiration = expiration;
        this.customerData = customerData;
        this.ticketsInfo = ticketsInfo;
        this.language = language;
    }

    @Getter
    public static class CustomerData {
        private final String firstName;
        private final String lastName;
        private final String emailAddress;

        @JsonCreator
        public CustomerData(@JsonProperty String firstName,
                            @JsonProperty String lastName,
                            @JsonProperty String emailAddress) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.emailAddress = emailAddress;
        }
    }

    @Getter
    public static class TicketsInfo {
        private final Category category;
        private final List<Attendee> attendees;
        private final boolean addSeatsIfNotAvailable;

        @JsonCreator
        public TicketsInfo(@JsonProperty Category category,
                           @JsonProperty List<Attendee> attendees,
                           @JsonProperty boolean addSeatsIfNotAvailable) {
            this.category = category;
            this.attendees = attendees;
            this.addSeatsIfNotAvailable = addSeatsIfNotAvailable;
        }
    }

    @Getter
    public static class Category {
        private final Integer existingCategoryId;
        private final String name;
        private final BigDecimal price;

        @JsonCreator
        public Category(@JsonProperty Integer existingCategoryId, @JsonProperty String name, @JsonProperty BigDecimal price) {
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
        private final String firstName;
        private final String lastName;
        private final String emailAddress;

        @JsonCreator
        public Attendee(@JsonProperty String firstName, @JsonProperty String lastName, @JsonProperty String emailAddress) {
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
    public static class Notification {
        private final boolean customer;
        private final boolean attendees;

        @JsonCreator
        public Notification(@JsonProperty boolean customer, @JsonProperty boolean attendees) {
            this.customer = customer;
            this.attendees = attendees;
        }
    }
}

