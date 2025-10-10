package com.instantpay.domain.error;

public abstract class DomainException extends RuntimeException {
    protected DomainException(String message) { super(message); }
}
