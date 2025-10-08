package com.instantpay.adapter.out.jpa;

import com.instantpay.adapter.out.mapper.TransactionJpaMapper;
import com.instantpay.domain.model.Transaction;
import com.instantpay.domain.port.out.TransactionRepositoryPort;
import org.springframework.stereotype.Component;

@Component
public class TransactionRepositoryAdapter implements TransactionRepositoryPort {

    private final SpringDataTransactionRepository repo;
    private final SpringDataAccountRepository accountRepo;
    private final TransactionJpaMapper mapper;

    public TransactionRepositoryAdapter(SpringDataTransactionRepository repo,
                                        SpringDataAccountRepository accountRepo,
                                        TransactionJpaMapper mapper) {
        this.repo = repo;
        this.accountRepo = accountRepo;
        this.mapper = mapper;
    }

    @Override
    public Transaction append(Transaction tx) {
        var accountEntity = accountRepo.findById(tx.accountId())
                .orElseThrow(() -> new IllegalStateException("Account not found: " + tx.accountId()));
        repo.save(mapper.toEntity(tx, accountEntity));
        return tx;
    }
}
