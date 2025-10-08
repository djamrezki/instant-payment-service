package com.instantpay.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Transaction(
        UUID id,
        UUID paymentId,
        UUID accountId,
        BigDecimal amount,      // positive for credit, negative for debit
        BigDecimal balanceAfter,
        Instant createdAt
) {
    public static Transaction debit(UUID paymentId, UUID accountId, BigDecimal amount, BigDecimal balanceAfter, Instant createdAt) {
        return new Transaction(UUID.randomUUID(), paymentId, accountId, amount.negate(), balanceAfter, createdAt);
    }

    public static Transaction credit(UUID paymentId, UUID accountId, BigDecimal amount, BigDecimal balanceAfter, Instant createdAt) {
        return new Transaction(UUID.randomUUID(), paymentId, accountId, amount, balanceAfter, createdAt);
    }
}
