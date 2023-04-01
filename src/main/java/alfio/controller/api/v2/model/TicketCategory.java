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
package alfio.controller.api.v2.model;

import alfio.controller.decorator.SaleableTicketCategory;
import alfio.model.TicketCategory.TicketAccessType;
import lombok.Getter;

import java.util.Map;

import static java.lang.Math.max;
import static java.lang.Math.min;

@Getter
public class TicketCategory {

    private final Map<String, String> description;
    private final int id;
    private final String name;
    private final TicketAccessType ticketAccessType;
    private final boolean bounded;
    private final int maximumSaleableTickets;
    private final boolean free;
    private final String formattedFinalPrice;
    private final boolean hasDiscount;
    private final String formattedDiscountedPrice;
    private final int ordinal;

    //
    private final boolean expired;
    private final boolean saleInFuture;
    private final Map<String, String> formattedInception;
    private final Map<String, String> formattedExpiration;
    private final boolean saleableAndLimitNotReached;
    private final boolean accessRestricted;
    private final boolean soldOutOrLimitReached;
    private final Integer availableTickets;
    private final boolean displayTaxInformation;
    //

    public TicketCategory(SaleableTicketCategory saleableTicketCategory,
                          Map<String, String> description,
                          Map<String, String> formattedInception,
                          Map<String, String> formattedExpiration,
                          boolean displayTicketsLeft,
                          boolean displayTaxInformation) {

        this.description = description;
        this.id = saleableTicketCategory.getId();
        this.name = saleableTicketCategory.getName();
        this.ticketAccessType = saleableTicketCategory.getTicketAccessType();
        this.bounded = saleableTicketCategory.isBounded();
        this.maximumSaleableTickets = max(0, min(saleableTicketCategory.getMaxTicketsAfterConfiguration(), saleableTicketCategory.getAvailableTickets()));
        this.free = saleableTicketCategory.getFree();
        this.formattedFinalPrice = saleableTicketCategory.getFormattedFinalPrice();
        this.hasDiscount = saleableTicketCategory.getSupportsDiscount();
        this.formattedDiscountedPrice = hasDiscount ? saleableTicketCategory.getDiscountedPrice() : "";

        //
        this.expired = saleableTicketCategory.getExpired();
        this.saleInFuture = saleableTicketCategory.getSaleInFuture();
        this.formattedInception = formattedInception;
        this.formattedExpiration = formattedExpiration;
        //

        //
        this.saleableAndLimitNotReached = saleableTicketCategory.getSaleableAndLimitNotReached();
        this.accessRestricted = saleableTicketCategory.getAccessRestricted();
        this.soldOutOrLimitReached = saleableTicketCategory.getSouldOutOrLimitReached();
        //
        this.availableTickets = displayTicketsLeft && saleableTicketCategory.isBounded() ? saleableTicketCategory.getAvailableTickets() : null;
        this.ordinal = saleableTicketCategory.isAccessRestricted() ? -1 : saleableTicketCategory.getOrdinal();

        this.displayTaxInformation = displayTaxInformation;
    }
}
