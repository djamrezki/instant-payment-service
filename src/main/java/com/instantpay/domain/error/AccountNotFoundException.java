package com.instantpay.domain.error;

public class AccountNotFoundException extends DomainException {
    public AccountNotFoundException(String msg) { super(msg); }
}
