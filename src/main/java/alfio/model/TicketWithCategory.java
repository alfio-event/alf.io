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
package alfio.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record TicketWithCategory(@JsonIgnore Ticket ticket, @JsonIgnore TicketCategory category) implements TicketInfoContainer {

    public String getCategoryName() {
        return category != null ? category.getName() : null;
    }

    @Override
    public boolean getAssigned() {
        return ticket.getAssigned();
    }

    @Override
    public boolean isCheckedIn() {
        return ticket.isCheckedIn();
    }

    @Override
    public int getId() {
        return ticket.getId();
    }

    @Override
    public String getUuid() {
        return ticket.getUuid();
    }

    @Override
    public int getEventId() {
        return ticket.getEventId();
    }

    @Override
    public String getTicketsReservationId() {
        return ticket.getTicketsReservationId();
    }

    @Override
    public String getFirstName() {
        return ticket.getFirstName();
    }

    @Override
    public String getLastName() {
        return ticket.getLastName();
    }

    @Override
    public String getEmail() {
        return ticket.getEmail();
    }

    @Override
    public String getUserLanguage() {
        return ticket.getUserLanguage();
    }

    @Override
    public Integer getCategoryId() {
        return ticket.getCategoryId();
    }

    public String getFullName() {
        return ticket.getFullName();
    }
}
