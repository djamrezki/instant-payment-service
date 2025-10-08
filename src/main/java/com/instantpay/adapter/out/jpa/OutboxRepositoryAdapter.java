package com.instantpay.adapter.out.jpa;

import com.instantpay.domain.model.OutboxEvent;
import com.instantpay.domain.port.out.OutboxRepositoryPort;
import org.springframework.stereotype.Component;

@Component
public class OutboxRepositoryAdapter implements OutboxRepositoryPort {

    private final SpringDataOutboxRepository repo;

    public OutboxRepositoryAdapter(SpringDataOutboxRepository repo) {
        this.repo = repo;
    }

    @Override
    public OutboxEvent save(OutboxEvent e) {
        var entity = new OutboxEventEntity();
        entity.setId(e.id());
        entity.setAggregateId(e.aggregateId());
        entity.setEventType(e.eventType());
        entity.setPayload(e.payload()); // JSON string
        entity.setCreatedAt(e.createdAt());
        entity.setProcessedAt(e.processedAt());
        repo.save(entity);
        return e;
    }
}
