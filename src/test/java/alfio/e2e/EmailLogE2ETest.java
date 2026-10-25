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

class EmailLogE2ETest extends BaseE2ETest {

    private static final String CUSTOMER_LAST_NAME = "McMail";

    @Override
    protected EnumSet<PaymentProxy> requiredPaymentProxies() {
        return EnumSet.of(PaymentProxy.OFFLINE);
    }

    @Test
    void viewReservationEmail() {
        buyTicketAndConfirmPayment();
        //
        // the organizer looks for them in the E-mail log, and opens one
        adminConsole.viewEmail(slug, CUSTOMER_LAST_NAME);
    }

    @Test
    void viewEmailFromReservationDetail() {
        buyTicketAndConfirmPayment();
        //
        // the organizer opens the reservation, and one of the e-mails sent for it
        adminConsole.viewReservationEmail(slug, CUSTOMER_LAST_NAME);
    }

    /**
     * The attendee buys a ticket by bank transfer, then the organizer confirms the payment,
     * which generates the confirmation e-mails
     */
    private void buyTicketAndConfirmPayment() {
        reservationFlow.buyTicketWithBankTransfer(CUSTOMER_LAST_NAME);
        adminConsole.login();
        adminConsole.confirmPendingPayment(slug, CUSTOMER_LAST_NAME);
    }
}
