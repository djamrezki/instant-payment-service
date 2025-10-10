package com.instantpay.domain.error;

public class IdempotencyConflictException extends DomainException {
    public IdempotencyConflictException(String msg) { super(msg); }
}