package io.bagarino.model.transaction;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Transaction {
    private final PaymentProxy paymentProxy;
    private final LocalDateTime timestamp;
    private final String sourceIp;
    private final String userID;
}
