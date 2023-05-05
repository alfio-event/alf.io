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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TicketTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void ticketCode(boolean caseInsensitive) {
        var fullName = "Full Name";
        var email = "email@example.org";
        var regularCase = Ticket.generateHmacTicketInfo("eventKey", caseInsensitive, fullName, email, "id", "uuid");
        var modifiedCase = Ticket.generateHmacTicketInfo("eventKey", caseInsensitive, fullName.toUpperCase(), email.toUpperCase(), "id", "uuid");
        assertEquals(caseInsensitive, regularCase.equals(modifiedCase));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void ticketCodeUTF8Chars(boolean caseInsensitive) {
        var fullName = "çÇĞğIıİiÖöŞşÜü";
        var fullNameSwappedCase = "ÇçğĞıIiİöÖşŞüÜ";
        var email = "email@example.org";
        var regularCase = Ticket.generateHmacTicketInfo("eventKey", caseInsensitive, fullName, email, "id", "uuid");
        var modifiedCase = Ticket.generateHmacTicketInfo("eventKey", caseInsensitive, fullNameSwappedCase, email.toUpperCase(), "id", "uuid");
        assertEquals(caseInsensitive, regularCase.equals(modifiedCase));
    }
}