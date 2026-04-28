package com.payment.authnet.service;

import com.payment.authnet.model.WebhookEvent;
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
            // TODO: handle payment.settled, subscription.renewed, charge.failed, refund.processed
            log.info("Processing Authorize.net webhook: {}", event.getEventType());
        } catch (Exception e) {
            log.error("Authorize.net webhook processing failed for event {}: {}", event.getEventType(), e.getMessage(), e);
        }
    }
}
