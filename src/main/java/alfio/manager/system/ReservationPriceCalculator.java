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
package alfio.manager.system;

import alfio.model.*;
import alfio.util.MonetaryUtil;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.apache.commons.lang3.ObjectUtils.firstNonNull;

@RequiredArgsConstructor
public class ReservationPriceCalculator implements PriceContainer {
    final TicketReservation reservation;
    final TotalPrice totalPrice;
    final List<Ticket> tickets;
    final Event event;

    @Override
    public int getSrcPriceCts() {
        return tickets.stream().mapToInt(Ticket::getSrcPriceCts).sum();
    }

    @Override
    public BigDecimal getAppliedDiscount() {
        return MonetaryUtil.centsToUnit(Math.abs(totalPrice.getDiscount()));
    }

    @Override
    public String getCurrencyCode() {
        return event.getCurrency();
    }

    @Override
    public Optional<BigDecimal> getOptionalVatPercentage() {
        return Optional.ofNullable(firstNonNull(reservation.getUsedVatPercent(), event.getVat()));
    }

    @Override
    public VatStatus getVatStatus() {
        return firstNonNull(reservation.getVatStatus(), event.getVatStatus());
    }
}
