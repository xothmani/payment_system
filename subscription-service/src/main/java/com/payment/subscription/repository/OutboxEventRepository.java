package com.payment.subscription.repository;

import com.payment.subscription.model.OutboxEvent;
import com.payment.subscription.model.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status);
    List<OutboxEvent> findByStatusAndRetryCountLessThan(OutboxStatus status, int maxRetries);
}
