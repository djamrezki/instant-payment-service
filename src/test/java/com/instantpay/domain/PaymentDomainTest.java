package com.instantpay.domain;

import com.instantpay.domain.model.Account;
import com.instantpay.domain.model.Payment;
import com.instantpay.domain.model.PaymentStatus;
import com.instantpay.domain.model.Transaction;
import com.instantpay.domain.port.in.SendPaymentUseCase;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class PaymentDomainTest {

    private final Clock fixed = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void paymentLifecycleTransitions() {
        var cmd = new SendPaymentUseCase.SendPaymentCommand(
                "idem-1", "CH93...", "CH47...", "CHF", new BigDecimal("25.00"), "hello", "req_id_1"
        );

        var created = Payment.newCreated(cmd, fixed);
        assertThat(created.status()).isEqualTo(PaymentStatus.CREATED);
        assertThat(created.createdAt()).isEqualTo(Instant.parse("2025-01-01T00:00:00Z"));
        assertThat(created.completedAt()).isNull();
        assertThat(created.failureReason()).isNull();

        var completed = created.completed(fixed);
        assertThat(completed.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(completed.completedAt()).isEqualTo(Instant.parse("2025-01-01T00:00:00Z"));
        assertThat(completed.failureReason()).isNull();

        var failed = created.failed("insufficient funds", fixed);
        assertThat(failed.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(failed.failureReason()).isEqualTo("insufficient funds");
        assertThat(failed.completedAt()).isEqualTo(Instant.parse("2025-01-01T00:00:00Z"));
    }

    @Test
    void accountDebitCreditAndTransactions() {
        var a = new Account(UUID.randomUUID(), "CH93...", new BigDecimal("100.00"), 0L);
        var afterDebit = a.debit(new BigDecimal("25.00"));
        assertThat(afterDebit.balance()).isEqualByComparingTo("75.00");

        var afterCredit = afterDebit.credit(new BigDecimal("10.00"));
        assertThat(afterCredit.balance()).isEqualByComparingTo("85.00");

        assertThatThrownBy(() -> a.debit(new BigDecimal("100.01")))
                .isInstanceOf(IllegalStateException.class);

        var pid = UUID.randomUUID();
        var txDebit = Transaction.debit(pid, UUID.randomUUID(), new BigDecimal("25.00"), new BigDecimal("75.00"),
                Instant.parse("2025-01-01T00:00:00Z"));
        assertThat(txDebit.amount()).isEqualByComparingTo("-25.00");

        var txCredit = Transaction.credit(pid, UUID.randomUUID(), new BigDecimal("10.00"), new BigDecimal("85.00"),
                Instant.parse("2025-01-01T00:00:00Z"));
        assertThat(txCredit.amount()).isEqualByComparingTo("10.00");
    }
}
