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

import alfio.model.PromoCodeDiscount;
import alfio.model.SummaryPriceContainer;
import alfio.model.Ticket;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

import java.math.BigDecimal;
import java.util.Optional;

import static alfio.util.MonetaryUtil.unitToCents;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class TicketPriceContainer implements SummaryPriceContainer {

    @Delegate(excludes = OverridePriceContainer.class)
    private final Ticket ticket;
    private final PromoCodeDiscount promoCodeDiscount;
    private final BigDecimal vatPercentage;
    private final VatStatus vatStatus;

    @Override
    public Optional<PromoCodeDiscount> getDiscount() {
        return Optional.ofNullable(promoCodeDiscount)
            .filter(discount -> discount.getCategories().isEmpty() || discount.getCategories().contains(getCategoryId()));
    }

    @Override
    public Optional<BigDecimal> getOptionalVatPercentage() {
        return Optional.ofNullable(vatPercentage);
    }

    @Override
    public VatStatus getVatStatus() {
        return vatStatus;
    }

    public int getSummarySrcPriceCts() {
        if(VatStatus.isVatExempt(getVatStatus())) {
            return unitToCents(getFinalPrice(), getCurrencyCode());
        }
        return getSrcPriceCts();
    }

    public static TicketPriceContainer from(Ticket t, VatStatus reservationVatStatus, BigDecimal vat, VatStatus eventVatStatus, PromoCodeDiscount discount) {
        VatStatus vatStatus = Optional.ofNullable(reservationVatStatus).orElse(eventVatStatus);
        return new TicketPriceContainer(t, discount, vat, vatStatus);
    }

    @Override
    public Integer getFinalPriceCts() {
        return ticket.getFinalPriceCts();
    }

    private interface OverridePriceContainer {
        int getVatCts();
        int getFinalPriceCts();
    }
}
