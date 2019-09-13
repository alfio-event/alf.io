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
import lombok.Data;

import java.util.List;
import java.util.stream.Collectors;

@Data
public class OrderSummary {
    private final TotalPrice originalTotalPrice;
    private final List<SummaryRow> summary;
    private final boolean free;
    private final String totalPrice;
    private final String totalVAT;
    private final boolean waitingForPayment;
    private final boolean cashPayment;
    private final String vatPercentage;
    private final PriceContainer.VatStatus vatStatus;
    private final String refundedAmount;

    /* lol jmustache */
    public boolean getFree() {
        return free;
    }

    public boolean getWaitingForPayment() {
        return waitingForPayment;
    }

    public boolean getCashPayment() {
        return cashPayment;
    }

    public boolean getNotYetPaid() {
        return waitingForPayment || cashPayment;
    }

    public int getTicketAmount() {
        return summary.stream().filter(s-> SummaryRow.SummaryType.TICKET == s.getType()).mapToInt(SummaryRow::getAmount).sum();
    }

    public List<SummaryRow> getSummary() {

        //filter out the promotions code that have been inserted in the order but not used
        return summary.stream()
            .filter(s-> !(SummaryRow.SummaryType.PROMOTION_CODE == s.getType() && s.getAmount() == 0))
            .collect(Collectors.toList());
    }

    public boolean getSingleTicketOrder() {
        return getTicketAmount() == 1;
    }

    public boolean getDisplayVat() {
        return !isVatExempt();
    }

    public boolean isVatExempt() {
        return PriceContainer.VatStatus.isVatExempt(vatStatus);
    }

    public String getRefundedAmount() {
        return refundedAmount;
    }

    public String getTotalNetPrice() {
        if(free) {
            return null;
        }
        return MonetaryUtil.formatCents(originalTotalPrice.getPriceWithVAT() - originalTotalPrice.getVAT(), originalTotalPrice.getCurrencyCode());// FIXME can be null
    }

    public int getPriceInCents() {
        return originalTotalPrice.getPriceWithVAT();
    }

    public String getDescriptionForPayment() {
        return summary.stream().filter(r -> r.getType() != SummaryRow.SummaryType.PROMOTION_CODE).map(SummaryRow::getDescriptionForPayment).collect(Collectors.joining(", "));
    }
}
