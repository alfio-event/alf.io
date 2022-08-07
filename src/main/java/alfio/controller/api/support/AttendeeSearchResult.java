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
package alfio.controller.api.support;

import alfio.model.Ticket;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class AttendeeSearchResult {
    private final String uuid;
    private final String firstName;
    private final String lastName;
    private final String categoryName;
    private final Map<String, List<String>> additionalInfo;
    private final Ticket.TicketStatus ticketStatus;
    private final String amountToPay;

    public AttendeeSearchResult(String uuid,
                                String firstName,
                                String lastName,
                                String categoryName,
                                Map<String, List<String>> additionalInfo,
                                Ticket.TicketStatus ticketStatus,
                                String amountToPay) {
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
        return additionalInfo;
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
