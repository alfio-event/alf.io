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
 * Covers the polls pages migrated to Lit (frontend/admin/src/event/polls): creating a poll,
 * opening it for voting, the results, and the participants management.
 */
class PollsE2ETest extends BaseE2ETest {

    private static final String CUSTOMER_LAST_NAME = "McPoll";
    private static final String QUESTION = "What is your favorite color?";
    private static final String OPTION = "Blue";

    @Override
    protected EnumSet<PaymentProxy> requiredPaymentProxies() {
        // the event is not free of charge, so it needs at least one payment method
        return EnumSet.of(PaymentProxy.OFFLINE);
    }

    @Test
    void createOpenPollAndManageParticipants() {
        // an attendee that can be allowed to vote in the restricted poll: the ticket must be
        // confirmed (ACQUIRED), otherwise the "Add participants" search does not find it
        reservationFlow.buyTicketWithBankTransfer(CUSTOMER_LAST_NAME);
        adminConsole.login();
        assertEquals("COMPLETE", adminConsole.confirmPaymentFromReservationDetail(slug, CUSTOMER_LAST_NAME));
        var detail = adminConsole.createRestrictedPoll(slug, QUESTION, OPTION);
        // the poll starts as draft: open it, the results show the option with zero votes
        adminConsole.openPollForVoting(detail);
        adminConsole.verifyPollResults(detail, OPTION);
        // manage the participants of the restricted poll: add the attendee, then remove them
        var participants = adminConsole.openPollParticipants(detail);
        adminConsole.addParticipantToPoll(participants, CUSTOMER_LAST_NAME);
        adminConsole.removeParticipantFromPoll(participants, CUSTOMER_LAST_NAME);
    }
}
