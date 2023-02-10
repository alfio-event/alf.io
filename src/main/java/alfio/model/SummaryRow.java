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

import lombok.Data;

@Data
public class SummaryRow {
    private final String name;
    private final String price;
    private final String priceBeforeVat;
    private final int amount;
    private final String subTotal;
    private final String subTotalBeforeVat;
    private final int originalSubTotal;
    private final SummaryType type;
    private final String taxPercentage;
    private final PriceContainer.VatStatus vatStatus;

    public enum SummaryType {
        TICKET, SUBSCRIPTION, PROMOTION_CODE, DYNAMIC_DISCOUNT, ADDITIONAL_SERVICE, APPLIED_SUBSCRIPTION, TAX_DETAIL
    }

    public String getDescriptionForPayment() {
        if(name != null) {
            return amount + " x " + name;
        }
        return "";
    }

    public boolean isDiscount() {
        return type == SummaryType.PROMOTION_CODE || type == SummaryType.DYNAMIC_DISCOUNT;
    }

    public boolean getTaxDetail() {
        return type == SummaryType.TAX_DETAIL;
    }
}
