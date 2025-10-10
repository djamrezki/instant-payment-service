package com.instantpay.domain.error;

public class InsufficientFundsException extends DomainException {
    public InsufficientFundsException(String msg) { super(msg); }
}