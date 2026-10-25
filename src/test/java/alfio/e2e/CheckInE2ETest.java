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

import alfio.model.transaction.PaymentProxy;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

class CheckInE2ETest extends BaseE2ETest {

    private static final String CUSTOMER_LAST_NAME = "McCheckIn";
    // the contact person is also the attendee (see ReservationFlow)
    private static final String ATTENDEE_NAME = "Test " + CUSTOMER_LAST_NAME;

    @Override
    protected EnumSet<PaymentProxy> requiredPaymentProxies() {
        return EnumSet.of(PaymentProxy.OFFLINE);
    }

    @Test
    void manualCheckInAndRevert() {
        // the attendee buys a ticket by bank transfer, and the organizer confirms the payment
        reservationFlow.buyTicketWithBankTransfer(CUSTOMER_LAST_NAME);
        adminConsole.login();
        adminConsole.confirmPendingPayment(slug, CUSTOMER_LAST_NAME);
        //
        // then checks the attendee in from the admin console, and changes their mind
        adminConsole.checkInAttendee(slug, ATTENDEE_NAME);
        adminConsole.revertCheckIn(slug, ATTENDEE_NAME);
    }
}
