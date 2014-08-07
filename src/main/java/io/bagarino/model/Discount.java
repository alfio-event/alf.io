package io.bagarino.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class Discount {
    private final BigDecimal amount;
}
