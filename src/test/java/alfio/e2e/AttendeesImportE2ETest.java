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

import alfio.e2e.AdminConsole.ReservationContact;
import alfio.model.transaction.PaymentProxy;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttendeesImportE2ETest extends BaseE2ETest {

    private static final Duration TIMEOUT = Duration.ofMinutes(5);
    // quoted values may contain commas and (doubled) quotes
    private static final String ATTENDEE_WITH_QUOTED_NAME = "Alan, Mathison Turing";
    private static final String UTF8_BOM = "\uFEFF";
    private static final String TEMPLATE_HEADER = "\"firstName\",\"lastName\",\"email\",\"lang\",\"reference\",\"company\"";
    private static final List<String> ATTENDEES = List.of(
        "Ada,Lovelace,ada@example.org,en,REF-1,Analytical Engines",
        "Grace,Hopper,grace@example.org,en,REF-2,\"Navy \"\"Research\"\", Lab\"",
        "\"Alan, Mathison\",Turing,alan@example.org,en,,Bletchley Park"
    );

    @Override
    protected EnumSet<PaymentProxy> requiredPaymentProxies() {
        return EnumSet.of(PaymentProxy.OFFLINE);
    }

    @Test
    void importAttendeesInASingleReservation() {
        adminConsole.login();
        adminConsole.addAttendeeField(slug, "company", "input:text", List.of());
        //
        // the template contains a column for each attendees' data to collect: fill it in and import it
        var template = adminConsole.downloadAttendeesImportTemplate(slug);
        assertTrue(template.startsWith(UTF8_BOM), "the template must start with the UTF-8 byte order mark");
        var templateHeader = template.substring(UTF8_BOM.length()).strip();
        assertEquals(TEMPLATE_HEADER, templateHeader);
        adminConsole.importAttendees(slug, CATEGORY_NAME, attendeesCsv(templateHeader), ATTENDEES.size(),
            new ReservationContact("Import", "Contact", "import-contact@example.org"), TIMEOUT);
        //
        // the imported attendees can be checked in
        var checkIn = adminConsole.openCheckIn(slug);
        adminConsole.waitForAcquiredTickets(checkIn, TIMEOUT);
        adminConsole.waitForCheckInCounts(checkIn, ATTENDEES.size(), 0, TIMEOUT);
        adminConsole.checkInAttendee(slug, ATTENDEE_WITH_QUOTED_NAME);
    }

    private static Path attendeesCsv(String header) {
        try {
            var file = Files.createTempFile("e2e-import", ".csv");
            file.toFile().deleteOnExit();
            return Files.writeString(file, header + "\r\n" + String.join("\r\n", ATTENDEES), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
