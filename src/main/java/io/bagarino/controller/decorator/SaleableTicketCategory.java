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
package io.bagarino.controller.decorator;

import io.bagarino.model.Event;
import io.bagarino.model.TicketCategory;
import io.bagarino.util.MonetaryUtil;
import lombok.experimental.Delegate;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class SaleableTicketCategory {

    @Delegate
    private final TicketCategory ticketCategory;
    private final ZonedDateTime now;
    private final ZoneId zoneId;
    private final Event event;

    public SaleableTicketCategory(TicketCategory ticketCategory,
                                  ZonedDateTime now,
                                  Event event) {
        this.ticketCategory = ticketCategory;
        this.now = now;
        this.zoneId = event.getZoneId();
        this.event = event;
    }

    public boolean getSaleable() {
        return getInception(zoneId).isBefore(now) && getExpiration(zoneId).isAfter(now);
    }

    public boolean getExpired() {
        return getExpiration(zoneId).isBefore(now);
    }

    public boolean getSaleInFuture() {
        return getInception(zoneId).isAfter(now);
    }

    public String getFormattedExpiration() {
        return getExpiration(zoneId).format(DateTimeFormatter.ISO_DATE_TIME);
    }
    
    public ZonedDateTime getZonedExpiration() {
    	return getExpiration(zoneId);
    }

    public BigDecimal getFinalPrice() {
        if(event.isVatIncluded()) {
            return MonetaryUtil.addVAT(ticketCategory.getPrice(), event.getVat());
        }
        return ticketCategory.getPrice();
    }



}
