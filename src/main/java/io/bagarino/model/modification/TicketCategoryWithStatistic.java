/**
 * This file is part of bagarino.
 *
 * bagarino is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bagarino is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.bagarino.model.modification;

import io.bagarino.manager.EventManager;
import io.bagarino.model.SpecialPrice;
import io.bagarino.model.TicketCategory;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Getter
public class TicketCategoryWithStatistic {

    private final TicketCategory ticketCategory;
    private final int soldTickets;
    private final BigDecimal soldTicketsPercent;
    private final List<SpecialPrice> tokenStatus;

    public TicketCategoryWithStatistic(TicketCategory ticketCategory,
                                       int soldTickets,
                                       List<SpecialPrice> tokenStatus) {
        this.ticketCategory = ticketCategory;
        this.soldTickets = soldTickets;
        this.tokenStatus = tokenStatus;
        this.soldTicketsPercent = BigDecimal.valueOf(soldTickets).divide(BigDecimal.valueOf(ticketCategory.getMaxTickets()), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal getNotSoldTicketsPercent() {
        return EventManager.HUNDRED.subtract(soldTicketsPercent);
    }

    public int getNotSoldTickets() {
        return ticketCategory.getMaxTickets() - soldTickets;
    }

    public boolean isAccessRestricted() {
        return ticketCategory.isAccessRestricted();
    }
}
