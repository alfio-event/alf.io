/**
 * This file is part of bagarino.
 *
 * bagarino is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bagarino is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.bagarino.model.modification;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.bagarino.model.Ticket;
import io.bagarino.model.TicketReservation;
import io.bagarino.model.transaction.Transaction;
import lombok.Getter;
import lombok.experimental.Delegate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.function.Function;

@Getter
public class TicketWithStatistic {
    @Delegate
    @JsonIgnore
    private final Ticket ticket;
    private final TicketReservation ticketReservation;
    @JsonIgnore
    private final ZoneId zoneId;
    @JsonIgnore
    private final Optional<Transaction> tx;
    private final Function<ZonedDateTime, LocalDateTime> dateMapper;

    public TicketWithStatistic(Ticket ticket,
                               TicketReservation ticketReservation,
                               ZoneId zoneId,
                               Optional<Transaction> tx) {
        this.ticket = ticket;
        this.ticketReservation = ticketReservation;
        this.zoneId = zoneId;
        this.tx = tx;
        this.dateMapper = d -> d.withZoneSameInstant(this.zoneId).toLocalDateTime();
    }

    public boolean isStuck() {
        return ticketReservation.isStuck();
    }

    public boolean isPaid() {
        return tx.isPresent();
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

}
