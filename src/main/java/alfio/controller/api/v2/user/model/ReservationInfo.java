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
package alfio.controller.api.v2.user.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@AllArgsConstructor
@Getter
public class ReservationInfo {
    private final String firstName;
    private final String lastName;
    private final String email;
    List<TicketsByTicketCategory> ticketsByCategory;


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
        private final boolean assigned;
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
        private final int minLength;
        private final int maxLength;
        private final List<String> restrictedValues;
        private final List<Field> fields;

        private final boolean beforeStandardFields;

        private final boolean inputField;
        private final boolean euVat;
        private final boolean textareaField;
        private final boolean countryField;
        private final boolean selectField;

        private final Map<String, Description> description;

        //tmp
        private final String labelDescription;

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
}
