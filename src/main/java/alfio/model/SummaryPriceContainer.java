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

import alfio.util.MonetaryUtil;
import org.joda.money.BigMoney;
import org.joda.money.CurrencyUnit;

import java.util.List;

import static alfio.util.MonetaryUtil.centsToUnit;

public interface SummaryPriceContainer extends PriceContainer {

    Integer getFinalPriceCts();

    static BigMoney getSummaryPriceBeforeVat(String currencyCode, List<? extends SummaryPriceContainer> elements) {
        var currencyUnit = CurrencyUnit.of(currencyCode);
        return elements.stream().map(item -> {
            PriceContainer.VatStatus vatStatus = item.getVatStatus();
            if(vatStatus == PriceContainer.VatStatus.NOT_INCLUDED_EXEMPT) {
                return centsToUnit(currencyUnit, item.getSrcPriceCts());
            } else if(vatStatus == PriceContainer.VatStatus.INCLUDED_EXEMPT) {
                return centsToUnit(currencyUnit, item.getSrcPriceCts()).plus(vatStatus.extractRawVAT(centsToUnit(currencyUnit, item.getSrcPriceCts()), item.getVatPercentageOrZero()));
            }
            return MonetaryUtil.centsToUnit(currencyUnit, item.getFinalPriceCts()).minus(item.getRawVAT());
        }).reduce(BigMoney::plus).orElse(BigMoney.zero(currencyUnit));
    }
}
