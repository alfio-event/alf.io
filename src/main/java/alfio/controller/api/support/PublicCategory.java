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
package alfio.controller.api.support;

import alfio.model.Event;
import alfio.model.TicketCategory;
import alfio.model.TicketCategoryDescription;
import alfio.util.EventUtil;
import alfio.util.MonetaryUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.builder.CompareToBuilder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class PublicCategory {
    public static Comparator<PublicCategory> SORT_BY_DATE = (c1, c2) -> new CompareToBuilder()
        .append(c1.getUtcInception(), c2.getUtcInception())
        .append(c1.getUtcExpiration(), c2.getUtcExpiration())
        .toComparison();

    private final TicketCategory category;
    private final List<TicketCategoryDescription> categoryDescriptions;
    private final Event event;
    private final int availableTickets;
    private final int maxTicketsPerReservation;

    public PublicCategory(TicketCategory category, Event event, int availableTickets, int maxTicketsPerReservation, DataLoader<TicketCategory, TicketCategoryDescription> categoryDescriptionsLoader) {
        this.category = category;
        this.event = event;
        this.categoryDescriptions = categoryDescriptionsLoader.load(category);
        this.availableTickets = availableTickets;
        this.maxTicketsPerReservation = maxTicketsPerReservation;
    }

    public boolean isActive() {
        ZonedDateTime now = ZonedDateTime.now(Clock.systemUTC());
        return category.getUtcInception().isBefore(now) && now.isBefore(category.getUtcExpiration());
    }

    public boolean isTicketsAvailable() {
        return isActive() && availableTickets > 0;
    }

    public boolean isSoldOut() {
        return !isTicketsAvailable();
    }

    public boolean isFutureSale() {
        return ZonedDateTime.now(Clock.systemUTC()).isBefore(category.getUtcInception());
    }

    public List<TicketCategoryDescription> getCategoryDescriptions() {
        return categoryDescriptions;
    }

    public boolean isFreeOfCharge() {
        return category.getFree();
    }

    public BigDecimal getPrice() {
        return category.getPrice();
    }

    public ZonedDateTime getInception() {
        return category.getInception(event.getZoneId());
    }

    public ZonedDateTime getExpiration() {
        return category.getExpiration(event.getZoneId());
    }

    public BigDecimal getFinalPrice() {
        return MonetaryUtil.centsToUnit(EventUtil.getFinalPriceInCents(event, category));
    }

    public int getMaxTickets() {
        return max(0, min(maxTicketsPerReservation, availableTickets));
    }

    @JsonIgnore
    public boolean isAccessRestricted() {
        return category.isAccessRestricted();
    }

    @JsonIgnore
    public ZonedDateTime getUtcInception() {
        return category.getUtcInception();
    }

    @JsonIgnore
    public ZonedDateTime getUtcExpiration() {
        return category.getUtcExpiration();
    }

}
