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
}