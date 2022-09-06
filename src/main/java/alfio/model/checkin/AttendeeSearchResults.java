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
package alfio.model.checkin;

import alfio.model.Ticket;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AttendeeSearchResults {
    private final int totalResults;
    private final int checkedIn;
    private final int totalPages;
    private final int numPage;
    private final List<Attendee> attendees;

    public AttendeeSearchResults(int totalResults,
                                 int checkedIn,
                                 int totalPages,
                                 int numPage,
                                 List<Attendee> attendees) {
        this.totalResults = totalResults;
        this.checkedIn = checkedIn;
        this.totalPages = totalPages;
        this.numPage = numPage;
        this.attendees = attendees;
    }

    public int getTotalResults() {
        return totalResults;
    }

    public int getCheckedIn() {
        return checkedIn;
    }

    public List<Attendee> getAttendees() {
        return attendees;
    }

    public boolean hasMorePages() {
        return numPage < totalPages - 1;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public int getNumPage() {
        return numPage;
    }

    public static class Attendee {
        private final String uuid;
        private final String firstName;
        private final String lastName;
        private final String categoryName;
        private final Map<String, List<String>> additionalInfo;
        private final Ticket.TicketStatus ticketStatus;
        private final String amountToPay;

        public Attendee(String uuid,
                        String firstName, String lastName, String categoryName, Map<String, List<String>> additionalInfo, Ticket.TicketStatus ticketStatus, String amountToPay) {
            this.uuid = uuid;
            this.firstName = firstName;
            this.lastName = lastName;
            this.categoryName = categoryName;
            this.additionalInfo = additionalInfo;
            this.ticketStatus = ticketStatus;
            this.amountToPay = amountToPay;
        }

        public String getFirstName() {
            return firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public String getCategoryName() {
            return categoryName;
        }

        public Map<String, List<String>> getAdditionalInfo() {
            return Objects.requireNonNullElse(additionalInfo, Map.of());
        }

        public Ticket.TicketStatus getTicketStatus() {
            return ticketStatus;
        }

        public String getAmountToPay() {
            return amountToPay;
        }

        public String getUuid() {
            return uuid;
        }
    }
}
