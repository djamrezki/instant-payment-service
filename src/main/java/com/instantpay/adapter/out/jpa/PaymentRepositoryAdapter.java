// com.instantpay.adapter.out.jpa.PaymentRepositoryAdapter
package com.instantpay.adapter.out.jpa;

import com.instantpay.adapter.out.mapper.PaymentJpaMapper;
import com.instantpay.domain.model.Payment;
import com.instantpay.domain.port.out.PaymentRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class PaymentRepositoryAdapter implements PaymentRepositoryPort {

    private final SpringDataPaymentRepository repo;
    private final PaymentJpaMapper mapper;

    public PaymentRepositoryAdapter(SpringDataPaymentRepository repo, PaymentJpaMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    @Override
    public Payment save(Payment p) {
        repo.save(mapper.toEntity(p));
        return p;
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return repo.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
        return repo.findByIdempotencyKey(idempotencyKey).map(mapper::toDomain);
    }
}
