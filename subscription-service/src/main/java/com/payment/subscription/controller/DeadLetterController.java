package com.payment.subscription.controller;

import com.payment.subscription.model.DeadLetterEvent;
import com.payment.subscription.model.OutboxEvent;
import com.payment.subscription.model.OutboxStatus;
import com.payment.subscription.repository.DeadLetterEventRepository;
import com.payment.subscription.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/admin/dead-letter")
@RequiredArgsConstructor
public class DeadLetterController {

    private final DeadLetterEventRepository deadLetterEventRepository;
    private final OutboxEventRepository outboxEventRepository;

    @Value("${admin.key}")
    private String configuredAdminKey;

    @GetMapping
    public ResponseEntity<List<DeadLetterEvent>> getUnresolved(
            @RequestHeader("X-Admin-Key") String adminKey) {
        if (!configuredAdminKey.equals(adminKey)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(deadLetterEventRepository.findByResolvedFalseOrderByFailedAtDesc());
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<Void> retry(
            @PathVariable UUID id,
            @RequestHeader("X-Admin-Key") String adminKey) {
        if (!configuredAdminKey.equals(adminKey)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<DeadLetterEvent> found = deadLetterEventRepository.findById(id);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        DeadLetterEvent dlq = found.get();
        OutboxEvent restored = OutboxEvent.builder()
                .aggregateType(dlq.getAggregateType())
                .aggregateId(dlq.getAggregateId())
                .eventType(dlq.getEventType())
                .payload(dlq.getPayload())
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .build();
        outboxEventRepository.save(restored);
        dlq.setResolved(true);
        dlq.setResolvedAt(LocalDateTime.now());
        deadLetterEventRepository.save(dlq);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<Void> resolve(
            @PathVariable UUID id,
            @RequestHeader("X-Admin-Key") String adminKey) {
        if (!configuredAdminKey.equals(adminKey)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<DeadLetterEvent> found = deadLetterEventRepository.findById(id);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        DeadLetterEvent dlq = found.get();
        dlq.setResolved(true);
        dlq.setResolvedAt(LocalDateTime.now());
        deadLetterEventRepository.save(dlq);
        return ResponseEntity.ok().build();
    }
}
