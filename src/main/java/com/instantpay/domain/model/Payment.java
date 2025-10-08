package com.instantpay.domain.model;

import com.instantpay.domain.port.in.SendPaymentUseCase;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public record Payment(
        UUID id,
        String idempotencyKey,
        String debtorIban,
        String creditorIban,
        String currency,
        BigDecimal amount,
        String remittanceInfo,
        PaymentStatus status,
        Instant createdAt,
        Instant completedAt,
        String failureReason
) {
    public static Payment newCreated(SendPaymentUseCase.SendPaymentCommand cmd, Clock clock) {
        return new Payment(
                UUID.randomUUID(),
                cmd.idempotencyKey(),
                cmd.debtorIban(),
                cmd.creditorIban(),
                cmd.currency(),
                cmd.amount(),
                cmd.remittanceInfo(),
                PaymentStatus.CREATED,
                Instant.now(clock),
                null,
                null
        );
    }

    public Payment completed(Clock clock) {
        return new Payment(
                id, idempotencyKey, debtorIban, creditorIban, currency,
                amount, remittanceInfo, PaymentStatus.COMPLETED,
                createdAt, Instant.now(clock), null
        );
    }

    public Payment failed(String reason, Clock clock) {
        return new Payment(
                id, idempotencyKey, debtorIban, creditorIban, currency,
                amount, remittanceInfo, PaymentStatus.FAILED,
                createdAt, Instant.now(clock), reason
        );
    }
}
