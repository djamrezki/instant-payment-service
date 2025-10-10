package com.instantpay.domain.error;

public class DuplicatePaymentException extends DomainException {
    public DuplicatePaymentException(String msg) { super(msg); }
}