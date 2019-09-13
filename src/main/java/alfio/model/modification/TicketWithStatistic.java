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
package alfio.model.modification;

import alfio.model.PriceContainer;
import alfio.model.Ticket;
import alfio.model.TicketReservation;
import alfio.model.transaction.Transaction;
import alfio.util.MonetaryUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.experimental.Delegate;
import org.apache.commons.lang3.builder.CompareToBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.function.Function;

@Getter
public class TicketWithStatistic implements Comparable<TicketWithStatistic>, PriceContainer {
    @Delegate
    @JsonIgnore
    private final Ticket ticket;
    private final TicketReservation ticketReservation;
    @JsonIgnore
    private final ZoneId zoneId;
    @JsonIgnore
    private final Optional<Transaction> tx;
    private final String promoCodeOrToken;
    @JsonIgnore
    private final Function<ZonedDateTime, LocalDateTime> dateMapper;

    public TicketWithStatistic(Ticket ticket,
                               TicketReservation ticketReservation,
                               ZoneId zoneId,
                               Optional<Transaction> tx,
                               String promoCodeOrToken) {
        this.ticket = ticket;
        this.ticketReservation = ticketReservation;
        this.zoneId = zoneId;
        this.tx = tx;
        this.dateMapper = d -> d.withZoneSameInstant(this.zoneId).toLocalDateTime();
        this.promoCodeOrToken = promoCodeOrToken;
    }

    public boolean isStuck() {
        return ticketReservation.isStuck();
    }

    public boolean isPaid() {
        return tx.isPresent();
    }

    public boolean isPending() {
        return getStatus() == Ticket.TicketStatus.PENDING;
    }

    public Transaction getTransaction() {
        return tx.orElse(null);
    }

    public LocalDateTime getTransactionTimestamp() {
        return tx.map(Transaction::getTimestamp).map(dateMapper).orElse(null);
    }

    public LocalDateTime getTimestamp() {
        return Optional.ofNullable(ticketReservation.getConfirmationTimestamp()).map(dateMapper).orElse(null);
    }

    @Override
    public int compareTo(TicketWithStatistic o) {
        return new CompareToBuilder().append(getTimestamp(), o.getTimestamp()).toComparison();
    }

    @Override
    @JsonIgnore
    public Optional<BigDecimal> getOptionalVatPercentage() {
        return Optional.ofNullable(ticketReservation.getUsedVatPercent());
    }

    @Override
    public BigDecimal getAppliedDiscount() {
        return MonetaryUtil.centsToUnit(ticket.getDiscountCts(), ticket.getCurrencyCode());
    }

    @Override
    public BigDecimal getFinalPrice() {
        return MonetaryUtil.centsToUnit(ticket.getFinalPriceCts(), ticket.getCurrencyCode());
    }

    @Override
    public BigDecimal getVAT() {
        return MonetaryUtil.centsToUnit(ticket.getVatCts(), ticket.getCurrencyCode());
    }

    @Override
    public VatStatus getVatStatus() {
        return ticketReservation.getVatStatus();
    }
}
