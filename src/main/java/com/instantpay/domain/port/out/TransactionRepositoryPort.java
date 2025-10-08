// com.instantpay.domain.port.out.TransactionRepositoryPort
package com.instantpay.domain.port.out;

import com.instantpay.domain.model.Transaction;

public interface TransactionRepositoryPort {
    Transaction append(Transaction tx); // write-only ledger
}
