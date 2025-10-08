package com.instantpay.adapter.out.kafka;

import com.instantpay.domain.model.Payment;
import com.instantpay.domain.port.out.PaymentEventPublisherPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventPublisherAdapter implements PaymentEventPublisherPort {

    private final KafkaTemplate<String, String> template;
    private final String topic;

    public PaymentEventPublisherAdapter(
            KafkaTemplate<String, String> template,
            @Value("${app.kafka.topics.payments:payments.events}") String topic) {
        this.template = template;
        this.topic = topic;
    }

    @Override
    public void publishPaymentCreated(Payment p) {
        template.send(topic, p.id().toString(), "PAYMENT_CREATED");
    }
    @Override
    public void publishPaymentConfirmed(Payment p) {
        template.send(topic, p.id().toString(), "PAYMENT_CONFIRMED");
    }
    @Override
    public void publishPaymentFailed(Payment p) {
        template.send(topic, p.id().toString(), "PAYMENT_FAILED");
    }
}
