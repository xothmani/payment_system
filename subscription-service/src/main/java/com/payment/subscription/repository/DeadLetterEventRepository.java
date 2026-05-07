package com.payment.subscription.repository;

import com.payment.subscription.model.DeadLetterEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeadLetterEventRepository extends JpaRepository<DeadLetterEvent, UUID> {
    List<DeadLetterEvent> findByResolvedFalseOrderByFailedAtDesc();
    List<DeadLetterEvent> findByAggregateId(UUID aggregateId);
}
