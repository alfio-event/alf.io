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
package alfio.manager.support;

import java.util.List;

public class TicketCheckInStatusResult {
    private final String ticketUuid;
    private final String firstName;
    private final String lastName;
    private final CheckInStatus checkInStatus;
    private final List<String> tags;

    public TicketCheckInStatusResult(String ticketUuid, String firstName, String lastName, CheckInStatus checkInStatus, List<String> tags) {
        this.ticketUuid = ticketUuid;
        this.firstName = firstName;
        this.lastName = lastName;
        this.checkInStatus = checkInStatus;
        this.tags = tags;
    }

    public String getTicketUuid() {
        return ticketUuid;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public CheckInStatus getCheckInStatus() {
        return checkInStatus;
    }

    public List<String> getTags() {
        return tags;
    }
}
