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
package alfio.controller.decorator;

import alfio.model.Event;
import alfio.model.TicketCategory;
import lombok.experimental.Delegate;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.IntStream;

import static alfio.util.MonetaryUtil.addVAT;
import static alfio.util.MonetaryUtil.formatCents;
import static java.lang.Math.max;
import static java.lang.Math.min;

public class SaleableTicketCategory {

    @Delegate
    private final TicketCategory ticketCategory;
    private final ZonedDateTime now;
    private final ZoneId zoneId;
    private final Event event;
    private final boolean soldOut;
    private final boolean inSale;
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
        this.inSale = isCurrentlyInSale(now, ticketCategory, event.getZoneId());
        this.soldOut = inSale && availableTickets == 0;
        this.availableTickets = availableTickets;
        this.maxTickets = maxTickets;
    }

    private static boolean isCurrentlyInSale(ZonedDateTime now, TicketCategory tc, ZoneId zoneId) {
        return tc.getInception(zoneId).isBefore(now) && tc.getExpiration(zoneId).isAfter(now);
    }

    public boolean getSaleable() {
        return inSale && !soldOut;
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
    
    public boolean getSouldOut() {
    	return soldOut;
    }

    public String getFormattedExpiration() {
        return getExpiration(zoneId).format(DateTimeFormatter.ISO_DATE_TIME);
    }
    
    public ZonedDateTime getZonedExpiration() {
    	return getExpiration(zoneId);
    }

    public String getFinalPrice() {
        if(event.isVatIncluded()) {
            return formatCents(addVAT(ticketCategory.getPriceInCents(), event.getVat()));
        }
        return formatCents(ticketCategory.getPriceInCents());
    }

    public int[] getAmountOfTickets() {
        return generateRangeOfTicketQuantity(maxTickets, availableTickets);
    }

    static int[] generateRangeOfTicketQuantity(int maxTickets, int availableTickets) {
        final int maximumSaleableTickets = max(0, min(maxTickets, availableTickets));
        return IntStream.rangeClosed(0, maximumSaleableTickets).toArray();
    }



}
