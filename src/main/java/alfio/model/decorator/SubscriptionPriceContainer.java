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
