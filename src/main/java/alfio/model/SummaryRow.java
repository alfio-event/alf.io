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

public record SummaryRow(String name,
                         String price,
                         String priceBeforeVat,
                         int amount,
                         String subTotal,
                         String subTotalBeforeVat,
                         int originalSubTotal,
                         SummaryType type,
                         String taxPercentage,
                         PriceContainer.VatStatus vatStatus) {
    public enum SummaryType {
        TICKET, SUBSCRIPTION, PROMOTION_CODE, DYNAMIC_DISCOUNT, ADDITIONAL_SERVICE, APPLIED_SUBSCRIPTION, TAX_DETAIL
    }

    public String getDescriptionForPayment() {
        if (name != null) {
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
