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
import alfio.util.MonetaryUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

import java.math.BigDecimal;
import java.util.Optional;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class AdditionalServiceItemPriceContainer implements SummaryPriceContainer {
    @Delegate(excludes = PriceContainer.class)
    private final AdditionalServiceItem additionalServiceItem;
    private final AdditionalService additionalService;
    private final String currencyCode;
    private final PromoCodeDiscount discount;
    private final VatStatus eventVatStatus;
    private final BigDecimal eventVatPercentage;

    @Override
    public int getSrcPriceCts() {
        return Optional.ofNullable(additionalServiceItem.getSrcPriceCts()).orElse(0);
    }

    @Override
    public Optional<PromoCodeDiscount> getDiscount() {
        return Optional.ofNullable(discount).filter(d -> d.getDiscountType() != PromoCodeDiscount.DiscountType.FIXED_AMOUNT_RESERVATION);
    }

    @Override
    public String getCurrencyCode() {
        return currencyCode;
    }

    @Override
    public Optional<BigDecimal> getOptionalVatPercentage() {
        if(additionalService.getVatType() == AdditionalService.VatType.INHERITED) {
            return Optional.ofNullable(eventVatPercentage);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public VatStatus getVatStatus() {
        if(additionalService.getVatType() == AdditionalService.VatType.INHERITED) {
            return eventVatStatus;
        } else {
            return VatStatus.NONE;
        }
    }

    @Override
    public BigDecimal getTaxablePrice() {
        if(getVatStatus() == VatStatus.NONE) {
            return BigDecimal.ZERO;
        }
        return MonetaryUtil.centsToUnit(getSrcPriceCts(), getCurrencyCode());
    }

    public static AdditionalServiceItemPriceContainer from(AdditionalServiceItem item, AdditionalService additionalService, PurchaseContext purchaseContext, PromoCodeDiscount discount) {
        var discountToApply = isDiscountCompatible(discount) && additionalService.getType() != AdditionalService.AdditionalServiceType.DONATION ? discount : null;
        return new AdditionalServiceItemPriceContainer(item, additionalService, purchaseContext.getCurrency(), discountToApply, purchaseContext.getVatStatus(), purchaseContext.getVat());
    }

    private static boolean isDiscountCompatible(PromoCodeDiscount discount) {
        return discount != null
            && discount.getCodeType() == PromoCodeDiscount.CodeType.DISCOUNT
            && discount.getDiscountType() == PromoCodeDiscount.DiscountType.PERCENTAGE;
    }
}
