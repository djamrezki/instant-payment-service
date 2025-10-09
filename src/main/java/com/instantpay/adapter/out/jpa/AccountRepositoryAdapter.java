package com.instantpay.adapter.out.jpa;

import com.instantpay.adapter.out.jpa.mapper.AccountJpaMapper;
import com.instantpay.domain.model.Account;
import com.instantpay.domain.port.out.AccountRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AccountRepositoryAdapter implements AccountRepositoryPort {

    private final SpringDataAccountRepository repo;
    private final AccountJpaMapper mapper;

    public AccountRepositoryAdapter(SpringDataAccountRepository repo, AccountJpaMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    @Override
    public Optional<Account> findByIbanForUpdate(String iban) {
        return repo.findByIbanForUpdate(iban).map(mapper::toDomain);
    }

    @Override
    public Account save(Account account) {
        var saved = repo.save(mapper.toEntity(account));
        return mapper.toDomain(saved);
    }
}
