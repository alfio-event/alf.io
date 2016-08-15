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
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

import java.math.BigDecimal;
import java.util.Optional;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class AdditionalServiceItemPriceContainer implements PriceContainer {
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
        return Optional.ofNullable(discount);
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

    public static AdditionalServiceItemPriceContainer from(AdditionalServiceItem item, AdditionalService additionalService, Event event, PromoCodeDiscount discount) {
        return new AdditionalServiceItemPriceContainer(item, additionalService, event.getCurrency(), discount, event.getVatStatus(), event.getVat());
    }
}
