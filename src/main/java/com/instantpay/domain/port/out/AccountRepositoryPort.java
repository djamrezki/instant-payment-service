package com.instantpay.domain.port.out;

import com.instantpay.domain.model.Account;
import java.util.Optional;

public interface AccountRepositoryPort {
    Optional<Account> findByIbanForUpdate(String iban); // SELECT ... FOR UPDATE
    Account save(Account account);
}
