package com.payment.paddle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.paddle.model.dto.PaddleWebhookEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
@Slf4j
public class WebhookService {

    private final ObjectMapper objectMapper;
    private final RestClient subscriptionServiceRestClient;

    public WebhookService(ObjectMapper objectMapper,
                          @Qualifier("subscriptionServiceRestClient") RestClient subscriptionServiceRestClient) {
        this.objectMapper = objectMapper;
        this.subscriptionServiceRestClient = subscriptionServiceRestClient;
    }

    @Async("webhookTaskExecutor")
    public void processAsync(String rawBody) {
        try {
            PaddleWebhookEvent event = objectMapper.readValue(rawBody, PaddleWebhookEvent.class);
            String eventType = event.getEventType();
            switch (eventType) {
                case "subscription.activated" -> {
                    String paddleSubId = extractPaddleSubscriptionId(event);
                    if (paddleSubId != null) {
                        updateSubscriptionStatus(paddleSubId, "ACTIVE");
                        log.info("Paddle webhook: subscription activated paddleSubId={}", paddleSubId);
                    }
                }
                case "subscription.canceled" -> {
                    String paddleSubId = extractPaddleSubscriptionId(event);
                    if (paddleSubId != null) {
                        updateSubscriptionStatus(paddleSubId, "CANCELLED");
                        log.info("Paddle webhook: subscription cancelled paddleSubId={}", paddleSubId);
                    }
                }
                case "transaction.payment_failed" -> {
                    String paddleSubId = extractPaddleSubscriptionId(event);
                    if (paddleSubId != null) {
                        updateSubscriptionStatus(paddleSubId, "PAST_DUE");
                        log.warn("Paddle webhook: payment failed paddleSubId={}", paddleSubId);
                    }
                }
                case "subscription.past_due" ->
                        log.warn("Paddle webhook: subscription past due. data={}", event.getData());
                case "transaction.completed" ->
                        log.info("Paddle webhook: transaction completed. data={}", event.getData());
                default ->
                        log.debug("Paddle webhook: unhandled event type={}", eventType);
            }
        } catch (Exception e) {
            log.error("Paddle webhook processing failed: {}", e.getMessage(), e);
        }
    }

    private String extractPaddleSubscriptionId(PaddleWebhookEvent event) {
        if (event.getData() == null) return null;
        Object id = event.getData().get("id");
        return id != null ? id.toString() : null;
    }

    private void updateSubscriptionStatus(String paddleSubId, String status) {
        try {
            subscriptionServiceRestClient.put()
                    .uri("/subscriptions/paddle/{paddleSubscriptionId}/status", paddleSubId)
                    .body(Map.of("status", status))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Failed to update subscription status for paddleSubId={} status={}: {}",
                    paddleSubId, status, e.getMessage(), e);
        }
    }
}
