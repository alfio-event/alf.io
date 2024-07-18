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
import java.util.UUID;

public record AttendeeSearchResults(
    int totalResults,
    int checkedIn,
    int totalPages,
    int numPage,
    List<AttendeeResult> attendees
) {

    public boolean hasMorePages() {
        return numPage < totalPages - 1;
    }

    public record AttendeeResult(
        String uuid,
        UUID publicUUID,
        String firstName,
        String lastName,
        String categoryName,
        Map<String, List<String>> additionalInfo,
        Ticket.TicketStatus ticketStatus,
        String amountToPay
    ) {

        @Override
        public Map<String, List<String>> additionalInfo() {
                return Objects.requireNonNullElse(additionalInfo, Map.of());
            }
        }
}
