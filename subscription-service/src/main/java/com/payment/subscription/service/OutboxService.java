package com.payment.subscription.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.subscription.model.BillingInterval;
import com.payment.subscription.model.DeadLetterEvent;
import com.payment.subscription.model.OutboxEvent;
import com.payment.subscription.model.OutboxStatus;
import com.payment.subscription.model.SubscriptionStatus;
import com.payment.subscription.model.dto.CancelProfileRequest;
import com.payment.subscription.repository.DeadLetterEventRepository;
import com.payment.subscription.repository.OutboxEventRepository;
import com.payment.subscription.repository.SubscriptionRepository;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final DeadLetterEventRepository deadLetterEventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper;
    private final RestClient paymentRouterRestClient;
    private final RestClient paddleServiceRestClient;

    public OutboxService(
            OutboxEventRepository outboxEventRepository,
            DeadLetterEventRepository deadLetterEventRepository,
            SubscriptionRepository subscriptionRepository,
            ObjectMapper objectMapper,
            @Qualifier("paymentRouterRestClient") RestClient paymentRouterRestClient,
            @Qualifier("paddleServiceRestClient") RestClient paddleServiceRestClient) {
        this.outboxEventRepository = outboxEventRepository;
        this.deadLetterEventRepository = deadLetterEventRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.objectMapper = objectMapper;
        this.paymentRouterRestClient = paymentRouterRestClient;
        this.paddleServiceRestClient = paddleServiceRestClient;
    }

    @Transactional
    public void saveEvent(String aggregateType, UUID aggregateId, String eventType, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            outboxEventRepository.save(OutboxEvent.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(json)
                    .status(OutboxStatus.PENDING)
                    .retryCount(0)
                    .build());
        } catch (Exception e) {
            log.error("Failed to serialize outbox payload for aggregateId={} eventType={}: {}",
                    aggregateId, eventType, e.getMessage(), e);
            throw new RuntimeException("Failed to save outbox event", e);
        }
    }

    /**
     * Synchronously creates a gateway profile (US) or Paddle checkout (non-US).
     * Called directly from SubscriptionService so the checkoutUrl can be returned
     * in the HTTP response before the outbox poller runs.
     *
     * @return checkoutUrl for non-US users, null for US users
     */
    @Transactional
    public String createCheckoutSync(UUID subscriptionId, Map<String, Object> payload) {
        String country = (String) payload.get("country");
        boolean isUs = "US".equalsIgnoreCase(country);

        if (isUs) {
            record ProfileResponse(String customerProfileId, String paymentProfileId,
                                   String paddleSubscriptionId) {}

            ProfileResponse profileRes = paymentRouterRestClient.post()
                    .uri("/profiles")
                    .body(payload)
                    .retrieve()
                    .body(ProfileResponse.class);

            if (profileRes != null) {
                subscriptionRepository.findById(subscriptionId).ifPresent(sub -> {
                    sub.setCustomerProfileId(profileRes.customerProfileId());
                    sub.setPaymentProfileId(profileRes.paymentProfileId());
                    sub.setStatus(SubscriptionStatus.TRIAL);
                    subscriptionRepository.save(sub);
                });
            }

            saveEvent("SUBSCRIPTION", subscriptionId, "PROFILE_ACTIVATED",
                    Map.of("subscriptionId", subscriptionId.toString()));

            log.info("[correlationId={}] createCheckoutSync(US): profile created for subscriptionId={}",
                    MDC.get("correlationId"), subscriptionId);
            return null;

        } else {
            record CheckoutResponse(String checkoutUrl, String paddleTransactionId) {}

            String priceId = (String) payload.get("priceId");
            String userId = (String) payload.get("userId");
            String email = (String) payload.get("email");

            CheckoutResponse checkoutRes = paddleServiceRestClient.post()
                    .uri("/checkout")
                    .body(Map.of("priceId", priceId, "userId", userId, "email", email))
                    .retrieve()
                    .body(CheckoutResponse.class);

            if (checkoutRes == null) {
                throw new RuntimeException("Empty response from paddle-service /checkout for subscriptionId=" + subscriptionId);
            }

            String checkoutUrl = checkoutRes.checkoutUrl();
            String paddleTransactionId = checkoutRes.paddleTransactionId();

            subscriptionRepository.findById(subscriptionId).ifPresent(sub -> {
                sub.setStatus(SubscriptionStatus.PENDING);
                sub.setPaddleSubscriptionId(paddleTransactionId);
                sub.setPaddleCheckoutUrl(checkoutUrl);
                subscriptionRepository.save(sub);
            });

            saveEvent("SUBSCRIPTION", subscriptionId, "PADDLE_CHECKOUT_CREATED",
                    Map.of("subscriptionId", subscriptionId.toString(),
                            "checkoutUrl", checkoutUrl != null ? checkoutUrl : ""));

            log.info("[correlationId={}] createCheckoutSync(non-US): checkout created for subscriptionId={}",
                    MDC.get("correlationId"), subscriptionId);
            return checkoutUrl;
        }
    }

    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEvent> pending = outboxEventRepository
                .findByStatusAndRetryCountLessThan(OutboxStatus.PENDING, 3);

        for (OutboxEvent event : pending) {
            try {
                deliverEvent(event);
                event.setStatus(OutboxStatus.PROCESSED);
                event.setProcessedAt(LocalDateTime.now());
                log.info("Outbox event delivered: id={} type={} aggregate={}",
                        event.getId(), event.getEventType(), event.getAggregateId());
            } catch (Exception e) {
                event.setRetryCount(event.getRetryCount() + 1);
                event.setLastError(e.getMessage());
                log.warn("Outbox delivery failed: id={} type={} retryCount={} error={}",
                        event.getId(), event.getEventType(), event.getRetryCount(), e.getMessage());
                if (event.getRetryCount() >= 3) {
                    moveToDeadLetter(event, e.getMessage());
                    event.setStatus(OutboxStatus.FAILED);
                }
            }
            outboxEventRepository.save(event);
        }
    }

    private void deliverEvent(OutboxEvent event) throws Exception {
        switch (event.getEventType()) {

            case "PROFILE_ACTIVATED" -> {
                // Profile creation is the activation marker for US trial subscriptions.
                // Charge happens at trial end via TRIAL_CHARGE.
                log.info("PROFILE_ACTIVATED: subscription ready aggregate={}", event.getAggregateId());
            }

            case "PADDLE_CHECKOUT_CREATED" -> {
                // Checkout was already delivered synchronously. Log and complete.
                log.info("PADDLE_CHECKOUT_CREATED: processed aggregate={}", event.getAggregateId());
            }

            case "TRIAL_CHARGE" -> {
                record RouteResponse(String transactionId, String gateway, String status, String message) {}

                Map<String, Object> chargePayload = objectMapper.readValue(
                        event.getPayload(), new TypeReference<>() {});

                String customerProfileId = (String) chargePayload.get("customerProfileId");
                String paddleSubscriptionId = (String) chargePayload.get("paddleSubscriptionId");

                if (customerProfileId != null && !customerProfileId.isBlank()) {
                    RouteResponse routeRes = paymentRouterRestClient.post()
                            .uri("/api/route")
                            .body(chargePayload)
                            .retrieve()
                            .body(RouteResponse.class);

                    log.info("[correlationId={}] TRIAL_CHARGE delivered via Authorize.net for aggregate={}",
                            MDC.get("correlationId"), event.getAggregateId());

                    if (routeRes != null && "APPROVED".equalsIgnoreCase(routeRes.status())) {
                        subscriptionRepository.findById(event.getAggregateId()).ifPresent(sub -> {
                            LocalDateTime now = LocalDateTime.now();
                            long periodDays = sub.getBillingInterval() == BillingInterval.ANNUAL ? 365 : 30;
                            sub.setStatus(SubscriptionStatus.ACTIVE);
                            sub.setCurrentPeriodStart(now);
                            sub.setCurrentPeriodEnd(now.plus(periodDays, ChronoUnit.DAYS));
                            subscriptionRepository.save(sub);
                            log.info("Subscription {} activated after successful TRIAL_CHARGE", sub.getId());
                        });
                    } else {
                        log.warn("TRIAL_CHARGE not approved for aggregate={}, status={}",
                                event.getAggregateId(), routeRes != null ? routeRes.status() : "null");
                    }
                } else if (paddleSubscriptionId != null && !paddleSubscriptionId.isBlank()) {
                    log.info("TRIAL_CHARGE skipped for aggregate={} — Paddle handles recurring billing",
                            event.getAggregateId());
                } else {
                    log.warn("TRIAL_CHARGE: no profile IDs found for aggregate={}, skipping",
                            event.getAggregateId());
                }
            }

            case "SUBSCRIPTION_CANCELLED" -> {
                CancelProfileRequest req = objectMapper.readValue(
                        event.getPayload(), CancelProfileRequest.class);
                paymentRouterRestClient.delete()
                        .uri("/profiles/{id}?country={country}", req.profileId(), req.country())
                        .retrieve()
                        .toBodilessEntity();
                log.info("[correlationId={}] SUBSCRIPTION_CANCELLED delivered for aggregate={}",
                        MDC.get("correlationId"), event.getAggregateId());
            }

            default -> log.warn("Unknown outbox event type: {}", event.getEventType());
        }
    }

    private void moveToDeadLetter(OutboxEvent event, String errorMessage) {
        deadLetterEventRepository.save(DeadLetterEvent.builder()
                .outboxEventId(event.getId())
                .aggregateType(event.getAggregateType())
                .aggregateId(event.getAggregateId())
                .eventType(event.getEventType())
                .payload(event.getPayload())
                .errorMessage(errorMessage)
                .retryCount(event.getRetryCount())
                .build());
        log.error("Event moved to dead letter queue: eventId={}, aggregate={}, error={}",
                event.getId(), event.getAggregateId(), errorMessage);

        if ("TRIAL_CHARGE".equals(event.getEventType())) {
            subscriptionRepository.findById(event.getAggregateId()).ifPresent(sub -> {
                sub.setStatus(SubscriptionStatus.PAST_DUE);
                subscriptionRepository.save(sub);
                log.error("Subscription {} marked PAST_DUE after TRIAL_CHARGE exhausted retries", sub.getId());
            });
        }
    }
}
