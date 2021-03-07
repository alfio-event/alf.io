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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReservationPriceCalculatorTest {

    private TicketReservation reservation;

    private Ticket ticket;
    private List<Ticket> tickets;
    private AdditionalServiceItem additionalServiceItem;
    private List<AdditionalServiceItem> additionalServiceItems;
    private AdditionalService additionalService;
    private List<AdditionalService> additionalServices;
    private Event event;

    @BeforeEach
    void setUp() {
        reservation = mock(TicketReservation.class);
        when(reservation.getCurrencyCode()).thenReturn("CHF");
        ticket = mock(Ticket.class);
        tickets = List.of(ticket);
        additionalServiceItem = mock(AdditionalServiceItem.class);
        additionalServiceItems = List.of(additionalServiceItem);
        event = mock(Event.class);
        when(event.getCurrency()).thenReturn("CHF");
        additionalService = mock(AdditionalService.class);
        additionalServices = List.of(additionalService);
    }

    @Nested
    public class PromoCodeDiscountTest {
        private PromoCodeDiscount discount = mock(PromoCodeDiscount.class);

        @BeforeEach
        void init() {
            when(ticket.getDiscountCts()).thenReturn(100);
            when(additionalServiceItem.getDiscountCts()).thenReturn(100);
        }

        @Test
        void returnZeroIfDiscountIsNull() {
            assertEquals(BigDecimal.ZERO, new ReservationPriceCalculator(reservation, null, tickets, additionalServiceItems, additionalServices, event, List.of(), Optional.empty()).getAppliedDiscount());
        }

        @Test
        void discountOnReservation() {
            when(discount.getDiscountType()).thenReturn(PromoCodeDiscount.DiscountType.FIXED_AMOUNT_RESERVATION);
            when(discount.getDiscountAmount()).thenReturn(1);
            assertEquals(new BigDecimal("0.01"), new ReservationPriceCalculator(reservation, discount, tickets, additionalServiceItems, additionalServices, event, List.of(), Optional.empty()).getAppliedDiscount());
        }

        @Test
        void discountOnItems() {
            when(discount.getDiscountType()).thenReturn(PromoCodeDiscount.DiscountType.PERCENTAGE);
            when(discount.getDiscountAmount()).thenReturn(10);
            assertEquals(new BigDecimal("2.00"), new ReservationPriceCalculator(reservation, discount, tickets, additionalServiceItems, additionalServices, event, List.of(), Optional.empty()).getAppliedDiscount());
        }

    }

    @Nested
    public class TaxCalculatorTest {
        @BeforeEach
        void init() {
            when(ticket.getSrcPriceCts()).thenReturn(1000);
            when(ticket.getCurrencyCode()).thenReturn("CHF");
            when(additionalServiceItem.getSrcPriceCts()).thenReturn(1100);
        }

        @Test
        void allItemsAreSubjectedToTaxation() {
            when(additionalService.getVatType()).thenReturn(AdditionalService.VatType.INHERITED);
            var taxablePrice = new ReservationPriceCalculator(reservation, null, tickets, additionalServiceItems, additionalServices, event, List.of(), Optional.empty()).getTaxablePrice();
            assertEquals(new BigDecimal("21.00"), taxablePrice);
        }

        @Test
        void additionalItemsAreNotSubjectedToTaxation() {
            when(additionalService.getVatType()).thenReturn(AdditionalService.VatType.NONE);
            var taxablePrice = new ReservationPriceCalculator(reservation, null, tickets, additionalServiceItems, additionalServices, event, List.of(), Optional.empty()).getTaxablePrice();
            assertEquals(new BigDecimal("10.00"), taxablePrice);
        }

    }

}