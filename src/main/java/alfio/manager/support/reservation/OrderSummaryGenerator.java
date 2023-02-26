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

import alfio.manager.PaymentManager;
import alfio.manager.i18n.MessageSourceManager;
import alfio.model.*;
import alfio.model.decorator.AdditionalServiceItemPriceContainer;
import alfio.model.decorator.TicketPriceContainer;
import alfio.model.subscription.Subscription;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.model.subscription.SubscriptionPriceContainer;
import alfio.model.transaction.PaymentProxy;
import alfio.repository.*;
import alfio.util.LocaleUtil;
import alfio.util.MonetaryUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static alfio.manager.support.reservation.ReservationCostCalculator.totalReservationCostWithVAT;
import static alfio.model.TicketReservation.TicketReservationStatus.DEFERRED_OFFLINE_PAYMENT;
import static alfio.util.MonetaryUtil.formatCents;
import static alfio.util.MonetaryUtil.formatUnit;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

@Component
public class OrderSummaryGenerator {

    private static final Logger log = LoggerFactory.getLogger(OrderSummaryGenerator.class);
    private final TicketReservationRepository ticketReservationRepository;
    private final AuditingRepository auditingRepository;
    private final PaymentManager paymentManager;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final AdditionalServiceTextRepository additionalServiceTextRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TicketRepository ticketRepository;
    private final MessageSourceManager messageSourceManager;
    private final ReservationCostCalculator reservationCostCalculator;

    public OrderSummaryGenerator(TicketReservationRepository ticketReservationRepository,
                                 AuditingRepository auditingRepository,
                                 PaymentManager paymentManager,
                                 TicketCategoryRepository ticketCategoryRepository,
                                 AdditionalServiceTextRepository additionalServiceTextRepository,
                                 SubscriptionRepository subscriptionRepository,
                                 TicketRepository ticketRepository,
                                 MessageSourceManager messageSourceManager,
                                 ReservationCostCalculator reservationCostCalculator) {
        this.ticketReservationRepository = ticketReservationRepository;
        this.auditingRepository = auditingRepository;
        this.paymentManager = paymentManager;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.additionalServiceTextRepository = additionalServiceTextRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.ticketRepository = ticketRepository;
        this.messageSourceManager = messageSourceManager;
        this.reservationCostCalculator = reservationCostCalculator;
    }

    public OrderSummary orderSummaryForReservationId(String reservationId, PurchaseContext purchaseContext) {
        TicketReservation reservation = ticketReservationRepository.findReservationById(reservationId);
        return orderSummaryForReservation(reservation, purchaseContext);
    }
    public OrderSummary orderSummaryForReservation(TicketReservation reservation, PurchaseContext context) {
        var totalPriceAndDiscount = reservationCostCalculator.totalReservationCostWithVAT(reservation);
        TotalPrice reservationCost = totalPriceAndDiscount.getLeft();
        PromoCodeDiscount discount = totalPriceAndDiscount.getRight().orElse(null);
        //
        boolean free = reservationCost.getPriceWithVAT() == 0;
        String refundedAmount = null;

        boolean hasRefund = auditingRepository.countAuditsOfTypeForReservation(reservation.getId(), Audit.EventType.REFUND) > 0;

        if(hasRefund) {
            refundedAmount = paymentManager.getInfo(reservation, context).getPaymentInformation().getRefundedAmount();
        }

        var currencyCode = reservation.getCurrencyCode();
        return new OrderSummary(reservationCost,
            extractSummary(reservation.getId(), reservation.getVatStatus(), context, LocaleUtil.forLanguageTag(reservation.getUserLanguage()), discount, reservationCost),
            free,
            formatCents(reservationCost.getPriceWithVAT(), currencyCode),
            formatCents(reservationCost.getVAT(), currencyCode),
            reservation.getStatus() == TicketReservation.TicketReservationStatus.OFFLINE_PAYMENT,
            reservation.getStatus() == DEFERRED_OFFLINE_PAYMENT,
            reservation.getPaymentMethod() == PaymentProxy.ON_SITE,
            Optional.ofNullable(context.getVat()).map(p -> MonetaryUtil.formatCents(MonetaryUtil.unitToCents(p, currencyCode), currencyCode)).orElse(null),
            reservation.getVatStatus(),
            refundedAmount);
    }

    public OrderSummary orderSummaryForCreditNote(TicketReservation reservation, PurchaseContext purchaseContext, List<Ticket> removedTickets) {
        var totalPriceAndDiscount = totalReservationCostWithVAT(null, purchaseContext, reservation, removedTickets, List.of(), List.of(), Optional.empty());
        TotalPrice reservationCost = totalPriceAndDiscount.getLeft();
        //
        boolean free = reservationCost.getPriceWithVAT() == 0;

        var currencyCode = reservation.getCurrencyCode();
        return new OrderSummary(reservationCost,
            extractSummary(reservation.getVatStatus(), purchaseContext, LocaleUtil.forLanguageTag(reservation.getUserLanguage()), null, reservationCost, removedTickets, Stream.empty(), subscriptionRepository.findSubscriptionsByReservationId(reservation.getId())),
            free,
            formatCents(reservationCost.getPriceWithVAT(), currencyCode),
            formatCents(reservationCost.getVAT(), currencyCode),
            reservation.getStatus() == TicketReservation.TicketReservationStatus.OFFLINE_PAYMENT,
            reservation.getStatus() == DEFERRED_OFFLINE_PAYMENT,
            reservation.getPaymentMethod() == PaymentProxy.ON_SITE,
            Optional.ofNullable(purchaseContext.getVat()).map(p -> MonetaryUtil.formatCents(MonetaryUtil.unitToCents(p, currencyCode), currencyCode)).orElse(null),
            reservation.getVatStatus(),
            null);
    }

    List<SummaryRow> extractSummary(PriceContainer.VatStatus reservationVatStatus,
                                    PurchaseContext purchaseContext,
                                    Locale locale,
                                    PromoCodeDiscount promoCodeDiscount,
                                    TotalPrice reservationCost,
                                    List<Ticket> ticketsToInclude,
                                    Stream<Pair<AdditionalService, List<AdditionalServiceItem>>> additionalServicesToInclude,
                                    List<Subscription> subscriptionsToInclude) {
        log.trace("extract summary subscriptionsToInclude {}", subscriptionsToInclude);
        List<SummaryRow> summary = new ArrayList<>();
        var currencyCode = reservationCost.getCurrencyCode();
        List<TicketPriceContainer> tickets = ticketsToInclude.stream()
            .map(t -> TicketPriceContainer.from(t, reservationVatStatus, purchaseContext.getVat(), purchaseContext.getVatStatus(), promoCodeDiscount)).collect(toList());
        purchaseContext.event().ifPresent(event -> {
            boolean multipleTaxRates = tickets.stream().map(TicketPriceContainer::getVatStatus).collect(Collectors.toSet()).size() > 1;
            var ticketsByCategory = tickets.stream()
                .collect(Collectors.groupingBy(TicketPriceContainer::getCategoryId));
            List<Map.Entry<Integer, List<TicketPriceContainer>>> sorted;
            if (multipleTaxRates) {
                sorted = ticketsByCategory
                    .entrySet()
                    .stream()
                    .sorted(Comparator.comparing((Map.Entry<Integer, List<TicketPriceContainer>> e) -> e.getValue().get(0).getVatStatus()).reversed())
                    .collect(Collectors.toList());
            } else {
                sorted = new ArrayList<>(ticketsByCategory.entrySet());
            }
            Map<Integer, TicketCategory> categoriesById;

            if(ticketsByCategory.isEmpty()) {
                categoriesById = Map.of();
            } else {
                categoriesById = ticketCategoryRepository.getByIdsAndActive(ticketsByCategory.keySet(), event.getId())
                    .stream()
                    .collect(Collectors.toMap(TicketCategory::getId, Function.identity()));
            }

            for (var categoryWithTickets : sorted) {
                var categoryTickets = categoryWithTickets.getValue();
                final int subTotal = categoryTickets.stream().mapToInt(TicketPriceContainer::getSummarySrcPriceCts).sum();
                final int subTotalBeforeVat = SummaryPriceContainer.getSummaryPriceBeforeVatCts(categoryTickets);
                var firstTicket = categoryTickets.get(0);
                final int ticketPriceCts = firstTicket.getSummarySrcPriceCts();
                final int priceBeforeVat = SummaryPriceContainer.getSummaryPriceBeforeVatCts(singletonList(firstTicket));
                String categoryName = categoriesById.get(categoryWithTickets.getKey()).getName();
                var ticketVatStatus = firstTicket.getVatStatus();
                summary.add(new SummaryRow(categoryName, formatCents(ticketPriceCts, currencyCode), formatCents(priceBeforeVat, currencyCode), categoryTickets.size(), formatCents(subTotal, currencyCode), formatCents(subTotalBeforeVat, currencyCode), subTotal, SummaryRow.SummaryType.TICKET, null, ticketVatStatus));
                if (PriceContainer.VatStatus.isVatExempt(ticketVatStatus) && ticketVatStatus != reservationVatStatus) {
                    summary.add(new SummaryRow(null,
                        "",
                        "",
                        0,
                        formatCents(0, currencyCode, true),
                        formatCents(0, currencyCode, true),
                        0,
                        SummaryRow.SummaryType.TAX_DETAIL,
                        "0", ticketVatStatus));
                }
            }
        });

        summary.addAll(additionalServicesToInclude
            .map(entry -> {
                String language = locale.getLanguage();
                AdditionalServiceText title = additionalServiceTextRepository.findBestMatchByLocaleAndType(entry.getKey().getId(), language, AdditionalServiceText.TextType.TITLE);
                if(!title.getLocale().equals(language) || title.getId() == -1) {
                    log.debug("additional service {}: title not found for locale {}", title.getAdditionalServiceId(), language);
                }
                List<AdditionalServiceItemPriceContainer> prices = generateASIPriceContainers(purchaseContext, null).apply(entry).collect(toList());
                AdditionalServiceItemPriceContainer first = prices.get(0);
                final int subtotal = prices.stream().mapToInt(AdditionalServiceItemPriceContainer::getSrcPriceCts).sum();
                final int subtotalBeforeVat = SummaryPriceContainer.getSummaryPriceBeforeVatCts(prices);
                return new SummaryRow(title.getValue(), formatCents(first.getSrcPriceCts(), currencyCode), formatCents(SummaryPriceContainer.getSummaryPriceBeforeVatCts(singletonList(first)), currencyCode), prices.size(), formatCents(subtotal, currencyCode), formatCents(subtotalBeforeVat, currencyCode), subtotal, SummaryRow.SummaryType.ADDITIONAL_SERVICE, null, first.getVatStatus());
            }).collect(toList()));

        Optional.ofNullable(promoCodeDiscount).ifPresent(promo -> {
            String formattedSingleAmount = "-" + (PromoCodeDiscount.DiscountType.isFixedAmount(promo.getDiscountType())  ? formatCents(promo.getDiscountAmount(), currencyCode) : (promo.getDiscountAmount()+"%"));
            summary.add(new SummaryRow(formatPromoCode(promo, ticketsToInclude, locale, purchaseContext),
                formattedSingleAmount,
                formattedSingleAmount,
                reservationCost.getDiscountAppliedCount(),
                formatCents(reservationCost.getDiscount(), currencyCode), formatCents(reservationCost.getDiscount(), currencyCode), reservationCost.getDiscount(),
                promo.isDynamic() ? SummaryRow.SummaryType.DYNAMIC_DISCOUNT : SummaryRow.SummaryType.PROMOTION_CODE,
                null, reservationVatStatus));
        });
        //
        if(purchaseContext instanceof SubscriptionDescriptor) {
            if(!subscriptionsToInclude.isEmpty()) {
                var subscription = subscriptionsToInclude.get(0);
                var priceContainer = new SubscriptionPriceContainer(subscription, promoCodeDiscount, (SubscriptionDescriptor) purchaseContext);
                var priceBeforeVat = formatUnit(priceContainer.getNetPrice(), currencyCode);
                summary.add(new SummaryRow(purchaseContext.getTitle().get(locale.getLanguage()),
                    formatCents(priceContainer.getSummarySrcPriceCts(), currencyCode),
                    priceBeforeVat,
                    subscriptionsToInclude.size(),
                    formatCents(priceContainer.getSummarySrcPriceCts() * subscriptionsToInclude.size(), currencyCode),
                    formatUnit(priceContainer.getNetPrice().multiply(new BigDecimal(subscriptionsToInclude.size())), currencyCode),
                    priceContainer.getSummarySrcPriceCts(),
                    SummaryRow.SummaryType.SUBSCRIPTION,
                    null,
                    reservationVatStatus));
            }
        } else if(CollectionUtils.isNotEmpty(subscriptionsToInclude)) {
            log.trace("subscriptions to include is not empty");
            var subscription = subscriptionsToInclude.get(0);
            subscriptionRepository.findOne(subscription.getSubscriptionDescriptorId(), subscription.getOrganizationId()).ifPresent(subscriptionDescriptor -> {
                log.trace("found subscriptionDescriptor with ID {}", subscriptionDescriptor.getId());
                // find tickets with subscription applied
                var ticketsSubscription = tickets.stream().filter(t -> Objects.equals(subscription.getId(), t.getSubscriptionId())).collect(toList());
                final int ticketPriceCts = ticketsSubscription.stream().mapToInt(TicketPriceContainer::getSummarySrcPriceCts).sum();
                final int priceBeforeVat = SummaryPriceContainer.getSummaryPriceBeforeVatCts(ticketsSubscription);
                summary.add(new SummaryRow(subscriptionDescriptor.getLocalizedTitle(locale),
                    "-" + formatCents(ticketPriceCts, currencyCode),
                    "-" + formatCents(priceBeforeVat, currencyCode),
                    ticketsSubscription.size(),
                    "-" + formatCents(ticketPriceCts, currencyCode),
                    "-" + formatCents(priceBeforeVat, currencyCode),
                    ticketPriceCts,
                    SummaryRow.SummaryType.APPLIED_SUBSCRIPTION,
                    null,
                    reservationVatStatus));
            });
        }

        //
        return summary;
    }

    public List<SummaryRow> extractSummary(String reservationId, PriceContainer.VatStatus reservationVatStatus,
                                    PurchaseContext purchaseContext, Locale locale, PromoCodeDiscount promoCodeDiscount, TotalPrice reservationCost) {
        List<Subscription> subscriptionsToInclude;
        if(purchaseContext.ofType(PurchaseContext.PurchaseContextType.event)) {
            subscriptionsToInclude = subscriptionRepository.findAppliedSubscriptionByReservationId(reservationId)
                .map(List::of)
                .orElse(List.of());
        } else {
            subscriptionsToInclude = subscriptionRepository.findSubscriptionsByReservationId(reservationId);
        }

        return extractSummary(reservationVatStatus,
            purchaseContext,
            locale,
            promoCodeDiscount,
            reservationCost,
            ticketRepository.findTicketsInReservation(reservationId),
            reservationCostCalculator.streamAdditionalServiceItems(reservationId, purchaseContext),
            subscriptionsToInclude);
    }

    private String formatPromoCode(PromoCodeDiscount promoCodeDiscount, List<Ticket> tickets, Locale locale, PurchaseContext purchaseContext) {

        if(promoCodeDiscount.getCodeType() == PromoCodeDiscount.CodeType.DYNAMIC) {
            return messageSourceManager.getMessageSourceFor(purchaseContext).getMessage("reservation.dynamic.discount.description", null, locale); //we don't expose the internal promo code
        }

        List<Ticket> filteredTickets = tickets.stream().filter(ticket -> promoCodeDiscount.getCategories().contains(ticket.getCategoryId())).collect(toList());

        if (promoCodeDiscount.getCategories().isEmpty() || filteredTickets.isEmpty()) {
            return promoCodeDiscount.getPromoCode();
        }

        String formattedDiscountedCategories = filteredTickets.stream()
            .map(Ticket::getCategoryId)
            .collect(toSet())
            .stream()
            .map(categoryId -> ticketCategoryRepository.getByIdAndActive(categoryId, promoCodeDiscount.getEventId()).getName())
            .collect(Collectors.joining(", ", "(", ")"));


        return promoCodeDiscount.getPromoCode() + " " + formattedDiscountedCategories;
    }

    private static Function<Pair<AdditionalService, List<AdditionalServiceItem>>, Stream<? extends AdditionalServiceItemPriceContainer>> generateASIPriceContainers(PurchaseContext purchaseContext, PromoCodeDiscount discount) {
        return p -> p.getValue().stream().map(asi -> AdditionalServiceItemPriceContainer.from(asi, p.getKey(), purchaseContext, discount));
    }
}
