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
package alfio.test.util;

import alfio.model.PriceContainer;
import alfio.model.PromoCodeDiscount;

import java.math.BigDecimal;
import java.util.Optional;

public class PriceContainerImpl implements PriceContainer {

    private final int srcPriceCts;
    private final String currencyCode;
    private final BigDecimal vat;
    private final VatStatus vatStatus;
    private final PromoCodeDiscount promoCodeDiscount;

    public PriceContainerImpl(int srcPriceCts, String currencyCode, BigDecimal vat, VatStatus vatStatus) {
        this(srcPriceCts, currencyCode, vat, vatStatus, null);
    }

    public PriceContainerImpl(int srcPriceCts, String currencyCode, BigDecimal vat, VatStatus vatStatus, PromoCodeDiscount promoCodeDiscount) {
        this.srcPriceCts = srcPriceCts;
        this.currencyCode = currencyCode;
        this.vat = vat;
        this.vatStatus = vatStatus;
        this.promoCodeDiscount = promoCodeDiscount;
    }

    @Override
    public int getSrcPriceCts() {
        return srcPriceCts;
    }

    @Override
    public Optional<PromoCodeDiscount> getDiscount() {
        return Optional.ofNullable(promoCodeDiscount);
    }

    @Override
    public String getCurrencyCode() {
        return currencyCode;
    }

    @Override
    public Optional<BigDecimal> getOptionalVatPercentage() {
        return Optional.ofNullable(vat);
    }

    @Override
    public VatStatus getVatStatus() {
        return vatStatus;
    }

}
