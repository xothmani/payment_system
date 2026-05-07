package com.payment.subscription.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "dead_letter_queue")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeadLetterEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID outboxEventId;

    @Column(nullable = false)
    private String aggregateType;

    @Column(nullable = false)
    private UUID aggregateId;

    @Column(nullable = false)
    private String eventType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private int retryCount;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime failedAt;

    private LocalDateTime resolvedAt;

    @Builder.Default
    private boolean resolved = false;
}
