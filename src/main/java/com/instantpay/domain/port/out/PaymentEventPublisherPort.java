package com.instantpay.domain.port.out;

import com.instantpay.domain.model.Payment;

public interface PaymentEventPublisherPort {
    void publishPaymentCreated(Payment payment);
    void publishPaymentCompleted(Payment payment);
    void publishPaymentFailed(Payment payment, String reason);
}
