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
package alfio.e2e;

import alfio.e2e.AdminConsole.NewAttendee;
import alfio.e2e.AdminConsole.ReservationContact;
import alfio.model.transaction.PaymentProxy;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReservationCreateE2ETest extends BaseE2ETest {

    private static final String RESTRICTED_CATEGORY_NAME = "Guests";
    private static final int EMPTY_SEATS = 2;

    @Override
    protected EnumSet<PaymentProxy> requiredPaymentProxies() {
        return EnumSet.of(PaymentProxy.OFFLINE);
    }

    @Test
    void createReservationWithTicketsOfTwoCategories() {
        adminConsole.login();
        var attendee = new NewAttendee("Ada", "Lovelace", "ada-" + uniqueId() + "@example.org");
        var reservationId = adminConsole.createReservation(slug,
            new ReservationContact("Create", "Contact", "create-contact@example.org"),
            attendee,
            CATEGORY_NAME,
            RESTRICTED_CATEGORY_NAME,
            EMPTY_SEATS);
        //
        // the attendee has a ticket of the existing category, and the seats of the new category are waiting for their attendees
        var tickets = adminConsole.reservationTickets(slug, reservationId);
        assertEquals("PENDING", tickets.status());
        assertEquals(Set.of(CATEGORY_NAME, RESTRICTED_CATEGORY_NAME), tickets.attendeesByCategory().keySet());
        assertEquals(List.of(attendee.fullName()), tickets.attendeesByCategory().get(CATEGORY_NAME));
        assertEquals(Collections.nCopies(EMPTY_SEATS, ""), tickets.attendeesByCategory().get(RESTRICTED_CATEGORY_NAME));
    }

    @Test
    void addAttendeeFromCheckIn() {
        adminConsole.login();
        var attendee = new NewAttendee("Grace", "Hopper", "grace-" + uniqueId() + "@example.org");
        adminConsole.addAttendeeFromCheckIn(slug, attendee, CATEGORY_NAME);
        //
        // the reservation has been confirmed: the attendee can be checked in right away
        adminConsole.checkInAttendee(slug, attendee.fullName());
    }
}
