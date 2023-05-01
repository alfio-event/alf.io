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
package alfio.model;

import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.StatisticsContainer;
import alfio.model.modification.TicketWithStatistic;
import alfio.model.system.Configuration;
import alfio.util.ClockProvider;
import alfio.util.MonetaryUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Delegate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@AllArgsConstructor
@Getter
public class TicketCategoryWithAdditionalInfo implements StatisticsContainer, PriceContainer {

    @JsonIgnore
    private final Event event;

    @JsonIgnore
    @Delegate
    private final TicketCategory ticketCategory;

    @JsonIgnore
    private final TicketCategoryStatisticView ticketCategoryStatisticView;

    private final Map<String, String> description;

    private final List<SpecialPrice> tokenStatus;

    private final AlfioMetadata metadata;

    //TODO: to remove it
    @Deprecated
    private final List<TicketWithStatistic> tickets = Collections.emptyList();

    @JsonIgnore
    public Event getEvent() {
        return event;
    }

    @JsonIgnore
    public TicketCategory getTicketCategory() {
        return ticketCategory;
    }

    @JsonIgnore
    public TicketCategoryStatisticView getTicketCategoryStatisticView() {
        return ticketCategoryStatisticView;
    }


    public String getFormattedInception() {
        return getInception(event.getZoneId()).format(EventStatistic.JSON_DATE_FORMATTER);
    }

    public String getFormattedExpiration() {
        return getExpiration(event.getZoneId()).format(EventStatistic.JSON_DATE_FORMATTER);
    }

    public String getFormattedValidCheckInFrom() {
        return getValidCheckInFrom() != null ? getValidCheckInFrom(event.getZoneId()).format(EventStatistic.JSON_DATE_FORMATTER) : null;
    }

    public String getFormattedValidCheckInTo() {
        return getValidCheckInTo() != null ? getValidCheckInTo(event.getZoneId()).format(EventStatistic.JSON_DATE_FORMATTER) : null;
    }

    public String getFormattedTicketValidityStart() {
        return getTicketValidityStart() != null ? getTicketValidityStart(event.getZoneId()).format(EventStatistic.JSON_DATE_FORMATTER) : null;
    }

    public String getFormattedTicketValidityEnd() {
        return getTicketValidityEnd() != null ? getTicketValidityEnd(event.getZoneId()).format(EventStatistic.JSON_DATE_FORMATTER) : null;
    }

    private static BigDecimal calcSoldTicketsPercent(TicketCategory ticketCategory, int soldTickets) {
        int maxTickets = Math.max(1, ticketCategory.getMaxTickets());
        return BigDecimal.valueOf(soldTickets).divide(BigDecimal.valueOf(maxTickets), 2, RoundingMode.HALF_UP).multiply(MonetaryUtil.HUNDRED);
    }

    public BigDecimal getSoldTicketsPercent() {
        return calcSoldTicketsPercent(ticketCategory, getSoldTickets());
    }

    public BigDecimal getNotSoldTicketsPercent() {
        return MonetaryUtil.HUNDRED.subtract(getSoldTicketsPercent());
    }

    @Override
    public int getNotAllocatedTickets() {
        return 0;
    }

    @Override
    public int getDynamicAllocation() {
        return 0;
    }

    @Override
    public int getNotSoldTickets() {
        return ticketCategoryStatisticView.getNotSoldTicketsCount();
    }

    @Override
    public int getSoldTickets() {
        return ticketCategoryStatisticView.getSoldTicketsCount();
    }

    @Override
    public int getCheckedInTickets() {
        return ticketCategoryStatisticView.getCheckedInCount();
    }

    @Override
    public int getPendingTickets() {
        return ticketCategoryStatisticView.getPendingCount();
    }

    @Override
    public int getReleasedTickets() {
        return ticketCategoryStatisticView.getReleasedTicketsCount();
    }

    public boolean isExpired() {
        return event.now(ClockProvider.clock()).isAfter(ticketCategory.getExpiration(event.getZoneId()));
    }

    public boolean isContainingOrphans() {
        return ticketCategoryStatisticView.isContainsOrphanTickets();
    }

    public boolean isContainingStuckTickets() {
        return ticketCategoryStatisticView.isContainsStuckTickets();
    }

    public BigDecimal getActualPrice() {
        return getFinalPrice();
    }

    @Override
    public Optional<BigDecimal> getOptionalVatPercentage() {
        return Optional.ofNullable(event.getVat());
    }

    @Override
    public VatStatus getVatStatus() {
        return event.getVatStatus();
    }

    public List<Configuration> getConfiguration() {
        return ticketCategoryStatisticView.getConfiguration();
    }
}
