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
import alfio.model.decorator.SubscriptionPriceContainer;
import alfio.model.decorator.TicketPriceContainer;
import alfio.model.subscription.Subscription;
import alfio.util.MonetaryUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
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
    final PurchaseContext purchaseContext;
    private final List<Subscription> subscriptions;
    private final Optional<Subscription> appliedSubscription;

    @Override
    public int getSrcPriceCts() {
        return tickets.stream().mapToInt(this::getTicketSrcPriceCts).sum() +
            additionalServiceItems.stream().mapToInt(AdditionalServiceItem::getSrcPriceCts).sum() +
            subscriptions.stream().mapToInt(Subscription::getSrcPriceCts).sum();
    }

    @Override
    public BigDecimal getAppliedDiscount() {

        int subscriptionDiscount = appliedSubscription
            .map(subscription -> tickets.stream().filter(t -> Objects.equals(t.getSubscriptionId(), subscription.getId())).mapToInt(Ticket::getSrcPriceCts).sum())
            .orElse(0);

        if(discount != null) {
            if (discount.getDiscountType() == PromoCodeDiscount.DiscountType.FIXED_AMOUNT_RESERVATION) {
                return MonetaryUtil.centsToUnit(discount.getDiscountAmount() + subscriptionDiscount, reservation.getCurrencyCode());
            }
            int ticketDiscount = tickets.stream()
                .mapToInt(t -> MonetaryUtil.unitToCents(TicketPriceContainer.from(t, reservation.getVatStatus(), reservation.getVatPercentageOrZero(), purchaseContext.getVatStatus(), discount).getAppliedDiscount(), reservation.getCurrencyCode()))
                .sum();
            return MonetaryUtil.centsToUnit(ticketDiscount +
                    additionalServiceItems.stream().mapToInt(AdditionalServiceItem::getDiscountCts).sum() +
                    subscriptions.stream().mapToInt(Subscription::getDiscountCts).sum() + subscriptionDiscount, reservation.getCurrencyCode());
        }
        return MonetaryUtil.centsToUnit(subscriptionDiscount, reservation.getCurrencyCode());
    }

    @Override
    public String getCurrencyCode() {
        return purchaseContext.getCurrency();
    }

    @Override
    public Optional<BigDecimal> getOptionalVatPercentage() {
        return Optional.ofNullable(firstNonNull(reservation.getUsedVatPercent(), purchaseContext.getVat()));
    }

    @Override
    public VatStatus getVatStatus() {
        return firstNonNull(reservation.getVatStatus(), purchaseContext.getVatStatus());
    }

    @Override
    public Optional<PromoCodeDiscount> getDiscount() {
        return Optional.ofNullable(discount);
    }

    @Override
    public BigDecimal getTaxablePrice() {
        var ticketsTaxablePrice = tickets.stream()
            .map(t -> TicketPriceContainer.from(t, reservation.getVatStatus(), getVatPercentageOrZero(), purchaseContext.getVatStatus(), discount).getTaxablePrice())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        var additionalServiceTaxablePrice = additionalServiceItems.stream()
            .map(asi -> AdditionalServiceItemPriceContainer.from(asi, additionalServices.stream().filter(as -> as.getId() == asi.getAdditionalServiceId()).findFirst().orElseThrow(), purchaseContext, discount).getTaxablePrice())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        var subscriptionsPrice = subscriptions.stream().map(s -> SubscriptionPriceContainer.from(s, purchaseContext, discount).getTaxablePrice())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        var totalTicketsAndAdditional = ticketsTaxablePrice.add(additionalServiceTaxablePrice).add(subscriptionsPrice);
        if(discount != null && discount.getDiscountType() != PromoCodeDiscount.DiscountType.FIXED_AMOUNT_RESERVATION) {
            // no need to add the discounted price here, since the single items are already taking it into account
            return totalTicketsAndAdditional;
        }
        return totalTicketsAndAdditional.subtract(getAppliedDiscount());
    }

    public static ReservationPriceCalculator from(TicketReservation reservation, PromoCodeDiscount discount, List<Ticket> tickets, PurchaseContext purchaseContext, List<Pair<AdditionalService, List<AdditionalServiceItem>>> additionalServiceItemsByAdditionalService,
                                                  List<Subscription> subscriptions, Optional<Subscription> appliedSubscription) {
        var additionalServiceItems = additionalServiceItemsByAdditionalService.stream().flatMap(p -> p.getRight().stream()).collect(Collectors.toList());
        var additionalServices = additionalServiceItemsByAdditionalService.stream().map(Pair::getKey).collect(Collectors.toList());
        return new ReservationPriceCalculator(reservation, discount, tickets, additionalServiceItems, additionalServices, purchaseContext, subscriptions, appliedSubscription);
    }

    private int getTicketSrcPriceCts(Ticket t) {
        if(VatStatus.isVatExempt(t.getVatStatus())) {
            return t.getSrcPriceCts() - Math.abs(t.getVatCts()); // VAT can be negative in some cases
        }
        return t.getSrcPriceCts();
    }

}
