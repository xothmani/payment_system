package com.payment.subscription.service;

import com.payment.subscription.config.RedisKeys;
import com.payment.subscription.model.Subscription;
import com.payment.subscription.model.SubscriptionStatus;
import com.payment.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrialSchedulerService {

    private final SubscriptionRepository subscriptionRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Scheduled(cron = "0 0 8 * * *")
    public void processExpiringTrials() {
        List<Subscription> expiring = subscriptionRepository
                .findByStatusAndTrialEndsAtLessThanEqual(SubscriptionStatus.TRIAL, LocalDateTime.now());

        for (Subscription sub : expiring) {
            String lockKey = RedisKeys.chargeLock(sub.getUserId().toString());
            Boolean locked = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", Duration.ofSeconds(60));
            if (Boolean.TRUE.equals(locked)) {
                try {
                    // TODO: call payment-router via RestClient to charge the user
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
