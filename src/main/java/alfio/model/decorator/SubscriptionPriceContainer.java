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

import alfio.model.PriceContainer;
import alfio.model.PromoCodeDiscount;
import alfio.model.PurchaseContext;
import alfio.model.subscription.Subscription;

import java.math.BigDecimal;
import java.util.Optional;

public class SubscriptionPriceContainer implements PriceContainer {


    private final Subscription subscription;
    private final PurchaseContext purchaseContext;
    private final PromoCodeDiscount promoCodeDiscount;

    public SubscriptionPriceContainer(Subscription s, PurchaseContext purchaseContext, PromoCodeDiscount discount) {
        this.subscription = s;
        this.purchaseContext = purchaseContext;
        this.promoCodeDiscount = discount;
    }

    public static PriceContainer from(Subscription s, PurchaseContext purchaseContext, PromoCodeDiscount discount) {
        return new SubscriptionPriceContainer(s, purchaseContext, discount);
    }

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
        return Optional.ofNullable(purchaseContext.getVat());
    }

    @Override
    public VatStatus getVatStatus() {
        return purchaseContext.getVatStatus();
    }
}
