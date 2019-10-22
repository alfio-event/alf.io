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
package alfio.model.decorator;

import alfio.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdditionalServiceItemPriceContainerTest {

    private AdditionalServiceItem additionalServiceItem;
    private AdditionalService additionalService;
    private Event event;
    private PromoCodeDiscount discount;


    @BeforeEach
    void setUp() {
        event = mock(Event.class);
        additionalService = mock(AdditionalService.class);
        additionalServiceItem = mock(AdditionalServiceItem.class);
        discount = mock(PromoCodeDiscount.class);
        when(event.getCurrency()).thenReturn("CHF");
        when(event.getVatStatus()).thenReturn(PriceContainer.VatStatus.INCLUDED);
        when(event.getVat()).thenReturn(BigDecimal.TEN);
    }

    @Test
    void fromWithDiscountCodeNull() {
        var priceContainer = AdditionalServiceItemPriceContainer.from(additionalServiceItem, additionalService, event, null);
        assertNotNull(priceContainer);
    }

    @Test
    void fromDonation() {
        when(additionalService.getType()).thenReturn(AdditionalService.AdditionalServiceType.DONATION);
        var priceContainer = AdditionalServiceItemPriceContainer.from(additionalServiceItem, additionalService, event, discount);
        assertNotNull(priceContainer);
        assertFalse(priceContainer.getDiscount().isPresent());
    }

    @Test
    void fromSupplementPercentageDiscount() {
        when(additionalService.getType()).thenReturn(AdditionalService.AdditionalServiceType.SUPPLEMENT);
        when(discount.getDiscountType()).thenReturn(PromoCodeDiscount.DiscountType.PERCENTAGE);
        when(discount.getCodeType()).thenReturn(PromoCodeDiscount.CodeType.DISCOUNT);
        var priceContainer = AdditionalServiceItemPriceContainer.from(additionalServiceItem, additionalService, event, discount);
        assertNotNull(priceContainer);
        assertTrue(priceContainer.getDiscount().isPresent());
        assertSame(discount, priceContainer.getDiscount().get());
    }

    @Test
    void fromSupplement() {
        when(additionalService.getType()).thenReturn(AdditionalService.AdditionalServiceType.SUPPLEMENT);
        when(discount.getDiscountType()).thenReturn(PromoCodeDiscount.DiscountType.FIXED_AMOUNT);
        when(discount.getCodeType()).thenReturn(PromoCodeDiscount.CodeType.DISCOUNT);
        var priceContainer = AdditionalServiceItemPriceContainer.from(additionalServiceItem, additionalService, event, discount);
        assertNotNull(priceContainer);
        assertFalse(priceContainer.getDiscount().isPresent());
    }
}