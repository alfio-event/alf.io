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
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Delegate;

import java.math.BigDecimal;
import java.util.*;

@AllArgsConstructor
@Getter
public class EventWithAdditionalInfo implements StatisticsContainer, PriceContainer {

    @Delegate(excludes = {EventHiddenFieldContainer.class, PriceContainer.class})
    @JsonIgnore
    private final Event event;

    private final List<TicketCategoryWithAdditionalInfo> ticketCategories;

    @Delegate(excludes = {Event.class})
    @JsonIgnore
    private final EventStatistic eventStatistic;

    private final Map<String, String> description;

    private final BigDecimal grossIncome;
    private final AlfioMetadata metadata;
    private final List<UUID> linkedSubscriptions;

    private final Set<ExtensionCapabilitySummary> supportedCapabilities;

    @JsonIgnore
    public Event getEvent() {
        return event;
    }

    @JsonIgnore
    public EventStatistic getEventStatistic() {
       return eventStatistic;
    }

    @Override
    @JsonIgnore
    public int getSrcPriceCts() {
        return event.getSrcPriceCts();
    }

    @Override
    public String getCurrencyCode() {
        return getCurrency();
    }

    @Override
    @JsonIgnore
    public Optional<BigDecimal> getOptionalVatPercentage() {
        return getVatStatus() != VatStatus.NONE ? Optional.ofNullable(event.getVat()) : Optional.empty();
    }

    @Override
    public VatStatus getVatStatus() {
        return event.getVatStatus();
    }

    public BigDecimal getVatPercentage() {
        return getVatPercentageOrZero();
    }

    public BigDecimal getVat() {
        return getVAT();
    }

    public boolean isAddCategoryEnabled() {
        return ticketCategories.stream()
            .mapToInt(TicketCategoryWithAdditionalInfo::getMaxTickets)
            .sum() < getAvailableSeats();
    }

    public boolean isContainingUnboundedCategories() {
        return getTicketCategories().stream().anyMatch(t -> !t.isBounded());
    }

    public boolean isSupportsAdditionalItemsQuantity() {
        return event.supportsLinkedAdditionalServices();
    }
}
