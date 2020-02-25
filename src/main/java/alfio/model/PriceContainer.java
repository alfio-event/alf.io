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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

import static alfio.util.MonetaryUtil.*;
import static java.math.RoundingMode.UNNECESSARY;

public interface PriceContainer {

    BinaryOperator<BigDecimal> includedVatExtractor = MonetaryUtil::extractVAT;
    BinaryOperator<BigDecimal> notIncludedVatCalculator = MonetaryUtil::calcVat;

    enum VatStatus {
        NONE((price, vatPercentage) -> BigDecimal.ZERO, UnaryOperator.identity()),
        INCLUDED(includedVatExtractor, UnaryOperator.identity()),
        NOT_INCLUDED(notIncludedVatCalculator, UnaryOperator.identity()),
        INCLUDED_EXEMPT(includedVatExtractor, BigDecimal::negate),
        NOT_INCLUDED_EXEMPT((price, vatPercentage) -> BigDecimal.ZERO, UnaryOperator.identity());

        private final UnaryOperator<BigDecimal> transformer;
        private final BinaryOperator<BigDecimal> extractor;

        VatStatus(BinaryOperator<BigDecimal> extractor,
                  UnaryOperator<BigDecimal> transformer) {
            this.extractor = extractor;
            this.transformer = transformer;
        }

        public BigDecimal extractVat(BigDecimal price, BigDecimal vatPercentage) {
            return this.extractRawVAT(price, vatPercentage).setScale(2, RoundingMode.HALF_UP);
        }

        public BigDecimal extractRawVAT(BigDecimal price, BigDecimal vatPercentage) {
            return this.extractor.andThen(transformer).apply(price, vatPercentage);
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
    default BigDecimal getFinalPrice() {
        var vatStatus = getVatStatus();
        if(getSrcPriceCts() == 0 || vatStatus == null) {
            return BigDecimal.ZERO;
        }
        final BigDecimal price = centsToUnit(getSrcPriceCts(), getCurrencyCode());
        BigDecimal discountedPrice = price.subtract(getAppliedDiscount());
        if(vatStatus != VatStatus.INCLUDED) {
            return discountedPrice.add(getVAT());
        } else {
            return discountedPrice;
        }
    }

    /**
     * Returns the taxable price.
     * This is often the price itself, but it can also be a fraction of it, if some of the items are not taxable
     */
    default BigDecimal getTaxablePrice() {
        return centsToUnit(getSrcPriceCts(), getCurrencyCode()).subtract(getAppliedDiscount());
    }

    /**
     * Returns the VAT
     * @return vat
     */
    @JsonIgnore
    default BigDecimal getVAT() {
        return getVAT(getTaxablePrice(), getVatStatus(), getVatPercentageOrZero());
    }


    /**
     * @return the discount applied, if any
     */
    @JsonIgnore
    default BigDecimal getAppliedDiscount() {
        return getDiscount()
            // do not take into account reservation-level discount
            .filter(discount -> discount.getDiscountType() != PromoCodeDiscount.DiscountType.FIXED_AMOUNT_RESERVATION)
            .map(discount -> {
                String currencyCode = getCurrencyCode();
                final BigDecimal price = centsToUnit(getSrcPriceCts(), currencyCode);
                if(discount.getFixedAmount()) {
                    return centsToUnit(Math.min(getSrcPriceCts(), discount.getDiscountAmount()), currencyCode);
                } else {
                    return fixScale(price.multiply(new BigDecimal(discount.getDiscountAmount()).divide(HUNDRED, 2, UNNECESSARY)), currencyCode);
                }
            }).orElse(BigDecimal.ZERO);
    }

    static BigDecimal getVAT(BigDecimal price, VatStatus vatStatus, BigDecimal vatPercentage) {
        return vatStatus.extractVat(price, vatPercentage);
    }

}