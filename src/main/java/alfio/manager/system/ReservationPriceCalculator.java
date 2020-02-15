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
package alfio.manager.system;

import alfio.model.*;
import alfio.model.decorator.AdditionalServiceItemPriceContainer;
import alfio.model.decorator.TicketPriceContainer;
import alfio.util.MonetaryUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.ObjectUtils.firstNonNull;

@RequiredArgsConstructor
public class ReservationPriceCalculator implements PriceContainer {
    final TicketReservation reservation;
    final PromoCodeDiscount discount;
    final List<Ticket> tickets;
    final List<AdditionalServiceItem> additionalServiceItems;
    final List<AdditionalService> additionalServices;
    final Event event;

    @Override
    public int getSrcPriceCts() {
        return tickets.stream().mapToInt(Ticket::getSrcPriceCts).sum() + additionalServiceItems.stream().mapToInt(AdditionalServiceItem::getSrcPriceCts).sum();
    }

    @Override
    public BigDecimal getAppliedDiscount() {
        if(discount != null) {
            if (discount.getDiscountType() == PromoCodeDiscount.DiscountType.FIXED_AMOUNT_RESERVATION) {
                return MonetaryUtil.centsToUnit(discount.getDiscountAmount(), reservation.getCurrencyCode());
            }
            return MonetaryUtil.centsToUnit(tickets.stream().mapToInt(Ticket::getDiscountCts).sum() + additionalServiceItems.stream().mapToInt(AdditionalServiceItem::getDiscountCts).sum(), reservation.getCurrencyCode());
        }
        return BigDecimal.ZERO;
    }

    @Override
    public String getCurrencyCode() {
        return event.getCurrency();
    }

    @Override
    public Optional<BigDecimal> getOptionalVatPercentage() {
        return Optional.ofNullable(firstNonNull(reservation.getUsedVatPercent(), event.getVat()));
    }

    @Override
    public VatStatus getVatStatus() {
        return firstNonNull(reservation.getVatStatus(), event.getVatStatus());
    }

    @Override
    public Optional<PromoCodeDiscount> getDiscount() {
        return Optional.ofNullable(discount);
    }

    @Override
    public BigDecimal getTaxablePrice() {
        var ticketsTaxablePrice = tickets.stream()
            .map(t -> TicketPriceContainer.from(t, reservation.getVatStatus(), getVatPercentageOrZero(), event.getVatStatus(), discount).getTaxablePrice())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        var additionalServiceTaxablePrice = additionalServiceItems.stream()
            .map(asi -> AdditionalServiceItemPriceContainer.from(asi, additionalServices.stream().filter(as -> as.getId() == asi.getAdditionalServiceId()).findFirst().orElseThrow(), event, discount).getTaxablePrice())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        var totalTicketsAndAdditional = ticketsTaxablePrice.add(additionalServiceTaxablePrice);
        if(discount != null && discount.getDiscountType() != PromoCodeDiscount.DiscountType.FIXED_AMOUNT_RESERVATION) {
            // no need to add the discounted price here, since the single items are already taking it into account
            return totalTicketsAndAdditional;
        }
        return totalTicketsAndAdditional.subtract(getAppliedDiscount());
    }

    public static ReservationPriceCalculator from(TicketReservation reservation, PromoCodeDiscount discount, List<Ticket> tickets, Event event, List<Pair<AdditionalService, List<AdditionalServiceItem>>> additionalServiceItemsByAdditionalService) {
        var additionalServiceItems = additionalServiceItemsByAdditionalService.stream().flatMap(p -> p.getRight().stream()).collect(Collectors.toList());
        var additionalServices = additionalServiceItemsByAdditionalService.stream().map(Pair::getKey).collect(Collectors.toList());
        return new ReservationPriceCalculator(reservation, discount, tickets, additionalServiceItems, additionalServices, event);
    }

}
