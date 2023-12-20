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
import alfio.repository.*;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReservationCostCalculatorTest {

    private static final String TICKET_RESERVATION_ID = "abcdef";
    private Event event;
    private Ticket ticket;
    private TicketCategory ticketCategory;
    private TicketReservation reservation;
    private EventRepository eventRepository;
    private TicketReservationRepository ticketReservationRepository;
    private TicketRepository ticketRepository;
    private TicketCategoryRepository ticketCategoryRepository;
    private AdditionalServiceItemRepository additionalServiceItemRepository;
    private AdditionalServiceRepository additionalServiceRepository;
    private AdditionalServiceTextRepository additionalServiceTextRepository;
    private ReservationCostCalculator calculator;

    @BeforeEach
    void setUp() {
        ticketRepository = mock(TicketRepository.class);
        ticketReservationRepository = mock(TicketReservationRepository.class);
        eventRepository = mock(EventRepository.class);
        reservation = mock(TicketReservation.class);
        event = mock(Event.class);
        when(event.getCurrency()).thenReturn("CHF");
        when(event.event()).thenReturn(Optional.of(event));
        ticket = mock(Ticket.class);
        when(ticket.getCurrencyCode()).thenReturn("CHF");
        when(ticket.getCategoryId()).thenReturn(1);
        ticketCategory = mock(TicketCategory.class);
        when(ticketCategory.getId()).thenReturn(1);
        ticketCategoryRepository = mock(TicketCategoryRepository.class);
        additionalServiceRepository = mock(AdditionalServiceRepository.class);
        additionalServiceItemRepository = mock(AdditionalServiceItemRepository.class);
        additionalServiceTextRepository = mock(AdditionalServiceTextRepository.class);
        var purchaseContextManager = mock(PurchaseContextManager.class);
        when(purchaseContextManager.findByReservationId(anyString())).thenReturn(Optional.of(event));
        calculator = new ReservationCostCalculator(
            ticketReservationRepository,
            purchaseContextManager,
            mock(PromoCodeDiscountRepository.class),
            mock(SubscriptionRepository.class),
            ticketRepository,
            additionalServiceRepository,
            additionalServiceItemRepository
        );
    }

    @Test
    void calcReservationCostOnlyTickets() {
        when(event.isVatIncluded()).thenReturn(true, false);
        when(event.getVat()).thenReturn(BigDecimal.TEN);
        when(eventRepository.findByReservationId(eq(TICKET_RESERVATION_ID))).thenReturn(event);
        when(ticketReservationRepository.findReservationById(eq(TICKET_RESERVATION_ID))).thenReturn(reservation);
        when(reservation.getId()).thenReturn(TICKET_RESERVATION_ID);
        when(ticket.getSrcPriceCts()).thenReturn(10);
        when(ticketRepository.findTicketsInReservation(eq(TICKET_RESERVATION_ID))).thenReturn(Collections.singletonList(ticket));
        AdditionalServiceItemRepository additionalServiceItemRepository = mock(AdditionalServiceItemRepository.class);
        when(additionalServiceItemRepository.findByReservationUuid(anyInt(), eq(TICKET_RESERVATION_ID))).thenReturn(Collections.emptyList());

        when(event.getVatStatus()).thenReturn(PriceContainer.VatStatus.INCLUDED);
        Pair<TotalPrice, Optional<PromoCodeDiscount>> priceAndDiscount = calculator.totalReservationCostWithVAT(TICKET_RESERVATION_ID);
        TotalPrice included = priceAndDiscount.getLeft();
        Assertions.assertTrue(priceAndDiscount.getRight().isEmpty());
        Assertions.assertEquals(10, included.getPriceWithVAT());
        Assertions.assertEquals(1, included.getVAT());

        when(event.getVatStatus()).thenReturn(PriceContainer.VatStatus.NOT_INCLUDED);
        Pair<TotalPrice, Optional<PromoCodeDiscount>> priceAndDiscountNotIncluded = calculator.totalReservationCostWithVAT(TICKET_RESERVATION_ID);
        TotalPrice notIncluded = priceAndDiscountNotIncluded.getLeft();
        Assertions.assertTrue(priceAndDiscountNotIncluded.getRight().isEmpty());
        Assertions.assertEquals(11, notIncluded.getPriceWithVAT());
        Assertions.assertEquals(1, notIncluded.getVAT());
    }

    @Test
    void calcReservationCostWithASVatIncludedInherited() {
        initReservationWithAdditionalServices(true, AdditionalService.VatType.INHERITED, 10, 10);
        //first: event price vat included, additional service VAT inherited
        Pair<TotalPrice, Optional<PromoCodeDiscount>> priceAndDiscount = calculator.totalReservationCostWithVAT(TICKET_RESERVATION_ID);
        TotalPrice first = priceAndDiscount.getLeft();
        Assertions.assertTrue(priceAndDiscount.getRight().isEmpty());
        Assertions.assertEquals(20, first.getPriceWithVAT());
        Assertions.assertEquals(2, first.getVAT());
    }

    @Test
    void calcReservationCostWithASVatIncludedASNoVat() {
        initReservationWithAdditionalServices(true, AdditionalService.VatType.NONE, 10, 10);
        //second: event price vat included, additional service VAT n/a
        Pair<TotalPrice, Optional<PromoCodeDiscount>> priceAndDiscount = calculator.totalReservationCostWithVAT(TICKET_RESERVATION_ID);
        TotalPrice second = priceAndDiscount.getLeft();
        Assertions.assertTrue(priceAndDiscount.getRight().isEmpty());
        Assertions.assertEquals(20, second.getPriceWithVAT());
        Assertions.assertEquals(1, second.getVAT());
    }

    @Test
    void calcReservationCostWithASVatNotIncludedASInherited() {
        initReservationWithAdditionalServices(false, AdditionalService.VatType.INHERITED, 10, 10);
        //third: event price vat not included, additional service VAT inherited
        Pair<TotalPrice, Optional<PromoCodeDiscount>> priceAndDiscount = calculator.totalReservationCostWithVAT(TICKET_RESERVATION_ID);
        TotalPrice third = priceAndDiscount.getLeft();
        Assertions.assertTrue(priceAndDiscount.getRight().isEmpty());
        Assertions.assertEquals(22, third.getPriceWithVAT());
        Assertions.assertEquals(2, third.getVAT());
    }

    @Test
    void calcReservationCostWithASVatNotIncludedASNone() {
        initReservationWithAdditionalServices(false, AdditionalService.VatType.NONE, 10, 10);
        //fourth: event price vat not included, additional service VAT n/a
        Pair<TotalPrice, Optional<PromoCodeDiscount>> priceAndDiscount = calculator.totalReservationCostWithVAT(TICKET_RESERVATION_ID);
        TotalPrice fourth = priceAndDiscount.getLeft();
        Assertions.assertTrue(priceAndDiscount.getRight().isEmpty());
        Assertions.assertEquals(21, fourth.getPriceWithVAT());
        Assertions.assertEquals(1, fourth.getVAT());
    }

    private void initReservationWithTicket(int ticketPaidPrice, boolean eventVatIncluded) {
        when(event.isVatIncluded()).thenReturn(eventVatIncluded);
        when(event.getVatStatus()).thenReturn(eventVatIncluded ? PriceContainer.VatStatus.INCLUDED : PriceContainer.VatStatus.NOT_INCLUDED);
        when(event.getVat()).thenReturn(BigDecimal.TEN);
        when(event.getId()).thenReturn(1);
        when(eventRepository.findByReservationId(eq(TICKET_RESERVATION_ID))).thenReturn(event);
        when(ticketReservationRepository.findReservationById(eq(TICKET_RESERVATION_ID))).thenReturn(reservation);
        when(ticket.getSrcPriceCts()).thenReturn(ticketPaidPrice);
        when(ticket.getCategoryId()).thenReturn(1);
        when(ticketRepository.findTicketsInReservation(eq(TICKET_RESERVATION_ID))).thenReturn(Collections.singletonList(ticket));
        when(ticketCategoryRepository.getByIdAndActive(eq(1), eq(1))).thenReturn(ticketCategory);
        when(ticketCategoryRepository.getByIdsAndActive(anyCollection(), eq(1))).thenReturn(List.of(ticketCategory));
        when(reservation.getId()).thenReturn(TICKET_RESERVATION_ID);
    }

    private void initReservationWithAdditionalServices(boolean eventVatIncluded, AdditionalService.VatType additionalServiceVatType, int ticketSrcPrice, int asSrcPrice) {

        initReservationWithTicket(ticketSrcPrice, eventVatIncluded);

        AdditionalServiceItem additionalServiceItem = mock(AdditionalServiceItem.class);
        when(additionalServiceItem.getCurrencyCode()).thenReturn("CHF");
        AdditionalService additionalService = mock(AdditionalService.class);
        when(additionalService.getCurrencyCode()).thenReturn("CHF");
        when(additionalService.getId()).thenReturn(1);

        when(additionalServiceItemRepository.findByReservationUuid(anyInt(), eq(TICKET_RESERVATION_ID))).thenReturn(Collections.singletonList(additionalServiceItem));
        when(additionalServiceItem.getAdditionalServiceId()).thenReturn(1);
        when(additionalServiceRepository.loadAllForEvent(eq(1))).thenReturn(List.of(additionalService));
        when(additionalServiceRepository.getById(eq(1), eq(1))).thenReturn(additionalService);
        when(additionalServiceItem.getSrcPriceCts()).thenReturn(asSrcPrice);
        when(additionalService.getVatType()).thenReturn(additionalServiceVatType);
        AdditionalServiceItemRepository additionalServiceItemRepository = mock(AdditionalServiceItemRepository.class);
        when(additionalServiceItemRepository.findByReservationUuid(anyInt(), eq(TICKET_RESERVATION_ID))).thenReturn(Collections.emptyList());
        AdditionalServiceText text = mock(AdditionalServiceText.class);
        when(text.getId()).thenReturn(1);
        when(text.getLocale()).thenReturn("en");
        when(additionalServiceTextRepository.findBestMatchByLocaleAndType(anyInt(), eq("en"), eq(AdditionalServiceText.TextType.TITLE))).thenReturn(text);
    }

}