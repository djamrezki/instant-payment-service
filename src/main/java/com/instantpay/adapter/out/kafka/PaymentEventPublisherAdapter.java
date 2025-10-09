package com.instantpay.adapter.out.kafka;

import com.instantpay.domain.event.PaymentCompletedEvent;
import com.instantpay.domain.event.PaymentCreatedEvent;
import com.instantpay.domain.event.PaymentFailedEvent;
import com.instantpay.domain.model.Payment;
import com.instantpay.domain.port.out.PaymentEventPublisherPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class PaymentEventPublisherAdapter implements PaymentEventPublisherPort {

    private final ApplicationEventPublisher publisher;

    public PaymentEventPublisherAdapter(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publishPaymentCreated(Payment p) {
        publisher.publishEvent(new PaymentCreatedEvent(
                p.id(), p.debtorIban(), p.creditorIban(), p.currency(), Instant.now()
        ));
    }

    @Override
    public void publishPaymentCompleted(Payment p) {
        publisher.publishEvent(new PaymentCompletedEvent(
                p.id(), p.debtorIban(), p.creditorIban(), p.currency(), Instant.now()
        ));
    }

    @Override
    public void publishPaymentFailed(Payment p, String reason) {
        publisher.publishEvent(new PaymentFailedEvent(
                p.id(), p.debtorIban(), p.creditorIban(), p.currency(), reason, Instant.now()
        ));
    }
}
