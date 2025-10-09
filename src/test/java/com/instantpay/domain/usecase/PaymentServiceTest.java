package com.instantpay.domain.usecase;

import com.instantpay.domain.model.Account;
import com.instantpay.domain.model.Payment;
import com.instantpay.domain.model.PaymentStatus;
import com.instantpay.domain.model.Transaction;
import com.instantpay.domain.port.in.SendPaymentUseCase;
import com.instantpay.domain.port.out.AccountRepositoryPort;
import com.instantpay.domain.port.out.PaymentEventPublisherPort;
import com.instantpay.domain.port.out.PaymentRepositoryPort;
import com.instantpay.domain.port.out.TransactionRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentRepositoryPort paymentRepo;
    @Mock AccountRepositoryPort accountRepo;
    @Mock TransactionRepositoryPort txRepo;
    @Mock PaymentEventPublisherPort publisherPort;

    private Clock fixed;
    private PaymentService service;

    // Valid Swiss IBANs (no spaces)
    private static final String DEBTOR   = "CH9300762011623852957";
    private static final String CREDITOR = "CH5604835012345678009";

    @BeforeEach
    void setUp() {
        fixed = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
        service = new PaymentService(paymentRepo, accountRepo, txRepo, publisherPort, fixed);
    }

    private SendPaymentUseCase.SendPaymentCommand cmd(String debtor, String creditor, BigDecimal amount) {
        return new SendPaymentUseCase.SendPaymentCommand(
                "idem-1", debtor, creditor, "CHF", amount, "ref", "req-1"
        );
    }

    @Test
    void selfTransfer_notAllowed_fastFail_noSideEffects() {
        var result = service.send(cmd(DEBTOR, DEBTOR, new BigDecimal("10.00")));

        assertThat(result.paymentId()).isNull();
        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.message()).isEqualTo("SELF_TRANSFER_NOT_ALLOWED");

        verifyNoInteractions(paymentRepo, accountRepo, txRepo, publisherPort);
    }

    @Test
    void amountMustBePositive_fastFail_noSideEffects() {
        var result = service.send(cmd(DEBTOR, CREDITOR, new BigDecimal("0.00")));

        assertThat(result.paymentId()).isNull();
        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.message()).contains("Amount must be > 0");

        verifyNoInteractions(paymentRepo, accountRepo, txRepo, publisherPort);
    }

    @Test
    void idempotentReplay_returnsExisting_withoutSideEffects() {
        var existing = new Payment(
                UUID.randomUUID(), "idem-1", DEBTOR, CREDITOR, "CHF",
                new BigDecimal("25.00"), "ref", PaymentStatus.COMPLETED,
                Instant.now(fixed), Instant.now(fixed), null
        );
        when(paymentRepo.findByIdempotencyKey("idem-1")).thenReturn(Optional.of(existing));

        var result = service.send(cmd(DEBTOR, CREDITOR, new BigDecimal("25.00")));

        assertThat(result.paymentId()).isEqualTo(existing.id());
        assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(result.message()).isEqualTo("Idempotent replay");

        verify(paymentRepo, times(1)).findByIdempotencyKey("idem-1");
        verifyNoMoreInteractions(paymentRepo);
        verifyNoInteractions(accountRepo, txRepo, publisherPort);
    }

    @Test
    void successFlow_debitsCredits_persistsLedger_andPublishesEvents() {
        // Arrange accounts
        var debtor = new Account(UUID.randomUUID(), DEBTOR, new BigDecimal("100.00"), 0L);
        var creditor = new Account(UUID.randomUUID(), CREDITOR, new BigDecimal("5.00"), 0L);

        when(paymentRepo.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        // Locking lookups (order doesn’t matter; we return both)
        when(accountRepo.findByIbanForUpdate(DEBTOR)).thenReturn(Optional.of(debtor));
        when(accountRepo.findByIbanForUpdate(CREDITOR)).thenReturn(Optional.of(creditor));

        var result = service.send(cmd(DEBTOR, CREDITOR, new BigDecimal("25.00")));

        // Assert result
        assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(result.message()).isEqualTo("Payment completed");
        assertThat(result.paymentId()).isNotNull();

        // Payment saves: CREATED then COMPLETED
        var paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepo, times(2)).save(paymentCaptor.capture());
        var saved = paymentCaptor.getAllValues();
        assertThat(saved.get(0).status()).isEqualTo(PaymentStatus.CREATED);
        assertThat(saved.get(0).createdAt()).isEqualTo(Instant.parse("2025-01-01T00:00:00Z"));
        assertThat(saved.get(1).status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(saved.get(1).completedAt()).isEqualTo(Instant.parse("2025-01-01T00:00:00Z"));

        // Accounts saved with new balances: debtor 75, creditor 30
        var accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepo, times(2)).save(accountCaptor.capture());
        var accountsSaved = accountCaptor.getAllValues();
        assertThat(accountsSaved)
                .anySatisfy(a -> {
                    if (a.iban().equals(DEBTOR)) assertThat(a.balance()).isEqualByComparingTo("75.00");
                })
                .anySatisfy(a -> {
                    if (a.iban().equals(CREDITOR)) assertThat(a.balance()).isEqualByComparingTo("30.00");
                });

        // Ledger: one debit (-25) and one credit (+25) with correct balancesAfter
        var txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(txRepo, times(2)).append(txCaptor.capture());
        var txs = txCaptor.getAllValues();
        assertThat(txs).hasSize(2);
        assertThat(txs)
                .anySatisfy(t -> {
                    if (t.accountId().equals(debtor.id())) {
                        assertThat(t.amount()).isEqualByComparingTo("-25.00");
                        assertThat(t.balanceAfter()).isEqualByComparingTo("75.00");
                    }
                })
                .anySatisfy(t -> {
                    if (t.accountId().equals(creditor.id())) {
                        assertThat(t.amount()).isEqualByComparingTo("25.00");
                        assertThat(t.balanceAfter()).isEqualByComparingTo("30.00");
                    }
                });

        // Events: CREATED then COMPLETED; no failure event
        verify(publisherPort, times(1)).publishPaymentCreated(any(Payment.class));
        verify(publisherPort, times(1)).publishPaymentCompleted(any(Payment.class));
        verify(publisherPort, never()).publishPaymentFailed(any(Payment.class), anyString());

        // Idempotency checked exactly once
        verify(paymentRepo, times(1)).findByIdempotencyKey("idem-1");
    }

    @Test
    void insufficientFunds_marksFailed_savesPaymentAndPublishesFailure_noLedgerNoBalanceChanges() {
        var debtor = new Account(UUID.randomUUID(), DEBTOR, new BigDecimal("10.00"), 0L);
        var creditor = new Account(UUID.randomUUID(), CREDITOR, new BigDecimal("0.00"), 0L);

        when(paymentRepo.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        when(accountRepo.findByIbanForUpdate(DEBTOR)).thenReturn(Optional.of(debtor));
        when(accountRepo.findByIbanForUpdate(CREDITOR)).thenReturn(Optional.of(creditor));

        var result = service.send(cmd(DEBTOR, CREDITOR, new BigDecimal("25.00")));

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.message()).contains("Insufficient funds");
        assertThat(result.paymentId()).isNotNull();

        var paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepo, times(2)).save(paymentCaptor.capture()); // CREATED then FAILED
        assertThat(paymentCaptor.getAllValues().get(0).status()).isEqualTo(PaymentStatus.CREATED);
        assertThat(paymentCaptor.getAllValues().get(1).status()).isEqualTo(PaymentStatus.FAILED);

        verify(publisherPort).publishPaymentCreated(any(Payment.class));
        verify(publisherPort).publishPaymentFailed(any(Payment.class), eq("INSUFFICIENT_FUNDS"));
        verify(publisherPort, never()).publishPaymentCompleted(any());

        verify(accountRepo, never()).save(any(Account.class)); // no balance changes
        verify(txRepo, never()).append(any(Transaction.class)); // no ledger entries
    }

    @Test
    void idempotencyRace_firstSaveThrows_uniqueConstraint_loadsExisting_andDoesNotPublishCreated() {
        // First lookup: empty (no existing payment yet)
        // Second lookup (after save throws): returns existing payment
        var existing = new Payment(
                UUID.randomUUID(), "idem-1", DEBTOR, CREDITOR, "CHF",
                new BigDecimal("25.00"), "ref", PaymentStatus.COMPLETED,
                Instant.now(fixed), Instant.now(fixed), null
        );

        // 👇 single stubbing with sequential returns (no unnecessary stubs)
        when(paymentRepo.findByIdempotencyKey("idem-1"))
                .thenReturn(Optional.empty(), Optional.of(existing));

        // Simulate unique constraint on first save of CREATED payment
        doThrow(new RuntimeException("unique_violation"))
                .when(paymentRepo).save(argThat(p -> p.status() == PaymentStatus.CREATED));

        var result = service.send(cmd(DEBTOR, CREDITOR, new BigDecimal("25.00")));

        assertThat(result.paymentId()).isEqualTo(existing.id());
        assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(result.message()).isEqualTo("Idempotent replay");

        // CREATED event must NOT be published in this race path
        verify(publisherPort, never()).publishPaymentCreated(any(Payment.class));
        verifyNoInteractions(accountRepo, txRepo);

        // sanity: idempotency key looked up twice; CREATED save attempted once
        verify(paymentRepo, times(2)).findByIdempotencyKey("idem-1");
        verify(paymentRepo, times(1)).save(argThat(p -> p.status() == PaymentStatus.CREATED));

        // no other unexpected interactions
        verifyNoMoreInteractions(paymentRepo, publisherPort);
    }

}
