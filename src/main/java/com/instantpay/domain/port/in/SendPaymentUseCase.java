package com.instantpay.domain.port.in;

import com.instantpay.domain.model.PaymentStatus;

import java.util.UUID;
import java.math.BigDecimal;

public interface SendPaymentUseCase {
    Result send(SendPaymentCommand command);

    record SendPaymentCommand(
            String idempotencyKey,
            String debtorIban,
            String creditorIban,
            String currency,
            BigDecimal amount,
            String remittanceInfo,
            String requestId // optional, for tracing
    ) {}

    record Result(UUID paymentId, PaymentStatus status, String message) {}
}
