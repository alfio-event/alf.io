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
import alfio.model.PriceContainer;
import alfio.model.PromoCodeDiscount;
import alfio.model.TicketCategory;
import lombok.experimental.Delegate;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class SaleableTicketCategory implements PriceContainer {

    @Delegate
    private final TicketCategory ticketCategory;
    private final String description;
    private final ZonedDateTime now;
    private final ZoneId zoneId;
    private final Event event;
    private final boolean soldOut;
    private final boolean inSale;
    private final int availableTickets;
    private final int maxTickets;
    private final PromoCodeDiscount promoCodeDiscount;

    public SaleableTicketCategory(TicketCategory ticketCategory,
                                  String description,
                                  ZonedDateTime now,
                                  Event event,
                                  int availableTickets,
                                  int maxTickets,
                                  PromoCodeDiscount promoCodeDiscount) {
        this.ticketCategory = ticketCategory;
        this.description = description;
        this.now = now;
        this.zoneId = event.getZoneId();
        this.event = event;
        this.inSale = isCurrentlyInSale(now, ticketCategory, event.getZoneId());
        this.soldOut = inSale && availableTickets == 0;
        this.availableTickets = availableTickets;
        this.maxTickets = maxTickets;
        this.promoCodeDiscount = promoCodeDiscount;
    }

    private static boolean isCurrentlyInSale(ZonedDateTime now, TicketCategory tc, ZoneId zoneId) {
        return tc.getInception(zoneId).isBefore(now) && tc.getExpiration(zoneId).isAfter(now);
    }

    public boolean getSaleable() {
        return inSale && !soldOut;
    }

    public boolean getSaleableAndLimitNotReached() {
        return getSaleable() && (promoCodeDiscount == null || maxTickets > 0);
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

    public boolean getSouldOutOrLimitReached() {
        return soldOut || (promoCodeDiscount != null && maxTickets == 0);
    }

    public String getFormattedExpiration() {
        return getExpiration(zoneId).format(DateTimeFormatter.ISO_DATE_TIME);
    }
    
    public ZonedDateTime getZonedExpiration() {
        return getExpiration(zoneId);
    }

    public ZonedDateTime getZonedInception() {
        return getInception(zoneId);
    }

    @Override
    public Optional<PromoCodeDiscount> getDiscount() {
        return Optional.ofNullable(promoCodeDiscount);
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

    public String getDescription() {
        return description;
    }

    public String getFormattedFinalPrice() {
        return getFinalPriceToDisplay(getFinalPrice().add(getAppliedDiscount()), getVAT(), getVatStatus()).toString();
    }

    public int[] getAmountOfTickets() {
        return DecoratorUtil.generateRangeOfTicketQuantity(maxTickets, availableTickets);
    }

    public int getAvailableTickets() {
        return availableTickets;
    }

    public String getDiscountedPrice() {
        return getFinalPriceToDisplay(getFinalPrice(), getVAT(), getVatStatus()).toString();
    }

    public boolean getSupportsDiscount() {
        return promoCodeDiscount != null && getSaleable();
    }

    public PromoCodeDiscount getPromoCodeDiscount() {
        return promoCodeDiscount;
    }

    static BigDecimal getFinalPriceToDisplay(BigDecimal price, BigDecimal vat, VatStatus vatStatus) {
        if(vatStatus == VatStatus.NOT_INCLUDED) {
            return price.subtract(vat);
        }
        return price;
    }


}
