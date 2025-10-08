package com.instantpay.domain.port.out;

import com.instantpay.domain.model.Payment;

public interface PaymentEventPublisherPort {
    void publishPaymentCreated(Payment payment);
    void publishPaymentConfirmed(Payment payment);
    void publishPaymentFailed(Payment payment);
}
