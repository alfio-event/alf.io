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
package alfio.model.subscription;

import alfio.model.PriceContainer;
import alfio.model.PromoCodeDiscount;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.Optional;

import static alfio.util.MonetaryUtil.centsToUnit;
import static alfio.util.MonetaryUtil.unitToCents;

@AllArgsConstructor
public class SubscriptionPriceContainer implements PriceContainer {

    private final Subscription subscription;
    private final PromoCodeDiscount promoCodeDiscount;
    private final SubscriptionDescriptor descriptor;


    @Override
    public int getSrcPriceCts() {
        return subscription.getSrcPriceCts();
    }

    @Override
    public String getCurrencyCode() {
        return subscription.getCurrency();
    }

    @Override
    public Optional<BigDecimal> getOptionalVatPercentage() {
        return Optional.ofNullable(descriptor.getVat());
    }

    @Override
    public VatStatus getVatStatus() {
        return descriptor.getVatStatus();
    }

    @Override
    public Optional<PromoCodeDiscount> getDiscount() {
        return Optional.ofNullable(promoCodeDiscount);
    }

    // FIXME remove once merged, as it has been implemented in Master
    public BigDecimal getNetPrice() {
        var vatStatus = getVatStatus();
        var currencyCode = getCurrencyCode();
        if(vatStatus == VatStatus.NOT_INCLUDED_EXEMPT) {
            return centsToUnit(getSrcPriceCts(), currencyCode);
        } else if(vatStatus == VatStatus.INCLUDED_EXEMPT) {
            var rawVat = vatStatus.extractRawVAT(centsToUnit(getSrcPriceCts(), getCurrencyCode()), getVatPercentageOrZero());
            return centsToUnit(getSrcPriceCts(), currencyCode).add(rawVat);
        } else if(vatStatus == VatStatus.INCLUDED) {
            var rawVat = vatStatus.extractRawVAT(centsToUnit(getSrcPriceCts(), getCurrencyCode()), getVatPercentageOrZero());
            return centsToUnit(getSrcPriceCts(), currencyCode).subtract(rawVat);
        } else {
            return centsToUnit(getSrcPriceCts(), currencyCode);
        }
    }

    public int getSummarySrcPriceCts() {
        if(VatStatus.isVatExempt(getVatStatus())) {
            return unitToCents(getFinalPrice(), getCurrencyCode());
        }
        return getSrcPriceCts();
    }
}
