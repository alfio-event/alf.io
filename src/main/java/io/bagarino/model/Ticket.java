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
package io.bagarino.model;

import io.bagarino.model.transaction.Transaction;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Data
public class Ticket {

    public enum TicketStatus {
        PENDING, ACQUIRED, CANCELLED, CHECKED_IN, EXPIRED
    }

    private final LocalDateTime creation;
    private final TicketCategory category;
    private final Event event;
    private final TicketStatus status;
    private final BigDecimal originalPrice;
    private final BigDecimal paidPrice;
    private final Transaction transaction;
}
