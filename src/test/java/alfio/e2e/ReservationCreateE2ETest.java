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
    void createAndEditReservationWithAttendeeFields() {
        adminConsole.login();
        adminConsole.addAttendeeField(slug, "company", "input:text", List.of());
        adminConsole.addAttendeeField(slug, "diet", "select", List.of("vegan", "standard"));
        var attendee = new NewAttendee("Katherine", "Johnson", "katherine-" + uniqueId() + "@example.org");
        var reservationId = adminConsole.createReservationWithAttendeeFields(slug,
            new ReservationContact("Fields", "Contact", "fields-contact@example.org"),
            attendee,
            CATEGORY_NAME,
            "ACME",
            "vegan");
        //
        // the values have been saved with the ticket
        var values = adminConsole.attendeeFieldValues(slug, reservationId);
        assertEquals(List.of("ACME"), values.get("company"));
        assertEquals(List.of("vegan"), values.get("diet"));
        //
        // and can be changed from the "Full data" dialog of the reservation detail
        adminConsole.editAttendeeFields(slug, reservationId, "Initech", "standard");
    }

    @Test
    void createReservationFromCsvWithAttendeeFields() {
        adminConsole.login();
        adminConsole.addAttendeeField(slug, "company", "input:text", List.of());
        adminConsole.addAttendeeField(slug, "diet", "select", List.of("vegan", "standard"));
        // the template has a header: the first three columns are positional, the additional fields are matched by name
        var csv = """
            "firstName","lastName","email","diet","company"
            "Grace","Hopper","grace-%s@example.org","Vegan","ACME, Inc."
            """.formatted(uniqueId());
        var reservationId = adminConsole.createReservationFromCsv(slug,
            new ReservationContact("Csv", "Contact", "csv-contact@example.org"),
            CATEGORY_NAME,
            csv,
            1);
        var tickets = adminConsole.reservationTickets(slug, reservationId);
        assertEquals(List.of("Grace Hopper"), tickets.attendeesByCategory().get(CATEGORY_NAME));
        var values = adminConsole.attendeeFieldValues(slug, reservationId);
        assertEquals(List.of("ACME, Inc."), values.get("company"));
        // the option is saved as configured, whatever the case in the file
        assertEquals(List.of("vegan"), values.get("diet"));
    }

    @Test
    void refuseToSaveIncompleteReservation() {
        adminConsole.login();
        var errors = adminConsole.saveEmptyReservation(slug);
        assertEquals("Please fill in this field", errors.get("contact-first-name"), () -> "unexpected errors: " + errors);
        assertEquals("Please fill in this field", errors.get("contact-email"), () -> "unexpected errors: " + errors);
        assertEquals("Please fill in this field", errors.get("attendee-first-name"), () -> "unexpected errors: " + errors);
        assertEquals("Please select a category", errors.get("existing-category"), () -> "unexpected errors: " + errors);
    }

    @Test
    void createReservationWithDiscountCode() {
        adminConsole.login();
        adminConsole.addDiscountCode(slug, "E2E_HALF_PRICE", 50);
        var attendee = new NewAttendee("Alan", "Turing", "alan-" + uniqueId() + "@example.org");
        var reservationId = adminConsole.createReservationWithDiscount(slug,
            new ReservationContact("Discount", "Contact", "discount-contact@example.org"),
            attendee,
            CATEGORY_NAME,
            "E2E_HALF_PRICE (-50%)");
        //
        // the ticket costs 10.00, VAT included
        var summary = adminConsole.orderSummary(slug, reservationId);
        assertEquals(List.of("E2E_HALF_PRICE"), summary.get("promoCodeRows"), () -> "unexpected summary: " + summary);
        assertEquals("5.00", summary.get("totalPrice"), () -> "unexpected summary: " + summary);
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
