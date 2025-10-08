// com.instantpay.adapter.out.mapper.TransactionJpaMapper
package com.instantpay.adapter.out.mapper;

import com.instantpay.adapter.out.jpa.TransactionEntity;
import com.instantpay.adapter.out.jpa.AccountEntity;
import com.instantpay.domain.model.Transaction;
import org.springframework.stereotype.Component;

@Component
public class TransactionJpaMapper {
    public TransactionEntity toEntity(Transaction d, AccountEntity accountEntity) {
        var e = new TransactionEntity();
        e.setId(d.id());
        e.setPaymentId(d.paymentId());
        e.setAccount(accountEntity);
        e.setAmount(d.amount());
        e.setBalanceAfter(d.balanceAfter());
        e.setCreatedAt(d.createdAt());
        return e;
    }
}
