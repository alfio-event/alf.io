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

import alfio.controller.support.TicketDecorator;
import alfio.model.TicketFieldConfigurationDescriptionAndValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
@Getter
public class BookingInfo {
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
        private final List<TicketFieldConfigurationDescriptionAndValue> ticketFieldConfiguration;

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

        public List<TicketFieldConfigurationDescriptionAndValue> getTicketFieldConfigurationBeforeStandard() {
            return ticketFieldConfiguration.stream().filter(TicketFieldConfigurationDescriptionAndValue::isBeforeStandardFields).collect(Collectors.toList());
        }

        public List<TicketFieldConfigurationDescriptionAndValue> getTicketFieldConfigurationAfterStandard() {
            return ticketFieldConfiguration.stream().filter(tv -> !tv.isBeforeStandardFields()).collect(Collectors.toList());
        }
    }
}
