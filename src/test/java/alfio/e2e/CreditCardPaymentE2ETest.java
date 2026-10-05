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

import org.junit.jupiter.api.Test;

class CreditCardPaymentE2ETest extends BaseE2ETest {

    private static final String CUSTOMER_LAST_NAME = "McTest";

    @Test
    void editConfirmedPaymentNotes() throws InterruptedException {
        // the attendee buys a ticket
        reservationFlow.buyTicketWithCreditCard(CUSTOMER_LAST_NAME);
        //
        // the organizer reviews the confirmed payment, and adds a note to it
        adminConsole.login();
        adminConsole.verifyConfirmedPayments(slug, 1);
        adminConsole.editConfirmedPaymentNotes(slug, CUSTOMER_LAST_NAME, "E2E note " + uniqueId());
        adminConsole.verifyPendingPaymentsCount(slug, 0);
    }

    @Test
    void sendMessageToAttendees() throws InterruptedException {
        // the attendee buys a ticket
        reservationFlow.buyTicketWithCreditCard(CUSTOMER_LAST_NAME);
        //
        // the organizer sends a message to the attendees
        adminConsole.login();
        var subject = "E2E message " + uniqueId();
        adminConsole.sendMessageToAttendees(slug, subject, "Hello {{fullName}}, this is a message for {{eventName}}", 1);
        adminConsole.verifyMessageSent(slug, subject);
    }
}
