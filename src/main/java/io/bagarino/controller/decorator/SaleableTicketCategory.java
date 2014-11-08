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
import java.util.stream.IntStream;

public class SaleableTicketCategory {

    @Delegate
    private final TicketCategory ticketCategory;
    private final ZonedDateTime now;
    private final ZoneId zoneId;
    private final Event event;
    private final boolean soldout;
    private final int availableTickets;
    private final int maxTickets;

    public SaleableTicketCategory(TicketCategory ticketCategory,
                                  ZonedDateTime now,
                                  Event event,
                                  int availableTickets,
                                  int maxTickets) {
        this.ticketCategory = ticketCategory;
        this.now = now;
        this.zoneId = event.getZoneId();
        this.event = event;
        this.soldout = availableTickets == 0;
        this.availableTickets = availableTickets;
        this.maxTickets = maxTickets;
    }

    public boolean getSaleable() {
        return getInception(zoneId).isBefore(now) && getExpiration(zoneId).isAfter(now) && !soldout;
    }

    public boolean getExpired() {
        return getExpiration(zoneId).isBefore(now);
    }

    public boolean getSaleInFuture() {
        return getInception(zoneId).isAfter(now);
    }
    
    //jmustache
    public boolean getAccessRestricted() {
    	return isAccessRestricted();
    }
    
    public boolean getSouldout() {
    	return soldout;
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

    public int[] getAmountOfTickets() {
        return IntStream.rangeClosed(0, maxTickets).limit(Math.min(maxTickets, availableTickets+1)).toArray();
    }



}
