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
package alfio.model.api.v1.admin;

import alfio.model.api.v1.admin.subscription.Owner;

import java.util.List;

public class ReservationDetail {
    private final String id;
    private final ReservationUser user;
    private final List<AttendeesByCategory> tickets;
    private final List<Owner> subscriptionOwners;

    public ReservationDetail(String id,
                             ReservationUser user,
                             List<AttendeesByCategory> tickets,
                             List<Owner> subscriptionOwners) {
        this.id = id;
        this.user = user;
        this.tickets = tickets;
        this.subscriptionOwners = subscriptionOwners;
    }

    public String getId() {
        return id;
    }

    public ReservationUser getUser() {
        return user;
    }

    public List<AttendeesByCategory> getTickets() {
        return tickets;
    }

    public List<Owner> getSubscriptionOwners() {
        return subscriptionOwners;
    }
}
