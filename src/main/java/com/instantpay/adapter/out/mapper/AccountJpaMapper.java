// com.instantpay.adapter.out.mapper.AccountJpaMapper
package com.instantpay.adapter.out.mapper;

import com.instantpay.adapter.out.jpa.AccountEntity;
import com.instantpay.domain.model.Account;
import org.springframework.stereotype.Component;

@Component
public class AccountJpaMapper {
    public AccountEntity toEntity(Account d) {
        var e = new AccountEntity();
        e.setId(d.id());
        e.setIban(d.iban());
        e.setBalance(d.balance());
        e.setVersion(d.version());
        return e;
    }
    public Account toDomain(AccountEntity e) {
        return new Account(e.getId(), e.getIban(), e.getBalance(), e.getVersion());
    }
}
