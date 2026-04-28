package com.payment.paddle.service;

import com.payment.paddle.model.WebhookEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    @Async("webhookTaskExecutor")
    public void processAsync(WebhookEvent event) {
        try {
            // TODO: handle transaction.completed, subscription.activated, subscription.canceled, refund.created
            log.info("Processing Paddle webhook: {}", event.getEventType());
        } catch (Exception e) {
            log.error("Paddle webhook processing failed for event {}: {}", event.getEventType(), e.getMessage(), e);
        }
    }
}
