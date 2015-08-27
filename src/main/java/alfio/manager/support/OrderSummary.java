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
package alfio.manager.support;

import alfio.manager.TicketReservationManager;
import lombok.Data;

import java.util.List;

@Data
public class OrderSummary {
    private final TicketReservationManager.TotalPrice originalTotalPrice;
    private final List<SummaryRow> summary;
    private final boolean free;
    private final String totalPrice;
    private final String totalVAT;
    private final boolean waitingForPayment;
    private final boolean cashPayment;

    /* lol jmustache */
    public boolean getFree() {
        return free;
    }

    public boolean getWaitingForPayment() {
        return waitingForPayment;
    }

    public boolean getCashPayment() {
        return cashPayment;
    }

    public boolean getNotYetPaid() {
        return waitingForPayment || cashPayment;
    }

    public int getTicketAmount() {
        return summary.stream().filter(s-> SummaryRow.SummaryType.TICKET == s.getType()).mapToInt(SummaryRow::getAmount).sum();
    }

    public boolean getSingleTicketOrder() {
        return getTicketAmount() == 1;
    }
}
