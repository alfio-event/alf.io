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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PendingPaymentsE2ETest extends BaseE2ETest {

    private static final String CUSTOMER_LAST_NAME = "McTransfer";

    /**
     * Runs after the event has been set up: the attendee buys a ticket and pays by bank transfer,
     * then the organizer gets notified about the pending payment.
     */
    @BeforeEach
    void buyTicketWithBankTransfer() {
        reservationFlow.buyTicketWithBankTransfer(CUSTOMER_LAST_NAME);
        adminConsole.login();
        adminConsole.verifyPendingPaymentsCount(slug, 1);
    }

    @Test
    void confirmPendingPayment() {
        adminConsole.filterPendingPayments(slug, CUSTOMER_LAST_NAME);
        adminConsole.confirmPendingPayment(slug, CUSTOMER_LAST_NAME);
        adminConsole.verifyPendingPaymentsCount(slug, 0);
        adminConsole.verifyConfirmedPayments(slug, 1);
    }

    @Test
    void bulkConfirmPendingPayment() {
        // the organizer uploads the list of received payments
        adminConsole.bulkConfirmPendingPayment(slug, CUSTOMER_LAST_NAME);
        adminConsole.verifyPendingPaymentsCount(slug, 0);
        adminConsole.verifyConfirmedPayments(slug, 1);
    }

    @Test
    void deletePendingPayment() {
        // the organizer deletes a reservation which has not been paid
        adminConsole.deletePendingPayment(slug, CUSTOMER_LAST_NAME);
        adminConsole.verifyPendingPaymentsCount(slug, 0);
    }
}
