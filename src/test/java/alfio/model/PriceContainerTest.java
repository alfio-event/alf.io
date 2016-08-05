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
package alfio.model;

import alfio.test.util.PriceContainerImpl;
import alfio.util.MonetaryUtil;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PriceContainerTest {

    @Test
    public void getFinalPriceInputDoNotApplyVat() throws Exception {
        Stream.of(PriceContainer.VatStatus.INCLUDED, PriceContainer.VatStatus.NONE)
            .forEach(vatStatus -> {
                PriceContainerImpl vs = new PriceContainerImpl(1000, "CHF", new BigDecimal("30.00"), vatStatus);
                assertEquals(new BigDecimal("10.00"), vs.getFinalPrice());
                PromoCodeDiscount promoCodeDiscount = mock(PromoCodeDiscount.class);
                when(promoCodeDiscount.getDiscountAmount()).thenReturn(100, 10);
                when(promoCodeDiscount.getFixedAmount()).thenReturn(true, false);
                when(promoCodeDiscount.getDiscountType()).thenReturn(PromoCodeDiscount.DiscountType.FIXED_AMOUNT, PromoCodeDiscount.DiscountType.PERCENTAGE);

                vs = new PriceContainerImpl(1100, "CHF", new BigDecimal("30.00"), vatStatus, promoCodeDiscount);
                assertEquals(String.format("vatStatus: %s", vatStatus.name()), new BigDecimal("10.00"), vs.getFinalPrice());

                vs = new PriceContainerImpl(1000, "CHF", new BigDecimal("30.00"), vatStatus, promoCodeDiscount);
                assertEquals(String.format("vatStatus: %s", vatStatus.name()), new BigDecimal("9.00"), vs.getFinalPrice());
            });
    }

    @Test
    public void getFinalPriceInputVatNotIncludedSingle() throws Exception {
        PriceContainerImpl vs = new PriceContainerImpl(1000, "CHF", new BigDecimal("30.00"), PriceContainer.VatStatus.NOT_INCLUDED);
        assertEquals(new BigDecimal("13.00"), vs.getFinalPrice());

        PromoCodeDiscount promoCodeDiscount = mock(PromoCodeDiscount.class);
        when(promoCodeDiscount.getDiscountAmount()).thenReturn(100, 10);
        when(promoCodeDiscount.getFixedAmount()).thenReturn(true, false);
        when(promoCodeDiscount.getDiscountType()).thenReturn(PromoCodeDiscount.DiscountType.FIXED_AMOUNT, PromoCodeDiscount.DiscountType.PERCENTAGE);

        vs = new PriceContainerImpl(1100, "CHF", new BigDecimal("8.00"), PriceContainer.VatStatus.NOT_INCLUDED, promoCodeDiscount);
        assertEquals(new BigDecimal("10.80"), vs.getFinalPrice());

        vs = new PriceContainerImpl(1000, "CHF", new BigDecimal("10.00"), PriceContainer.VatStatus.NOT_INCLUDED, promoCodeDiscount);
        assertEquals(new BigDecimal("9.90"), vs.getFinalPrice());
    }

    @Test
    public void getFinalPriceInputVatNotIncluded() throws Exception {
        generateTestStream("CHF", PriceContainer.VatStatus.NOT_INCLUDED)
            .forEach(p -> {
                PriceContainer priceContainer = p.getRight();
                BigDecimal finalPrice = priceContainer.getFinalPrice();
                Integer price = p.getLeft();
                BigDecimal netPrice = MonetaryUtil.centsToUnit(price);
                BigDecimal vatAmount = finalPrice.subtract(netPrice);
                int result = MonetaryUtil.unitToCents(vatAmount.subtract(MonetaryUtil.calcVat(netPrice, priceContainer.getVatPercentageOrZero())).abs());
                if(result >= 2) {
                    BigDecimal calcVatPerc = vatAmount.divide(finalPrice, 5, RoundingMode.HALF_UP).multiply(new BigDecimal("100.00")).setScale(2, RoundingMode.HALF_UP);
                    fail(String.format("Expected percentage: %s, got %s, vat %s v. %s", calcVatPerc, priceContainer.getOptionalVatPercentage(), vatAmount, MonetaryUtil.calcVat(netPrice, priceContainer.getVatPercentageOrZero())));
                }
            });
    }

    private Stream<Pair<Integer, PriceContainer>> generateTestStream(String currency, PriceContainer.VatStatus vatStatus) {
        List<BigDecimal> vatPercentages = IntStream.range(100, 3000)
            .mapToObj(vatCts -> new BigDecimal(vatCts).divide(new BigDecimal("100.00"), 2, RoundingMode.UNNECESSARY))
            .collect(Collectors.toList());
        return IntStream.range(1, 500_00).
            parallel()
            .mapToObj(Integer::new)
            .flatMap(i -> vatPercentages.stream().map(vat -> Pair.of(i, new PriceContainerImpl(i, currency, vat, vatStatus))));
    }

}