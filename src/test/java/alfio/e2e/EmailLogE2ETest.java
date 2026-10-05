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

class EmailLogE2ETest extends BaseE2ETest {

    private static final String CUSTOMER_LAST_NAME = "McMail";

    @Test
    void viewReservationEmail() throws InterruptedException {
        // the attendee buys a ticket, which generates the confirmation e-mails
        reservationFlow.buyTicketWithCreditCard(CUSTOMER_LAST_NAME);
        //
        // the organizer looks for them in the E-mail log, and opens one
        adminConsole.login();
        adminConsole.viewEmail(slug, CUSTOMER_LAST_NAME);
    }

    @Test
    void viewEmailFromReservationDetail() throws InterruptedException {
        // the attendee buys a ticket, which generates the confirmation e-mails
        reservationFlow.buyTicketWithCreditCard(CUSTOMER_LAST_NAME);
        //
        // the organizer opens the reservation, and one of the e-mails sent for it
        adminConsole.login();
        adminConsole.viewReservationEmail(slug, CUSTOMER_LAST_NAME);
    }
}
