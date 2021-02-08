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
package alfio.manager.support;

import alfio.model.PriceContainer;

import java.math.BigDecimal;
import java.util.Optional;


public class RefundPriceContainer implements PriceContainer {

    private final int priceCts;
    private final String currencyCode;
    private final BigDecimal vatPercentage;
    private final VatStatus vatStatus;

    public RefundPriceContainer(int priceCts,
                                String currencyCode,
                                VatStatus reservationVatStatus,
                                BigDecimal vatPercentage) {
        this.priceCts = priceCts;
        this.currencyCode = currencyCode;
        this.vatPercentage = vatPercentage;
        this.vatStatus = determineRefundVatStatus(reservationVatStatus);
    }

    @Override
    public int getSrcPriceCts() {
        return priceCts;
    }

    @Override
    public String getCurrencyCode() {
        return currencyCode;
    }

    @Override
    public Optional<BigDecimal> getOptionalVatPercentage() {
        return Optional.ofNullable(vatPercentage);
    }

    @Override
    public VatStatus getVatStatus() {
        return vatStatus;
    }

    private static VatStatus determineRefundVatStatus(VatStatus reservationVatStatus) {
        switch (reservationVatStatus) {
            case NOT_INCLUDED_NOT_CHARGED:
            case INCLUDED_NOT_CHARGED:
                return VatStatus.NOT_INCLUDED_NOT_CHARGED;
            case NOT_INCLUDED_EXEMPT:
            case INCLUDED_EXEMPT:
                return VatStatus.NOT_INCLUDED_EXEMPT;
            case INCLUDED:
            case NOT_INCLUDED:
                return VatStatus.INCLUDED;
            case NONE:
                return VatStatus.NONE;
            default:
                throw new IllegalStateException("Vat status "+reservationVatStatus+ " not mapped");
        }
    }
}
