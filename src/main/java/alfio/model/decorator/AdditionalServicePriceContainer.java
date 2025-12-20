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

import alfio.model.AdditionalService;
import alfio.model.Event;
import alfio.model.PriceContainer;
import alfio.model.PromoCodeDiscount;
import alfio.util.MonetaryUtil;

import java.beans.ConstructorProperties;
import java.math.BigDecimal;
import java.util.Optional;

public class AdditionalServicePriceContainer implements PriceContainer {

    private final BigDecimal customAmount;
    private final AdditionalService additionalService;
    private final PromoCodeDiscount promoCodeDiscount;
    private final String currencyCode;
    private final BigDecimal vatPercentage;
    private final VatStatus vatStatus;

    @ConstructorProperties({"customAmount", "additionalService", "promoCodeDiscount", "currencyCode", "vatPercentage", "vatStatus"})
    private AdditionalServicePriceContainer(BigDecimal customAmount, AdditionalService additionalService, PromoCodeDiscount promoCodeDiscount, String currencyCode, BigDecimal vatPercentage, VatStatus vatStatus) {
        this.customAmount = customAmount;
        this.additionalService = additionalService;
        this.promoCodeDiscount = promoCodeDiscount;
        this.currencyCode = currencyCode;
        this.vatPercentage = vatPercentage;
        this.vatStatus = vatStatus;
    }

    @Override
    public int getSrcPriceCts() {
        if (AdditionalService.SupplementPolicy.isMandatoryPercentage(additionalService.supplementPolicy()) && customAmount != null) {
            return MonetaryUtil.unitToCents(customAmount, currencyCode);
        }
        if(additionalService.fixPrice()) {
            return additionalService.srcPriceCts();
        }
        return Optional.ofNullable(customAmount).map(a -> MonetaryUtil.unitToCents(a, currencyCode)).orElse(0);
    }

    @Override
    public Optional<PromoCodeDiscount> getDiscount() {
        return Optional.ofNullable(promoCodeDiscount).filter(d -> additionalService.type() != AdditionalService.AdditionalServiceType.DONATION);
    }

    @Override
    public String getCurrencyCode() {
        return currencyCode;
    }

    @Override
    public Optional<BigDecimal> getOptionalVatPercentage() {
        if(additionalService.vatType() == AdditionalService.VatType.INHERITED) {
            return Optional.ofNullable(vatPercentage);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public VatStatus getVatStatus() {
        if(additionalService.vatType() == AdditionalService.VatType.INHERITED) {
            return vatStatus;
        } else {
            return VatStatus.NONE;
        }
    }

    public static AdditionalServicePriceContainer from(BigDecimal customAmount, AdditionalService as, Event event, PromoCodeDiscount discount) {
        return new AdditionalServicePriceContainer(customAmount, as, discount, event.getCurrency(), event.getVat(), event.getVatStatus());
    }
}
