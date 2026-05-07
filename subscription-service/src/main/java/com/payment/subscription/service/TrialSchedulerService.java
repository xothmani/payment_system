package com.payment.subscription.service;

import com.payment.subscription.config.RedisKeys;
import com.payment.subscription.model.BillingInterval;
import com.payment.subscription.model.Subscription;
import com.payment.subscription.model.SubscriptionStatus;
import com.payment.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrialSchedulerService {

    private final SubscriptionRepository subscriptionRepository;
    private final OutboxService outboxService;
    private final RedisTemplate<String, String> redisTemplate;

    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void processExpiringTrials() {
        List<Subscription> expiring = subscriptionRepository
                .findByStatusAndTrialEndsAtLessThanEqual(SubscriptionStatus.TRIAL, LocalDateTime.now());

        for (Subscription sub : expiring) {
            String lockKey = RedisKeys.chargeLock(sub.getUserId().toString());
            Boolean locked = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", Duration.ofSeconds(60));
            if (Boolean.TRUE.equals(locked)) {
                try {
                    if (!"US".equalsIgnoreCase(sub.getCountry())) {
                        log.info("Skipping Paddle subscription {} — billing handled by Paddle webhooks", sub.getId());
                        redisTemplate.delete(lockKey);
                        continue;
                    }

                    BigDecimal amount = sub.getBillingInterval() == BillingInterval.ANNUAL
                            ? sub.getPlan().getPriceAnnual()
                            : sub.getPlan().getPriceMonthly();

                    Map<String, Object> routePayload = Map.of(
                            "userId", sub.getUserId().toString(),
                            "idempotencyKey", UUID.randomUUID().toString(),
                            "country", sub.getCountry(),
                            "amount", amount != null ? amount : BigDecimal.ZERO,
                            "currency", "USD",
                            "customerProfileId", sub.getCustomerProfileId() != null
                                    ? sub.getCustomerProfileId() : "",
                            "paymentProfileId", sub.getPaymentProfileId() != null
                                    ? sub.getPaymentProfileId() : "",
                            "paddleSubscriptionId", sub.getPaddleSubscriptionId() != null
                                    ? sub.getPaddleSubscriptionId() : ""
                    );

                    outboxService.saveEvent("SUBSCRIPTION", sub.getId(),
                            "TRIAL_CHARGE", routePayload);

                    log.info("Queued TRIAL_CHARGE outbox event for userId={}", sub.getUserId());
                } catch (Exception e) {
                    log.error("Failed to process trial expiry for userId={}: {}", sub.getUserId(), e.getMessage(), e);
                } finally {
                    redisTemplate.delete(lockKey);
                }
            } else {
                log.debug("Lock not acquired for userId={} — another instance is processing", sub.getUserId());
            }
        }
    }
}
