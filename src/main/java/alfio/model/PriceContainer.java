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

import alfio.util.MonetaryUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.joda.money.BigMoney;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

import static alfio.util.MonetaryUtil.HUNDRED;
import static java.math.RoundingMode.HALF_UP;

public interface PriceContainer {

    BiFunction<BigMoney, BigDecimal, Optional<BigMoney>> includedVatExtractor = (price, vat) -> Optional.of(MonetaryUtil.extractVAT(price, vat));
    BiFunction<BigMoney, BigDecimal, Optional<BigMoney>> notIncludedVatCalculator = (price, vat) -> Optional.of(MonetaryUtil.calcVat(price, vat));

    enum VatStatus {
        NONE((price, vatPercentage) -> Optional.empty(), UnaryOperator.identity()),
        INCLUDED(includedVatExtractor, UnaryOperator.identity()),
        NOT_INCLUDED(notIncludedVatCalculator, UnaryOperator.identity()),
        INCLUDED_EXEMPT(includedVatExtractor, p -> p.map(BigMoney::negated)),
        NOT_INCLUDED_EXEMPT((price, vatPercentage) -> Optional.empty(), UnaryOperator.identity());

        private final UnaryOperator<Optional<BigMoney>> transformer;
        private final BiFunction<BigMoney, BigDecimal, Optional<BigMoney>> extractor;

        VatStatus(BiFunction<BigMoney, BigDecimal, Optional<BigMoney>> extractor,
                  UnaryOperator<Optional<BigMoney>> transformer) {
            this.extractor = extractor;
            this.transformer = transformer;
        }

        public Money extractVat(BigMoney price, BigDecimal vatPercentage) {
            return this.extractRawVAT(price, vatPercentage).toMoney(RoundingMode.HALF_UP);
        }

        public BigMoney extractRawVAT(BigMoney price, BigDecimal vatPercentage) {
            return this.extractor.andThen(transformer).apply(price, vatPercentage).orElse(BigMoney.zero(price.getCurrencyUnit()));
        }

        public static boolean isVatExempt(VatStatus vatStatus) {
            return vatStatus == INCLUDED_EXEMPT || vatStatus == NOT_INCLUDED_EXEMPT;
        }
    }

    /**
     * @return The price defined by the event organizer, can be either VAT included or not.
     */
    int getSrcPriceCts();

    /**
     * @return the discount to apply, if any. The default implementation returns empty.
     */
    @JsonIgnore
    default Optional<PromoCodeDiscount> getDiscount() {
        return Optional.empty();
    }

    /**
     * Returns the currency in which the user will be charged.
     * @return currency code
     */
    String getCurrencyCode();

    /**
     * Returns the VAT percentage to apply.
     * @return VAT percentage
     */
    @JsonIgnore
    Optional<BigDecimal> getOptionalVatPercentage();

    /**
     * utility method. It returns the VAT percentage defined or {@link BigDecimal#ZERO}
     * @return the VAT percentage defined or {@link BigDecimal#ZERO}
     */
    @JsonIgnore
    default BigDecimal getVatPercentageOrZero() {
        return getOptionalVatPercentage().orElse(BigDecimal.ZERO);
    }

    /**
     * Returns the VAT Status (none, included, not included)
     * @return VatStatus
     */
    VatStatus getVatStatus();

    /**
     * Returns the price AFTER tax
     * @return net + tax
     */
    default BigMoney getFinalPrice() {
        final BigMoney price = MonetaryUtil.centsToUnit(getCurrencyCode(), getSrcPriceCts());
        BigMoney discountedPrice = price.minus(getAppliedDiscount());
        if(getVatStatus() != VatStatus.INCLUDED) {
            return discountedPrice.plus(getVAT(discountedPrice, getVatStatus(), getVatPercentageOrZero()));
        } else {
            return discountedPrice;
        }
    }

    /**
     * Returns the VAT
     * @return vat
     */
    default Money getVAT() {
        final BigMoney price = MonetaryUtil.centsToUnit(getCurrencyCode(), getSrcPriceCts());
        return getVAT(price.minus(getAppliedDiscount()), getVatStatus(), getVatPercentageOrZero());
    }


    /**
     * Returns the VAT, with a reasonable, less error-prone, rounding
     * @return vat
     * @see MonetaryUtil#ROUNDING_SCALE
     */
    default BigMoney getRawVAT() {
        final BigMoney price = MonetaryUtil.centsToUnit(getCurrencyCode(), getSrcPriceCts());
        return getVatStatus().extractRawVAT(price.minus(getAppliedDiscount()), getVatPercentageOrZero());
    }


    /**
     * @return the discount applied, if any
     */
    default BigMoney getAppliedDiscount() {
        return getDiscount().map(discount -> {
            if(discount.getFixedAmount()) {
                return MonetaryUtil.centsToUnit(getCurrencyCode(), discount.getDiscountAmount());
            } else {
                final BigMoney price = MonetaryUtil.centsToUnit(getCurrencyCode(), getSrcPriceCts());
                int discountAmount = discount.getDiscountAmount();
                return price.multipliedBy(new BigDecimal(discountAmount).divide(HUNDRED, 2, HALF_UP));
            }
        }).orElse(BigMoney.zero(CurrencyUnit.of(getCurrencyCode())));
    }

    default String getFormattedFinalPrice() {
        return MonetaryUtil.formatAmount(getFinalPrice(), false);
    }

    default String getFormattedNetPrice() {
        return MonetaryUtil.formatAmount(getFinalPrice().minus(getAppliedDiscount()), false);
    }

    static Money getVAT(BigMoney price, VatStatus vatStatus, BigDecimal vatPercentage) {
        return vatStatus.extractVat(price, vatPercentage);
    }

}