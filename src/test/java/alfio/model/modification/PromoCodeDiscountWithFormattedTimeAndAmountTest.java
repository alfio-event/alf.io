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
package alfio.model.modification;

import alfio.model.PromoCodeDiscount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static alfio.test.util.TestUtil.FIXED_TIME_CLOCK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromoCodeDiscountWithFormattedTimeAndAmountTest {

    private PromoCodeDiscount promoCodeDiscount;

    @BeforeEach
    void setUp() {
        promoCodeDiscount = mock(PromoCodeDiscount.class);
    }

    @Test
    void getFormattedDiscountAmountUseEventCurrencyCode() {
        when(promoCodeDiscount.hasCurrencyCode()).thenReturn(true);
        when(promoCodeDiscount.getCurrencyCode()).thenReturn("JPY");
        when(promoCodeDiscount.getDiscountType()).thenReturn(PromoCodeDiscount.DiscountType.FIXED_AMOUNT);
        when(promoCodeDiscount.getDiscountAmount()).thenReturn(10000);
        when(promoCodeDiscount.getFixedAmount()).thenReturn(true);
        var pdc = new PromoCodeDiscountWithFormattedTimeAndAmount(promoCodeDiscount, FIXED_TIME_CLOCK.getClock().getZone(), "CHF");
        assertEquals("100.00", pdc.getFormattedDiscountAmount());
    }

    @Test
    void getFormattedDiscountAmountUseEmbeddedCurrencyCode() {
        when(promoCodeDiscount.hasCurrencyCode()).thenReturn(true);
        when(promoCodeDiscount.getCurrencyCode()).thenReturn("JPY");
        when(promoCodeDiscount.getDiscountType()).thenReturn(PromoCodeDiscount.DiscountType.FIXED_AMOUNT);
        when(promoCodeDiscount.getDiscountAmount()).thenReturn(10000);
        when(promoCodeDiscount.getFixedAmount()).thenReturn(true);
        var pdc = new PromoCodeDiscountWithFormattedTimeAndAmount(promoCodeDiscount, FIXED_TIME_CLOCK.getClock().getZone(), null);
        assertEquals("10000", pdc.getFormattedDiscountAmount());
    }
}
