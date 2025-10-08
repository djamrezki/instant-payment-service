package com.instantpay.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public record Account(
        UUID id,
        String iban,
        BigDecimal balance,
        long version
) {
    public Account debit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        if (balance.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient balance");
        }
        return new Account(id, iban, balance.subtract(amount), version);
    }

    public Account credit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        return new Account(id, iban, balance.add(amount), version);
    }
}
