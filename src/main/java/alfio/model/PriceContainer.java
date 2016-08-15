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

import static alfio.util.MonetaryUtil.HUNDRED;
import static java.math.RoundingMode.HALF_UP;
import static java.math.RoundingMode.UNNECESSARY;

public interface PriceContainer {

    enum VatStatus {
        NONE,
        INCLUDED,
        NOT_INCLUDED
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
        final BigDecimal price = MonetaryUtil.centsToUnit(getSrcPriceCts());
        BigDecimal discountedPrice = price.subtract(getAppliedDiscount());
        if(getVatStatus() != VatStatus.NOT_INCLUDED) {
            return discountedPrice;
        } else {
            return discountedPrice.add(getVAT(discountedPrice, getVatStatus(), getVatPercentageOrZero()));
        }
    }

    /**
     * Returns the VAT
     * @return vat
     */
    default BigDecimal getVAT() {
        final BigDecimal price = MonetaryUtil.centsToUnit(getSrcPriceCts());
        return getVAT(price.subtract(getAppliedDiscount()), getVatStatus(), getVatPercentageOrZero());
    }

    /**
     * @return the discount applied, if any
     */
    default BigDecimal getAppliedDiscount() {
        return getDiscount().map(discount -> {
            final BigDecimal price = MonetaryUtil.centsToUnit(getSrcPriceCts());
            if(discount.getFixedAmount()) {
                return MonetaryUtil.centsToUnit(discount.getDiscountAmount());
            } else {
                int discountAmount = discount.getDiscountAmount();
                return price.multiply(new BigDecimal(discountAmount).divide(HUNDRED, 2, UNNECESSARY)).setScale(2, HALF_UP);
            }
        }).orElse(BigDecimal.ZERO);
    }

    static BigDecimal getVAT(BigDecimal price, VatStatus vatStatus, BigDecimal vatPercentage) {
        if(vatStatus != VatStatus.NOT_INCLUDED) {
            return MonetaryUtil.extractVAT(price, vatPercentage).setScale(2, RoundingMode.HALF_UP);
        } else {
            return MonetaryUtil.calcVat(price, vatPercentage).setScale(2, RoundingMode.HALF_UP);
        }
    }

}