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

import java.math.BigDecimal;
import java.util.List;

import static alfio.util.MonetaryUtil.centsToUnit;

public interface SummaryPriceContainer extends PriceContainer {

    Integer getFinalPriceCts();

    static int getSummaryPriceBeforeVatCts(List<? extends SummaryPriceContainer> elements) {
        var currencyCode = !elements.isEmpty() ? elements.get(0).getCurrencyCode() : null;
        return elements.stream().map(item -> {
            PriceContainer.VatStatus vatStatus = item.getVatStatus();
            if(vatStatus == PriceContainer.VatStatus.NOT_INCLUDED_EXEMPT) {
                return MonetaryUtil.centsToUnit(item.getSrcPriceCts(), currencyCode);
            } else if(vatStatus == PriceContainer.VatStatus.INCLUDED_EXEMPT) {
                return MonetaryUtil.centsToUnit(item.getSrcPriceCts(), currencyCode).add(vatStatus.extractRawVAT(centsToUnit(item.getSrcPriceCts(), item.getCurrencyCode()), item.getVatPercentageOrZero()));
            }
            return MonetaryUtil.centsToUnit(item.getFinalPriceCts(), currencyCode).subtract(item.getRawVAT());
        }).reduce(BigDecimal::add).map(p -> MonetaryUtil.unitToCents(p, currencyCode)).orElse(0);
    }
}
