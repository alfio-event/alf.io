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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReservationDetailE2ETest extends BaseE2ETest {

    private static final String CUSTOMER_LAST_NAME = "McDetail";

    @Test
    void editContactInformation() throws InterruptedException {
        reservationFlow.buyTicketWithCreditCard(CUSTOMER_LAST_NAME);
        adminConsole.login();
        var contact = adminConsole.editReservationContact(slug, CUSTOMER_LAST_NAME, "Edited");
        assertTrue(contact.contains("Edited " + CUSTOMER_LAST_NAME), () -> "unexpected contact information: " + contact);
    }

    @Test
    void editBillingDetails() throws InterruptedException {
        reservationFlow.buyTicketWithCreditCard(CUSTOMER_LAST_NAME);
        adminConsole.login();
        var companyName = "Company " + uniqueId();
        var contact = adminConsole.editReservationBillingDetails(slug, CUSTOMER_LAST_NAME, companyName, "Via Cantonale 2", "6900", "Lugano");
        // the billing address is rebuilt from the structured fields, starting with the company name
        assertTrue(contact.contains("Company\n" + companyName), () -> "company not displayed: " + contact);
        assertTrue(contact.contains(companyName + "\nTest " + CUSTOMER_LAST_NAME + "\nVia Cantonale 2\n6900 Lugano"), () -> "unexpected billing address: " + contact);
    }

    @Test
    void confirmPayment() {
        reservationFlow.buyTicketWithBankTransfer(CUSTOMER_LAST_NAME);
        adminConsole.login();
        assertEquals("COMPLETE", adminConsole.confirmPaymentFromReservationDetail(slug, CUSTOMER_LAST_NAME));
        adminConsole.verifyPendingPaymentsCount(slug, 0);
    }

    @Test
    void cancelReservation() {
        reservationFlow.buyTicketWithBankTransfer(CUSTOMER_LAST_NAME);
        adminConsole.login();
        assertEquals("CANCELLED", adminConsole.cancelReservationFromDetail(slug, CUSTOMER_LAST_NAME));
        adminConsole.verifyPendingPaymentsCount(slug, 0);
    }
}
