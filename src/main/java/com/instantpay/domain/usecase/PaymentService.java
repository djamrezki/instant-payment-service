package com.instantpay.domain.usecase;

import com.instantpay.domain.model.OutboxEvent;
import com.instantpay.domain.model.Payment;
import com.instantpay.domain.model.PaymentStatus;
import com.instantpay.domain.model.Transaction;
import com.instantpay.domain.port.in.SendPaymentUseCase;
import com.instantpay.domain.port.out.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class PaymentService implements SendPaymentUseCase {

    private final PaymentRepositoryPort paymentRepo;
    private final AccountRepositoryPort accountRepo;
    private final TransactionRepositoryPort txRepo;
    private final OutboxRepositoryPort outboxRepo;
    private final PaymentEventPublisherPort publisherPort;
    private final Clock clock;

    public PaymentService(PaymentRepositoryPort paymentRepo,
                          AccountRepositoryPort accountRepo,
                          TransactionRepositoryPort txRepo,
                          OutboxRepositoryPort outboxRepo,
                          PaymentEventPublisherPort publisherPort,
                          Clock clock) {
        this.paymentRepo = paymentRepo;
        this.accountRepo = accountRepo;
        this.txRepo = txRepo;
        this.outboxRepo = outboxRepo;
        this.clock = clock;
        this.publisherPort = publisherPort;
    }

    @Override
    @Transactional
    public Result send(SendPaymentCommand cmd) {
        // 0) Fast validation
        if (cmd.debtorIban().equals(cmd.creditorIban())) {
            // Option A: no payment row at all, just fail
            return new Result(null, PaymentStatus.FAILED, "SELF_TRANSFER_NOT_ALLOWED");
        }
        if (cmd.amount() == null || cmd.amount().signum() <= 0) {
            return new Result(null, PaymentStatus.FAILED, "Amount must be > 0");
        }

        // 1) Idempotency
        var existing = paymentRepo.findByIdempotencyKey(cmd.idempotencyKey());
        if (existing.isPresent()) {
            var p = existing.get();
            return new Result(p.id(), p.status(), "Idempotent replay");
        }

        // 2) Create payment (CREATED)
        var payment = Payment.newCreated(cmd, clock);
        try {
            paymentRepo.save(payment);
        } catch (Exception uniqueMaybe) {
            // If another thread inserted concurrently, load and replay
            var again = paymentRepo.findByIdempotencyKey(cmd.idempotencyKey()).orElseThrow();
            return new Result(again.id(), again.status(), "Idempotent replay");
        }

        // Publish CREATED (externalized after tx commits)
        publisherPort.publishPaymentCreated(payment);

        // 3) Deterministic locking order (avoid deadlocks)
        var debtorIban = cmd.debtorIban();
        var creditorIban = cmd.creditorIban();
        var same = debtorIban.equals(creditorIban);

        var firstIban  = same ? debtorIban : (debtorIban.compareTo(creditorIban) < 0 ? debtorIban : creditorIban);
        var secondIban = same ? debtorIban : (firstIban.equals(debtorIban) ? creditorIban : debtorIban);

        var first  = accountRepo.findByIbanForUpdate(firstIban).orElseThrow();
        var second = same ? first : accountRepo.findByIbanForUpdate(secondIban).orElseThrow();

        // Map back to from/to
        var from = debtorIban.equals(first.iban()) ? first : second;
        var to   = debtorIban.equals(first.iban()) ? second : first;

        // 4) Balance check
        if (from.balance().compareTo(cmd.amount()) < 0) {
            payment = payment.failed("INSUFFICIENT_FUNDS", clock);
            paymentRepo.save(payment);
            publisherPort.publishPaymentFailed(payment, "INSUFFICIENT_FUNDS");
            return new Result(payment.id(), payment.status(), "Insufficient funds");
        }

        // 5) Apply transfer + persist balances + ledger
        var newFrom = from.debit(cmd.amount());
        var newTo   = to.credit(cmd.amount());
        accountRepo.save(newFrom);
        if (!same) accountRepo.save(newTo);

        var now = Instant.now(clock);
        txRepo.append(Transaction.debit(payment.id(), newFrom.id(), cmd.amount(), newFrom.balance(), now));
        txRepo.append(Transaction.credit(payment.id(), newTo.id(),   cmd.amount(), newTo.balance(),   now));

        // 6) Complete payment
        payment = payment.completed(clock);
        paymentRepo.save(payment);

        // 7) Publish domain event via Modulith (instead of writing into your outbox table)
        publisherPort.publishPaymentCompleted(payment);

        return new Result(payment.id(), payment.status(), "Payment completed");
    }
}
