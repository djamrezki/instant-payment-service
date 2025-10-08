package com.instantpay.adapter.out.mapper;

import com.instantpay.adapter.out.jpa.PaymentEntity;
import com.instantpay.domain.model.Payment;
import com.instantpay.domain.model.PaymentStatus;
import org.springframework.stereotype.Component;

@Component
public class PaymentJpaMapper {

    public PaymentEntity toEntity(Payment domain) {
        PaymentEntity e = new PaymentEntity();
        e.setId(domain.id());
        e.setIdempotencyKey(domain.idempotencyKey());
        e.setDebtorIban(domain.debtorIban());
        e.setCreditorIban(domain.creditorIban());
        e.setCurrency(domain.currency());
        e.setAmount(domain.amount());
        e.setRemittanceInfo(domain.remittanceInfo());
        e.setStatus(domain.status().name());
        e.setCreatedAt(domain.createdAt());
        e.setCompletedAt(domain.completedAt());
        e.setFailureReason(domain.failureReason());
        return e;
    }

    public Payment toDomain(PaymentEntity e) {
        return new Payment(
                e.getId(),
                e.getIdempotencyKey(),
                e.getDebtorIban(),
                e.getCreditorIban(),
                e.getCurrency(),
                e.getAmount(),
                e.getRemittanceInfo(),
                Enum.valueOf(PaymentStatus.class, e.getStatus()),
                e.getCreatedAt(),
                e.getCompletedAt(),
                e.getFailureReason()
        );
    }
}
