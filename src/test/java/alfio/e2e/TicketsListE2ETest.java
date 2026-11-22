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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the tickets page of a category migrated to Lit (frontend/admin/src/event/tickets-list):
 * the list of the tickets of the category, the lock action and the removal of a ticket.
 */
class TicketsListE2ETest extends BaseE2ETest {

    private static final String CUSTOMER_LAST_NAME = "McTickets";

    @Override
    protected EnumSet<PaymentProxy> requiredPaymentProxies() {
        // the event is not free of charge, so it needs at least one payment method
        return EnumSet.of(PaymentProxy.OFFLINE);
    }

    @Test
    void ticketsPageListsLocksAndRemovesTicket() {
        reservationFlow.buyTicketWithBankTransfer(CUSTOMER_LAST_NAME);
        adminConsole.login();
        var page = adminConsole.openCategoryTickets(slug);
        adminConsole.verifyTicketsPageTitle(page, CATEGORY_NAME);
        // the pending ticket of the reservation is listed, with its status
        assertEquals("PENDING", adminConsole.ticketStatus(page, CUSTOMER_LAST_NAME));
        // locking the ticket turns the button into "Unlock"
        adminConsole.lockTicket(page, CUSTOMER_LAST_NAME);
        // removing it through the confirmation dialog empties the list
        adminConsole.removeTicketFromList(page, CUSTOMER_LAST_NAME);
    }
}
