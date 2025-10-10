package com.instantpay.domain.error;

public class PaymentRejectedException extends DomainException {
    public PaymentRejectedException(String msg) { super(msg); }
}
