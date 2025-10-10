package com.instantpay.domain.usecase;

import com.instantpay.domain.error.AccountNotFoundException;
import com.instantpay.domain.error.InsufficientFundsException;
import com.instantpay.domain.error.PaymentRejectedException;
import com.instantpay.domain.model.Payment;
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
    private final PaymentEventPublisherPort publisherPort;
    private final Clock clock;

    public PaymentService(PaymentRepositoryPort paymentRepo,
                          AccountRepositoryPort accountRepo,
                          TransactionRepositoryPort txRepo,
                          PaymentEventPublisherPort publisherPort,
                          Clock clock) {
        this.paymentRepo = paymentRepo;
        this.accountRepo = accountRepo;
        this.txRepo = txRepo;
        this.publisherPort = publisherPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result send(SendPaymentCommand cmd) {
        // 0) Fast validation -> throw domain exceptions (handled by GlobalExceptionHandler)
        if (cmd.debtorIban() == null || cmd.creditorIban() == null) {
            throw new PaymentRejectedException("Both debtorIban and creditorIban are required.");
        }
        if (cmd.debtorIban().equals(cmd.creditorIban())) {
            throw new PaymentRejectedException("Self transfer is not allowed.");
        }
        if (cmd.amount() == null || cmd.amount().signum() <= 0) {
            throw new PaymentRejectedException("Amount must be greater than zero.");
        }

        // 1) Idempotency – return the previously computed outcome (success or otherwise) without error
        var existing = paymentRepo.findByIdempotencyKey(cmd.idempotencyKey());
        if (existing.isPresent()) {
            var p = existing.get();
            return new Result(p.id(), p.status(), "Idempotent replay");
        }

        // 2) Lock accounts deterministically and validate their existence
        var debtorIban = cmd.debtorIban();
        var creditorIban = cmd.creditorIban();
        var same = debtorIban.equals(creditorIban);

        var firstIban  = same ? debtorIban : (debtorIban.compareTo(creditorIban) < 0 ? debtorIban : creditorIban);
        var secondIban = same ? debtorIban : (firstIban.equals(debtorIban) ? creditorIban : debtorIban);

        var first = accountRepo.findByIbanForUpdate(firstIban)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + firstIban));
        var second = same ? first : accountRepo.findByIbanForUpdate(secondIban)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + secondIban));

        // Map back to from/to
        var from = debtorIban.equals(first.iban()) ? first : second;
        var to   = debtorIban.equals(first.iban()) ? second : first;

        // 3) Business rule: balance check BEFORE creating/publishing anything
        if (from.balance().compareTo(cmd.amount()) < 0) {
            throw new InsufficientFundsException("Insufficient balance on source account.");
        }

        // 4) Create payment (CREATED) – now that we know it can succeed
        var payment = Payment.newCreated(cmd, clock);
        try {
            paymentRepo.save(payment);
        } catch (Exception uniqueMaybe) {
            // In case another thread inserted concurrently, load and return as idempotent replay
            var again = paymentRepo.findByIdempotencyKey(cmd.idempotencyKey()).orElseThrow();
            return new Result(again.id(), again.status(), "Idempotent replay");
        }

        // Publish CREATED (after we validated the business preconditions)
        publisherPort.publishPaymentCreated(payment);

        // 5) Apply transfer: persist new balances + ledger
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

        // 7) Publish COMPLETED
        publisherPort.publishPaymentCompleted(payment);

        return new Result(payment.id(), payment.status(), "Payment completed");
    }
}
