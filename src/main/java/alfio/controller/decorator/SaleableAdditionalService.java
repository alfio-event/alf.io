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
import alfio.model.PromoCodeDiscount;
import alfio.util.EventUtil;
import lombok.experimental.Delegate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Optional;

import static alfio.util.MonetaryUtil.formatCents;

public class SaleableAdditionalService {
    private final Event event;
    @Delegate(excludes = Exclusions.class)
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
        return isFixPrice() && getPriceInCents() == 0;
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

    public String getFinalPrice() {
        return formatCents(getFinalPriceInCents());
    }

    private int getFinalPriceInCents() {
        return EventUtil.getFinalPriceInCents(event, additionalService);
    }

    public boolean getSupportsDiscount() {
        return isFixPrice() && promoCodeDiscount != null;
    }

    public boolean getUserDefinedPrice() {
        return !isFixPrice();
    }

    public String getDiscountedPrice() {
        return Optional.ofNullable(promoCodeDiscount).map(d -> formatCents(DecoratorUtil.calcDiscount(d, getFinalPriceInCents()))).orElseGet(this::getFinalPrice);
    }

    public boolean getVatIncluded() {
        switch (getVatType()) {
            case INHERITED:
                return event.isVatIncluded();
            case NONE:
            case CUSTOM_EXCLUDED:
                return false;
            case CUSTOM_INCLUDED:
                return false;
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

    private interface Exclusions {
        BigDecimal getVat();
    }

}
