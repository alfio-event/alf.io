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
import lombok.experimental.Delegate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Optional;

public class SaleableAdditionalService implements PriceContainer {
    private final Event event;
    @Delegate(excludes = {Exclusions.class, PriceContainer.class})
    private final AdditionalService additionalService;
    private final String title;
    private final String description;
    private final PromoCodeDiscount promoCodeDiscount;

    public SaleableAdditionalService(Event event, AdditionalService additionalService, String title, String description, PromoCodeDiscount promoCodeDiscount) {
        this.event = event;
        this.additionalService = additionalService;
        this.title = title;
        this.description = description;
        this.promoCodeDiscount = promoCodeDiscount;
    }

    public boolean isExpired() {
        return getUtcExpiration().isBefore(now());
    }

    public boolean isNotExpired() {
        return !isExpired();
    }

    public boolean getExpired() {
        return isExpired();
    }

    public boolean getSaleInFuture() {
        return getUtcInception().isAfter(now());
    }

    private static ZonedDateTime now() {
        return ZonedDateTime.now(Clock.systemUTC());
    }

    public boolean getFree() {
        return isFixPrice() && getFinalPrice().compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean getSaleable() {
        return getUtcInception().isBefore(now()) && getUtcExpiration().isAfter(now());
    }

    public ZonedDateTime getZonedInception() {
        return getInception(event.getZoneId());
    }

    public ZonedDateTime getZonedExpiration() {
        return getExpiration(event.getZoneId());
    }

    @Override
    public int getSrcPriceCts() {
        return Optional.ofNullable(additionalService.getSrcPriceCts()).orElse(0);
    }

    @Override
    public Optional<PromoCodeDiscount> getDiscount() {
        return Optional.ofNullable(promoCodeDiscount);
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
        switch (getVatType()) {
            case INHERITED:
                return event.getVatStatus();
            case NONE:
                return VatStatus.NONE;
            case CUSTOM_EXCLUDED:
                return VatStatus.NOT_INCLUDED;
            case CUSTOM_INCLUDED:
                return VatStatus.INCLUDED;
            default:
                return VatStatus.NOT_INCLUDED;
        }
    }

    public String getFormattedFinalPrice() {
        return getFinalPrice().add(getAppliedDiscount()).toPlainString();
    }

    public boolean getSupportsDiscount() {
        return isFixPrice() && promoCodeDiscount != null;
    }

    public boolean getUserDefinedPrice() {
        return !isFixPrice();
    }

    public String getDiscountedPrice() {
        return getFinalPrice().toPlainString();
    }

    public boolean getVatIncluded() {
        switch (getVatType()) {
            case INHERITED:
                return event.isVatIncluded();
            case NONE:
            case CUSTOM_EXCLUDED:
                return false;
            case CUSTOM_INCLUDED:
                return true;
            default:
                return false;
        }
    }

    public BigDecimal getVat() {
        AdditionalService.VatType vatType = getVatType();
        if(vatType == AdditionalService.VatType.INHERITED) {
            return event.getVat();
        }
        return additionalService.getVat();
    }

    public boolean getVatApplies() {
        return getVatType() != AdditionalService.VatType.NONE;
    }

    public int[] getAmountOfTickets() {
        return new int[]{0, 1};
    }

    public boolean getSoldOut() {
        return false;
    }

    private boolean getAccessRestricted() {
        return false;
    }

    public String getCurrency() {
        return event.getCurrency();
    }

    private interface Exclusions {
        BigDecimal getVat();
    }

}
