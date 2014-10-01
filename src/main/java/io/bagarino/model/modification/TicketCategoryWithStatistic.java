package io.bagarino.model.modification;

import io.bagarino.manager.EventManager;
import io.bagarino.model.TicketCategory;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
public class TicketCategoryWithStatistic {

    private final TicketCategory ticketCategory;
    private final int soldTickets;
    private final BigDecimal soldTicketsPercent;

    public TicketCategoryWithStatistic(TicketCategory ticketCategory,
                                       int soldTickets) {
        this.ticketCategory = ticketCategory;
        this.soldTickets = soldTickets;
        this.soldTicketsPercent = BigDecimal.valueOf(soldTickets).divide(BigDecimal.valueOf(ticketCategory.getMaxTickets()), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal getNotSoldTicketsPercent() {
        return EventManager.HUNDRED.subtract(soldTicketsPercent);
    }

    public int getNotSoldTickets() {
        return ticketCategory.getMaxTickets() - soldTickets;
    }
}
