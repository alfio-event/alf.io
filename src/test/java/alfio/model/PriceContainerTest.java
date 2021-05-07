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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PriceContainerTest {

    @Test
    public void getFinalPriceInputDoNotApplyVat() {
        Stream.of(PriceContainer.VatStatus.INCLUDED, PriceContainer.VatStatus.NONE)
            .forEach(vatStatus -> {
                PriceContainerImpl vs = new PriceContainerImpl(1000, "CHF", new BigDecimal("30.00"), vatStatus);
                assertEquals(new BigDecimal("10.00"), vs.getFinalPrice());
                PromoCodeDiscount promoCodeDiscount = mock(PromoCodeDiscount.class);
                when(promoCodeDiscount.getDiscountAmount()).thenReturn(100, 10);
                when(promoCodeDiscount.getFixedAmount()).thenReturn(true, false);
                when(promoCodeDiscount.getDiscountType()).thenReturn(PromoCodeDiscount.DiscountType.FIXED_AMOUNT, PromoCodeDiscount.DiscountType.PERCENTAGE);

                vs = new PriceContainerImpl(1100, "CHF", new BigDecimal("30.00"), vatStatus, promoCodeDiscount);
                assertEquals(new BigDecimal("10.00"), vs.getFinalPrice(), String.format("vatStatus: %s", vatStatus.name()));

                vs = new PriceContainerImpl(1000, "CHF", new BigDecimal("30.00"), vatStatus, promoCodeDiscount);
                assertEquals(new BigDecimal("9.00"), vs.getFinalPrice(), String.format("vatStatus: %s", vatStatus.name()));
            });
    }

    @Test
    public void getFinalPriceInputVatNotIncludedSingle() {
        PriceContainerImpl vs = new PriceContainerImpl(1000, "CHF", new BigDecimal("30.00"), PriceContainer.VatStatus.NOT_INCLUDED);
        assertEquals(new BigDecimal("13.00"), vs.getFinalPrice());

        PromoCodeDiscount promoCodeDiscount = mock(PromoCodeDiscount.class);

        when(promoCodeDiscount.getDiscountAmount()).thenReturn(100);
        when(promoCodeDiscount.getFixedAmount()).thenReturn(true);
        when(promoCodeDiscount.getDiscountType()).thenReturn(PromoCodeDiscount.DiscountType.FIXED_AMOUNT);
        vs = new PriceContainerImpl(1100, "CHF", new BigDecimal("8.00"), PriceContainer.VatStatus.NOT_INCLUDED, promoCodeDiscount);
        assertEquals(new BigDecimal("10.80"), vs.getFinalPrice());

        when(promoCodeDiscount.getDiscountAmount()).thenReturn(10);
        when(promoCodeDiscount.getFixedAmount()).thenReturn(false);
        when(promoCodeDiscount.getDiscountType()).thenReturn(PromoCodeDiscount.DiscountType.PERCENTAGE);
        vs = new PriceContainerImpl(1000, "CHF", new BigDecimal("10.00"), PriceContainer.VatStatus.NOT_INCLUDED, promoCodeDiscount);
        assertEquals(new BigDecimal("9.90"), vs.getFinalPrice());
    }

    @Test
    public void getFinalPriceInputVatNotIncludedExemptSingle() {
        PriceContainerImpl vs = new PriceContainerImpl(1000, "CHF", new BigDecimal("30.00"), PriceContainer.VatStatus.NOT_INCLUDED_EXEMPT);
        assertEquals(new BigDecimal("10.00"), vs.getFinalPrice());

        PromoCodeDiscount promoCodeDiscount = mock(PromoCodeDiscount.class);

        when(promoCodeDiscount.getDiscountAmount()).thenReturn(100);
        when(promoCodeDiscount.getFixedAmount()).thenReturn(true);
        when(promoCodeDiscount.getDiscountType()).thenReturn(PromoCodeDiscount.DiscountType.FIXED_AMOUNT);
        vs = new PriceContainerImpl(1100, "CHF", new BigDecimal("8.00"), PriceContainer.VatStatus.NOT_INCLUDED_EXEMPT, promoCodeDiscount);
        assertEquals(new BigDecimal("10.00"), vs.getFinalPrice());

        when(promoCodeDiscount.getDiscountAmount()).thenReturn(10);
        when(promoCodeDiscount.getFixedAmount()).thenReturn(false);
        when(promoCodeDiscount.getDiscountType()).thenReturn(PromoCodeDiscount.DiscountType.PERCENTAGE);
        vs = new PriceContainerImpl(1000, "CHF", new BigDecimal("10.00"), PriceContainer.VatStatus.NOT_INCLUDED_EXEMPT, promoCodeDiscount);
        assertEquals(new BigDecimal("9.00"), vs.getFinalPrice());
    }

    @Test
    public void getFinalPriceInputVatIncludedExemptSingle() {
        PriceContainerImpl vs = new PriceContainerImpl(1000, "CHF", new BigDecimal("30.00"), PriceContainer.VatStatus.INCLUDED_EXEMPT);
        assertEquals(new BigDecimal("7.69"), vs.getFinalPrice());

        PromoCodeDiscount promoCodeDiscount = mock(PromoCodeDiscount.class);
        when(promoCodeDiscount.getDiscountAmount()).thenReturn(100);
        when(promoCodeDiscount.getFixedAmount()).thenReturn(true);
        when(promoCodeDiscount.getDiscountType()).thenReturn(PromoCodeDiscount.DiscountType.FIXED_AMOUNT);

        vs = new PriceContainerImpl(1100, "CHF", new BigDecimal("8.00"), PriceContainer.VatStatus.INCLUDED_EXEMPT, promoCodeDiscount);
        assertEquals(new BigDecimal("9.26"), vs.getFinalPrice());

        when(promoCodeDiscount.getDiscountAmount()).thenReturn(10);
        when(promoCodeDiscount.getFixedAmount()).thenReturn(false);
        when(promoCodeDiscount.getDiscountType()).thenReturn(PromoCodeDiscount.DiscountType.PERCENTAGE);
        vs = new PriceContainerImpl(1000, "CHF", new BigDecimal("10.00"), PriceContainer.VatStatus.INCLUDED_EXEMPT, promoCodeDiscount);
        assertEquals(new BigDecimal("8.18"), vs.getFinalPrice());
    }

    @Test
    public void getFinalPriceInputVatNotIncluded() {
        generateTestStream(PriceContainer.VatStatus.NOT_INCLUDED)
            .forEach(p -> {
                PriceContainer priceContainer = p.getRight();
                BigDecimal finalPrice = priceContainer.getFinalPrice();
                Integer price = p.getLeft();
                BigDecimal netPrice = MonetaryUtil.centsToUnit(price, "CHF");
                BigDecimal vatAmount = finalPrice.subtract(netPrice);
                int result = MonetaryUtil.unitToCents(vatAmount.subtract(MonetaryUtil.calcVat(netPrice, priceContainer.getVatPercentageOrZero())).abs(), "CHF");
                if(result >= 2) {
                    BigDecimal calcVatPerc = vatAmount.divide(finalPrice, 5, RoundingMode.HALF_UP).multiply(new BigDecimal("100.00")).setScale(2, RoundingMode.HALF_UP);
                    fail(String.format("Expected percentage: %s, got %s, vat %s v. %s", calcVatPerc, priceContainer.getOptionalVatPercentage(), vatAmount, MonetaryUtil.calcVat(netPrice, priceContainer.getVatPercentageOrZero())));
                }
            });
    }

    @Test
    void testDoNotProduceNegativePrices() {
        var discount = mock(PromoCodeDiscount.class);
        when(discount.getCodeType()).thenReturn(PromoCodeDiscount.CodeType.DISCOUNT);
        when(discount.getDiscountType()).thenReturn(PromoCodeDiscount.DiscountType.FIXED_AMOUNT);
        when(discount.getFixedAmount()).thenReturn(true);
        when(discount.getDiscountAmount()).thenReturn(3100);

        PriceContainerImpl vs = new PriceContainerImpl(1000, "CHF", new BigDecimal("30.00"), PriceContainer.VatStatus.INCLUDED_EXEMPT, discount);
        assertEquals(new BigDecimal("0.00"), vs.getFinalPrice());
    }

    @Test
    void totalPriceZeroIfVatStatusIsNull() {
        PriceContainerImpl vs = new PriceContainerImpl(1000, "CHF", new BigDecimal("30.00"), null);
        assertEquals(BigDecimal.ZERO, vs.getFinalPrice());
    }

    @Test
    void totalPriceDoesNotIncludeVatIfSplitPayment() {
        PriceContainerImpl vs = new PriceContainerImpl(1100, "CHF", new BigDecimal("10.00"), PriceContainer.VatStatus.INCLUDED_NOT_CHARGED);
        assertEquals(new BigDecimal("10.00"), vs.getFinalPrice());
        assertEquals(new BigDecimal("1.00"), vs.getVAT());

        vs = new PriceContainerImpl(1000, "CHF", new BigDecimal("10.00"), PriceContainer.VatStatus.NOT_INCLUDED_NOT_CHARGED);
        assertEquals(new BigDecimal("10.00"), vs.getFinalPrice());
        assertEquals(new BigDecimal("1.00"), vs.getVAT());
    }

    private Stream<Pair<Integer, PriceContainer>> generateTestStream(PriceContainer.VatStatus vatStatus) {
        List<BigDecimal> vatPercentages = IntStream.range(100, 3000)
            .mapToObj(vatCts -> new BigDecimal(vatCts).divide(new BigDecimal("100.00"), 2, RoundingMode.UNNECESSARY))
            .collect(Collectors.toList());
        return IntStream.range(1, 5_000).
            parallel()
            .boxed()
            .flatMap(i -> vatPercentages.stream().map(vat -> Pair.of(i, new PriceContainerImpl(i, "CHF", vat, vatStatus))));
    }

}