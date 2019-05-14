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
package alfio.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.joda.money.BigMoney;
import org.joda.money.CurrencyUnit;

import static alfio.util.MonetaryUtil.centsToUnit;

@Getter
public class PriceDescriptor {

    private final BigMoney priceAfterTax;
    private final BigMoney tax;
    private final BigMoney discount;
    private final int discountAppliedCount;


    @JsonCreator
    public PriceDescriptor(@JsonProperty("priceAfterTax") BigMoney priceAfterTax,
                           @JsonProperty("tax") BigMoney tax,
                           @JsonProperty("discount") BigMoney discount,
                           @JsonProperty("discountAppliedCount") int discountAppliedCount) {
        this.priceAfterTax = priceAfterTax;
        this.tax = tax;
        this.discount = discount;
        this.discountAppliedCount = discountAppliedCount;
    }

    public boolean requiresPayment() {
        return priceAfterTax.isPositive();
    }

    public static PriceDescriptor from(String currencyCode, TotalPrice price) {
        var currencyUnit = CurrencyUnit.of(currencyCode);
        return new PriceDescriptor(centsToUnit(currencyUnit, price.getPriceWithVAT()),
            centsToUnit(currencyUnit, price.getVAT()), centsToUnit(currencyUnit, price.getDiscount()), price.getDiscountAppliedCount());
    }
}
