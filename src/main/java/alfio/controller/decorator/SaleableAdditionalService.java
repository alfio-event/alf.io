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
package alfio.controller.decorator;

import alfio.model.AdditionalService;
import alfio.model.Event;
import alfio.model.PriceContainer;
import alfio.model.PromoCodeDiscount;
import alfio.util.ClockProvider;
import lombok.experimental.Delegate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Optional;

import static alfio.util.MonetaryUtil.formatPercentage;

public class SaleableAdditionalService implements PriceContainer {
    private final Event event;
    @Delegate(excludes = {Exclusions.class, PriceContainer.class})
    private final AdditionalService additionalService;
    private final PromoCodeDiscount promoCodeDiscount;
    private final Clock clock;

    public SaleableAdditionalService(Event event,
                                     AdditionalService additionalService,
                                     PromoCodeDiscount promoCodeDiscount) {
        this.event = event;
        this.additionalService = additionalService;
        this.promoCodeDiscount = promoCodeDiscount;
        this.clock = ClockProvider.clock().withZone(event.getZoneId());
    }

    public boolean isExpired() {
        return utcExpiration().isBefore(ZonedDateTime.now(clock));
    }

    public boolean getExpired() {
        return isExpired();
    }

    public boolean getSaleInFuture() {
        return utcInception().isAfter(ZonedDateTime.now(clock));
    }

    public boolean getFree() {
        return fixPrice() && getFinalPrice().compareTo(BigDecimal.ZERO) == 0;
    }

    public ZonedDateTime getZonedInception() {
        return getInception(event.getZoneId());
    }

    public ZonedDateTime getZonedExpiration() {
        return getExpiration(event.getZoneId());
    }

    @Override
    public int getSrcPriceCts() {
        return Optional.ofNullable(additionalService.srcPriceCts()).orElse(0);
    }

    @Override
    public Optional<PromoCodeDiscount> getDiscount() {
        return Optional.ofNullable(promoCodeDiscount)
            .filter(x -> x.getCodeType() == PromoCodeDiscount.CodeType.DISCOUNT && type() != AdditionalService.AdditionalServiceType.DONATION);
    }

    @Override
    public String getCurrencyCode() {
        return event.getCurrency();
    }

    @Override
    public Optional<BigDecimal> getOptionalVatPercentage() {
        if(getVatStatus() != VatStatus.NONE) {
            return Optional.ofNullable(event.getVat()); //FIXME implement VAT override
        }
        return Optional.of(BigDecimal.ZERO);
    }

    @Override
    public VatStatus getVatStatus() {
        return AdditionalService.getVatStatus(vatType(), event.getVatStatus());
    }

    public String getFormattedFinalPrice() {
        if (AdditionalService.SupplementPolicy.isMandatoryPercentage(supplementPolicy())) {
            return formatPercentage(srcPriceCts());
        }
        return SaleableTicketCategory.getFinalPriceToDisplay(getFinalPrice().add(getAppliedDiscount()), getVAT(), getVatStatus()).toPlainString();
    }

    public boolean getSupportsDiscount() {
        return type() != AdditionalService.AdditionalServiceType.DONATION && fixPrice()
            && promoCodeDiscount != null && promoCodeDiscount.getCodeType() == PromoCodeDiscount.CodeType.DISCOUNT;
    }

    public String getDiscountedPrice() {
        return getFinalPrice().toPlainString();
    }

    public boolean getVatIncluded() {
        return switch (vatType()) {
            case INHERITED -> event.isVatIncluded();
            case CUSTOM_INCLUDED -> true;
            default -> false;
        };
    }

    public BigDecimal getVatPercentage() {
        AdditionalService.VatType vatType = vatType();
        if(vatType == AdditionalService.VatType.INHERITED) {
            return event.getVat();
        }
        return Optional.ofNullable(additionalService.vat()).orElse(BigDecimal.ZERO);
    }

    public boolean getVatApplies() {
        return vatType() != AdditionalService.VatType.NONE;
    }


    public String getCurrency() {
        return event.getCurrency();
    }

    public boolean isSaleable() {
        return !isExpired() && additionalService.availableItems() > 0;
    }

    private interface Exclusions {
        BigDecimal getVat();
    }

}
