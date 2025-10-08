package com.instantpay.domain.model;

import java.time.Instant;
import java.util.UUID;

public record OutboxEvent(
        UUID id,
        UUID aggregateId,
        String eventType,
        String payload,
        Instant createdAt,
        Instant processedAt
) {
    public static OutboxEvent paymentCompleted(Payment payment, Instant now) {
        String payload = String.format(
                "{\"paymentId\":\"%s\",\"debtorIban\":\"%s\",\"creditorIban\":\"%s\",\"amount\":%s,\"currency\":\"%s\"}",
                payment.id(),
                payment.debtorIban(),
                payment.creditorIban(),
                payment.amount(),
                payment.currency()
        );
        return new OutboxEvent(
                UUID.randomUUID(),
                payment.id(),
                "PaymentCompleted",
                payload,
                now,
                null
        );
    }
}
