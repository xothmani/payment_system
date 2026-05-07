package com.payment.subscription.service;

import com.payment.subscription.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final RedisTemplate<String, String> redisTemplate;

    public void checkSubscriptionRateLimit(UUID userId) {
        String key = "rate:limit:subscription:" + userId;
        Long count = redisTemplate.opsForValue().increment(key, 1);
        if (count == null) {
            return;
        }
        if (count == 1) {
            redisTemplate.expire(key, Duration.ofHours(1));
        }
        if (count > 3) {
            throw new RateLimitExceededException(
                    "Too many subscription attempts. Try again in 1 hour.");
        }
        log.info("Rate limit check passed for user {}: attempt {}/3", userId, count);
    }
}
