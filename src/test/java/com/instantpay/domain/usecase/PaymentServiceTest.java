package com.instantpay.domain.usecase;

import com.instantpay.domain.error.*;
import com.instantpay.domain.model.Account;
import com.instantpay.domain.model.Payment;
import com.instantpay.domain.model.PaymentStatus;
import com.instantpay.domain.port.in.SendPaymentUseCase.SendPaymentCommand;
import com.instantpay.domain.port.out.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentServiceTest {

    PaymentRepositoryPort paymentRepo;
    AccountRepositoryPort accountRepo;
    TransactionRepositoryPort txRepo;
    PaymentEventPublisherPort publisherPort;
    Clock clock;

    PaymentService service;

    @BeforeEach
    void setUp() {
        paymentRepo = mock(PaymentRepositoryPort.class);
        accountRepo = mock(AccountRepositoryPort.class);
        txRepo = mock(TransactionRepositoryPort.class);
        publisherPort = mock(PaymentEventPublisherPort.class);
        clock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
        service = new PaymentService(paymentRepo, accountRepo, txRepo, publisherPort, clock);
    }

    private SendPaymentCommand cmd(BigDecimal amount) {
        return new SendPaymentCommand(
                "idem-1",
                "CH93-0000-0000-0000-0000-0",
                "CH44-1111-1111-1111-1111-1",
                "CHF",
                amount,
                "Remittance info",
                "note"
        );
    }

    @Test
    void selfTransfer_isRejected() {
        var c = new SendPaymentCommand("k","CH93-0000-0000-0000-0000-0","CH93-0000-0000-0000-0000-0","CH1",BigDecimal.TEN,"Remittance info",null);
        assertThatThrownBy(() -> service.send(c))
                .isInstanceOf(PaymentRejectedException.class)
                .hasMessageContaining("Self transfer");
        verifyNoInteractions(paymentRepo, accountRepo, txRepo, publisherPort);
    }

    @Test
    void nonPositiveAmount_isRejected() {
        assertThatThrownBy(() -> service.send(cmd(BigDecimal.ZERO)))
                .isInstanceOf(PaymentRejectedException.class)
                .hasMessageContaining("greater than zero");
        verifyNoInteractions(paymentRepo, accountRepo, txRepo, publisherPort);
    }

    @Test
    void idempotencyKey_returnsExistingResult() {
        var existing = Payment.newCreated(cmd(BigDecimal.TEN), clock).completed(clock);
        when(paymentRepo.findByIdempotencyKey("idem-1")).thenReturn(Optional.of(existing));

        var res = service.send(cmd(BigDecimal.TEN));

        assertThat(res.paymentId()).isEqualTo(existing.id());
        assertThat(res.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(res.message()).contains("Idempotent replay");
        verifyNoMoreInteractions(accountRepo, txRepo, publisherPort);
    }

    @Test
    void missingAccounts_throwAccountNotFound() {
        when(paymentRepo.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        when(accountRepo.findByIbanForUpdate("CH44-1111-1111-1111-1111-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.send(cmd(BigDecimal.TEN)))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("CH44-1111-1111-1111-1111-1");
        verify(paymentRepo, never()).save(any());
        verifyNoInteractions(txRepo, publisherPort);
    }

    @Test
    void insufficientFunds_throwException_andNoPaymentRow() {
        when(paymentRepo.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());

        var debtor = new Account(UUID.randomUUID(), "CH93-0000-0000-0000-0000-0", new BigDecimal("5.00"), 1);
        var creditor = new Account(UUID.randomUUID(), "CH44-1111-1111-1111-1111-1", new BigDecimal("0.00"), 1);

        when(accountRepo.findByIbanForUpdate("CH44-1111-1111-1111-1111-1")).thenReturn(Optional.of(creditor));
        when(accountRepo.findByIbanForUpdate("CH93-0000-0000-0000-0000-0")).thenReturn(Optional.of(debtor));

        assertThatThrownBy(() -> service.send(cmd(new BigDecimal("10.00"))))
                .isInstanceOf(InsufficientFundsException.class);

        verify(paymentRepo, never()).save(any(Payment.class));
        verifyNoInteractions(txRepo, publisherPort);
    }

    @Test
    void happyPath_debitsCredits_persistsAndPublishes() {
        when(paymentRepo.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());

        var debtor = new Account(UUID.randomUUID(), "CH93-0000-0000-0000-0000-0", new BigDecimal("100.00"),1);
        var creditor = new Account(UUID.randomUUID(), "CH44-1111-1111-1111-1111-1", new BigDecimal("20.00"), 1);

        when(accountRepo.findByIbanForUpdate("CH44-1111-1111-1111-1111-1")).thenReturn(Optional.of(creditor));
        when(accountRepo.findByIbanForUpdate("CH93-0000-0000-0000-0000-0")).thenReturn(Optional.of(debtor));

        // Allow save(payment) to proceed
        doAnswer(inv -> inv.getArgument(0)).when(paymentRepo).save(any(Payment.class));

        var res = service.send(cmd(new BigDecimal("30.00")));

        assertThat(res.status()).isEqualTo(PaymentStatus.COMPLETED);
        verify(paymentRepo, atLeastOnce()).save(any(Payment.class));
        verify(accountRepo).save(argThat(a -> a.iban().equals(debtor.iban()) && a.balance().compareTo(new BigDecimal("70.00")) == 0));
        verify(accountRepo).save(argThat(a -> a.iban().equals(creditor.iban()) && a.balance().compareTo(new BigDecimal("50.00")) == 0));
        verify(txRepo, times(2)).append(any());
        verify(publisherPort).publishPaymentCreated(any(Payment.class));
        verify(publisherPort).publishPaymentCompleted(any(Payment.class));
    }
}
