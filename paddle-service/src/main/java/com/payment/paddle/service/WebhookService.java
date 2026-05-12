package com.payment.paddle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.paddle.model.dto.PaddleWebhookEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
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
                    if (event.getData() == null) break;
                    String subId = extractField(event, "id");            // sub_xxx
                    String userId = extractCustomDataField(event, "userId");
                    if (subId == null) break;

                    // Try finding by sub_xxx — also stores sub_xxx as canonical ID (idempotent)
                    boolean updated = tryUpdateStatus(subId, "ACTIVE", subId);
                    if (!updated && userId != null) {
                        // Fallback: find subscription by userId and wire up the sub_xxx
                        updated = tryActivateByUserId(userId, "ACTIVE", subId);
                    }
                    log.info("Paddle webhook: subscription activated subId={} userId={} updated={}",
                            subId, userId, updated);
                }
                case "subscription.canceled" -> {
                    String paddleSubId = extractField(event, "id");
                    if (paddleSubId != null) {
                        updateSubscriptionStatus(paddleSubId, "CANCELLED");
                        log.info("Paddle webhook: subscription cancelled paddleSubId={}", paddleSubId);
                    }
                }
                case "transaction.payment_failed" -> {
                    String paddleSubId = extractField(event, "id");
                    if (paddleSubId != null) {
                        updateSubscriptionStatus(paddleSubId, "PAST_DUE");
                        log.warn("Paddle webhook: payment failed paddleSubId={}", paddleSubId);
                    }
                }
                case "subscription.past_due" ->
                        log.warn("Paddle webhook: subscription past due. data={}", event.getData());
                case "transaction.completed" -> {
                    if (event.getData() == null) break;
                    String subscriptionId = extractField(event, "subscription_id");
                    String userId = extractCustomDataField(event, "userId");
                    if (subscriptionId != null) {
                        boolean updated = tryUpdateStatus(subscriptionId, "ACTIVE", subscriptionId);
                        if (!updated && userId != null) {
                            updated = tryActivateByUserId(userId, "ACTIVE", subscriptionId);
                        }
                        log.info("Paddle webhook: transaction completed - subscription activated: {} updated={}",
                                subscriptionId, updated);
                    } else {
                        log.info("Paddle webhook: transaction completed (no subscription_id). data={}", event.getData());
                    }
                }
                default ->
                        log.debug("Paddle webhook: unhandled event type={}", eventType);
            }
        } catch (Exception e) {
            log.error("Paddle webhook processing failed: {}", e.getMessage(), e);
        }
    }

    private String extractField(PaddleWebhookEvent event, String fieldName) {
        if (event.getData() == null) return null;
        Object value = event.getData().get(fieldName);
        return value != null ? value.toString() : null;
    }

    private String extractCustomDataField(PaddleWebhookEvent event, String fieldName) {
        if (event.getData() == null) return null;
        Object customDataObj = event.getData().get("custom_data");
        if (!(customDataObj instanceof Map)) return null;
        Object value = ((Map<?, ?>) customDataObj).get(fieldName);
        return value != null ? value.toString() : null;
    }

    private void updateSubscriptionStatus(String paddleId, String status) {
        tryUpdateStatus(paddleId, status, null);
    }

    private boolean tryUpdateStatus(String paddleId, String status, String newPaddleSubscriptionId) {
        try {
            Map<String, String> body = new LinkedHashMap<>();
            body.put("status", status);
            if (newPaddleSubscriptionId != null) {
                body.put("paddleSubscriptionId", newPaddleSubscriptionId);
            }
            subscriptionServiceRestClient.put()
                    .uri("/subscriptions/paddle/{id}/status", paddleId)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                return false;
            }
            log.error("HTTP error updating subscription paddleId={} status={}: {}",
                    paddleId, status, e.getMessage(), e);
            return false;
        } catch (Exception e) {
            log.error("Failed to update subscription status for paddleId={} status={}: {}",
                    paddleId, status, e.getMessage(), e);
            return false;
        }
    }

    private boolean tryActivateByUserId(String userId, String status, String paddleSubscriptionId) {
        try {
            Map<String, String> body = new LinkedHashMap<>();
            body.put("status", status);
            body.put("paddleSubscriptionId", paddleSubscriptionId);
            subscriptionServiceRestClient.put()
                    .uri("/subscriptions/user/{userId}/paddle-activate", userId)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                return false;
            }
            log.error("HTTP error activating by userId={}: {}", userId, e.getMessage(), e);
            return false;
        } catch (Exception e) {
            log.error("Failed to activate subscription by userId={}: {}", userId, e.getMessage(), e);
            return false;
        }
    }
}
