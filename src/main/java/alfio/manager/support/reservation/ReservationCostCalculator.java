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
package alfio.manager.support.reservation;

import alfio.manager.PurchaseContextManager;
import alfio.model.*;
import alfio.model.decorator.TicketPriceContainer;
import alfio.model.subscription.Subscription;
import alfio.repository.*;
import alfio.util.MonetaryUtil;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static alfio.util.MonetaryUtil.unitToCents;
import static java.util.stream.Collectors.toList;

@Component
public class ReservationCostCalculator {

    private final TicketReservationRepository ticketReservationRepository;
    private final PurchaseContextManager purchaseContextManager;
    private final PromoCodeDiscountRepository promoCodeDiscountRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TicketRepository ticketRepository;
    private final AdditionalServiceRepository additionalServiceRepository;
    private final AdditionalServiceItemRepository additionalServiceItemRepository;

    public ReservationCostCalculator(TicketReservationRepository ticketReservationRepository,
                                     PurchaseContextManager purchaseContextManager,
                                     PromoCodeDiscountRepository promoCodeDiscountRepository,
                                     SubscriptionRepository subscriptionRepository,
                                     TicketRepository ticketRepository,
                                     AdditionalServiceRepository additionalServiceRepository,
                                     AdditionalServiceItemRepository additionalServiceItemRepository) {
        this.ticketReservationRepository = ticketReservationRepository;
        this.purchaseContextManager = purchaseContextManager;
        this.promoCodeDiscountRepository = promoCodeDiscountRepository;

        this.subscriptionRepository = subscriptionRepository;
        this.ticketRepository = ticketRepository;
        this.additionalServiceRepository = additionalServiceRepository;
        this.additionalServiceItemRepository = additionalServiceItemRepository;
    }

    /**
     * Get the total cost with VAT if it's not included in the ticket price.
     *
     * @param reservationId
     * @return
     */
    public Pair<TotalPrice, Optional<PromoCodeDiscount>> totalReservationCostWithVAT(String reservationId) {
        return totalReservationCostWithVAT(ticketReservationRepository.findReservationById(reservationId));
    }

    public Pair<TotalPrice, Optional<PromoCodeDiscount>> totalReservationCostWithVAT(TicketReservation reservation) {
        return totalReservationCostWithVAT(purchaseContextManager.findByReservationId(reservation.getId()).orElseThrow(), reservation, ticketRepository.findTicketsInReservation(reservation.getId()));
    }

    private Pair<TotalPrice, Optional<PromoCodeDiscount>> totalReservationCostWithVAT(PurchaseContext purchaseContext, TicketReservation reservation, List<Ticket> tickets) {
        var promoCodeDiscount = Optional.ofNullable(reservation.getPromoCodeDiscountId()).map(promoCodeDiscountRepository::findById);
        var subscriptions = subscriptionRepository.findSubscriptionsByReservationId(reservation.getId());
        var appliedSubscription = subscriptionRepository.findAppliedSubscriptionByReservationId(reservation.getId());
        return totalReservationCostWithVAT(promoCodeDiscount.orElse(null), purchaseContext, reservation, tickets,
            purchaseContext.event().map(event -> collectAdditionalServiceItems(reservation.getId(), event)).orElse(List.of()),
            subscriptions,
            appliedSubscription);
    }

    public static Pair<TotalPrice, Optional<PromoCodeDiscount>> totalReservationCostWithVAT(PromoCodeDiscount promoCodeDiscount,
                                                                                             PurchaseContext purchaseContext,
                                                                                             TicketReservation reservation,
                                                                                             List<Ticket> tickets,
                                                                                             List<Pair<AdditionalService, List<AdditionalServiceItem>>> additionalServiceItems,
                                                                                             List<Subscription> subscriptions,
                                                                                             Optional<Subscription> appliedSubscription) {

        String currencyCode = purchaseContext.getCurrency();
        List<TicketPriceContainer> ticketPrices = tickets.stream().map(t -> TicketPriceContainer.from(t, reservation.getVatStatus(), purchaseContext.getVat(), purchaseContext.getVatStatus(), promoCodeDiscount)).collect(toList());
        int discountedTickets = (int) ticketPrices.stream().filter(t -> t.getAppliedDiscount().compareTo(BigDecimal.ZERO) > 0).count();
        int discountAppliedCount = discountedTickets <= 1 || promoCodeDiscount.getDiscountType() == PromoCodeDiscount.DiscountType.FIXED_AMOUNT ? discountedTickets : 1;
        if(discountAppliedCount == 0 && promoCodeDiscount != null && promoCodeDiscount.getDiscountType() == PromoCodeDiscount.DiscountType.FIXED_AMOUNT_RESERVATION) {
            discountAppliedCount = 1;
        }
        var reservationPriceCalculator = alfio.manager.system.ReservationPriceCalculator.from(reservation, promoCodeDiscount, tickets, purchaseContext, additionalServiceItems, subscriptions, appliedSubscription);
        var price = new TotalPrice(unitToCents(reservationPriceCalculator.getFinalPrice(), currencyCode),
            unitToCents(reservationPriceCalculator.getVAT(), currencyCode),
            -MonetaryUtil.unitToCents(reservationPriceCalculator.getAppliedDiscount(), currencyCode),
            discountAppliedCount,
            currencyCode);
        return Pair.of(price, Optional.ofNullable(promoCodeDiscount));
    }

    Stream<Pair<AdditionalService, List<AdditionalServiceItem>>> streamAdditionalServiceItems(String reservationId, PurchaseContext purchaseContext) {
        return purchaseContext.event().map(event -> {
            return additionalServiceItemRepository.findByReservationUuid(event.getId(), reservationId)
                .stream()
                .collect(Collectors.groupingBy(AdditionalServiceItem::getAdditionalServiceId))
                .entrySet()
                .stream()
                .map(entry -> Pair.of(additionalServiceRepository.getById(entry.getKey(), event.getId()), entry.getValue()));
        }).orElse(Stream.empty());
    }
    public List<Pair<AdditionalService, List<AdditionalServiceItem>>> collectAdditionalServiceItems(String reservationId, Event event) {
        return streamAdditionalServiceItems(reservationId, event).collect(Collectors.toList());
    }
}
