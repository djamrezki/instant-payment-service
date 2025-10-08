// com.instantpay.domain.port.out.OutboxRepositoryPort
package com.instantpay.domain.port.out;

import com.instantpay.domain.model.OutboxEvent;

public interface OutboxRepositoryPort {
    OutboxEvent save(OutboxEvent event);
}
