package io.bagarino.model;

import io.bagarino.model.transaction.Transaction;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class Ticket {

    public enum TicketStatus {
        PENDING, ACQUIRED, CANCELLED, CHECKED_IN, EXPIRED
    }

    private final Instant creation;
    private final TicketCategory category;
    private final Event event;//TODO should it be directly linked with the section?
    private final TicketStatus status;
    private final BigDecimal originalPrice;
    private final BigDecimal paidPrice;
    private final Transaction transaction;
}
