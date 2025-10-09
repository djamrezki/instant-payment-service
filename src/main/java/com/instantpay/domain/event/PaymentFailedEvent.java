package com.instantpay.domain.event;

import org.springframework.modulith.events.Externalized;

import java.time.Instant;
import java.util.UUID;

@Externalized
public record PaymentFailedEvent(
        UUID paymentId,
        String debtorIban,
        String creditorIban,
        String currency,
        String remittanceInfo,
        String reason,
        Instant failedAt
) {}
