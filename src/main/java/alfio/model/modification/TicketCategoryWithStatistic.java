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
package alfio.model.modification;

import alfio.model.Event;
import alfio.model.PriceContainer;
import alfio.model.SpecialPrice;
import alfio.model.TicketCategory;
import alfio.util.MonetaryUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.experimental.Delegate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

@Getter
public class TicketCategoryWithStatistic implements Comparable<TicketCategoryWithStatistic>, StatisticsContainer, PriceContainer {

    @Delegate
    @JsonIgnore
    private final TicketCategory ticketCategory;
    private final int soldTickets;
    private final BigDecimal soldTicketsPercent;
    private final List<TicketWithStatistic> tickets;
    private final List<SpecialPrice> tokenStatus;
    private final int checkedInTickets;
    private final int pendingTickets;
    @JsonIgnore
    private final Event event;

    private final Map<String, String> description;

    public TicketCategoryWithStatistic(TicketCategory ticketCategory,
                                       List<TicketWithStatistic> tickets,
                                       List<SpecialPrice> tokenStatus,
                                       Event event,
                                       Map<String, String> description) {
        this.ticketCategory = ticketCategory;
        this.tickets = tickets.stream().filter(tc -> tc.hasBeenSold() || tc.isStuck()).collect(toList());
        this.pendingTickets = (int) tickets.stream().filter(TicketWithStatistic::isPending).count();
        this.checkedInTickets = (int) this.tickets.stream().filter(TicketWithStatistic::isCheckedIn).count();
        this.soldTickets = ((int) this.tickets.stream().filter(TicketWithStatistic::hasBeenSold).count()) - checkedInTickets;
        this.tokenStatus = tokenStatus;
        this.event = event;
        this.soldTicketsPercent = calcSoldTicketsPercent(ticketCategory, soldTickets);
        this.description = description;
    }

    public BigDecimal getNotSoldTicketsPercent() {
        return MonetaryUtil.HUNDRED.subtract(soldTicketsPercent);
    }

    @Override
    public int getNotSoldTickets() {
        if(!ticketCategory.isBounded()) {
            return 0;
        }
        return ticketCategory.getMaxTickets() - soldTickets - checkedInTickets - pendingTickets;
    }

    @Override
    public int getNotAllocatedTickets() {
        return 0;
    }

    @Override
    public int getDynamicAllocation() {
        return 0;
    }

    public boolean isExpired() {
        return ZonedDateTime.now(event.getZoneId()).isAfter(ticketCategory.getExpiration(event.getZoneId()));
    }

    public boolean isContainingOrphans() {
        return isExpired() && getNotSoldTickets() > 0;
    }

    public boolean isContainingStuckTickets() {
        return tickets.stream().anyMatch(TicketWithStatistic::isStuck);
    }

    public boolean isContainingTickets() {
        return !tickets.isEmpty();
    }

    @Override
    public int compareTo(TicketCategoryWithStatistic o) {
        return getExpiration(event.getZoneId()).compareTo(o.getExpiration(event.getZoneId()));
    }

    private static BigDecimal calcSoldTicketsPercent(TicketCategory ticketCategory, int soldTickets) {
        int maxTickets = Math.max(1, ticketCategory.getMaxTickets());
        return BigDecimal.valueOf(soldTickets).divide(BigDecimal.valueOf(maxTickets), 2, RoundingMode.HALF_UP).multiply(MonetaryUtil.HUNDRED);
    }

    public String getFormattedInception() {
        return getInception(event.getZoneId()).format(EventWithStatistics.JSON_DATE_FORMATTER);
    }

    public String getFormattedExpiration() {
        return getExpiration(event.getZoneId()).format(EventWithStatistics.JSON_DATE_FORMATTER);
    }

    public BigDecimal getActualPrice() {
        return getFinalPrice();
    }

    @Override
    public String getCurrencyCode() {
        return event.getCurrency();
    }

    @Override
    public Optional<BigDecimal> getOptionalVatPercentage() {
        return Optional.ofNullable(event.getVat());
    }

    @Override
    public VatStatus getVatStatus() {
        return event.getVatStatus();
    }
}
