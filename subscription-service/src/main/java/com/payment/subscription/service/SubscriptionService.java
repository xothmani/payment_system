package com.payment.subscription.service;

import com.payment.subscription.config.RedisKeys;
import com.payment.subscription.exception.*;
import com.payment.subscription.model.*;
import com.payment.subscription.model.dto.*;
import com.payment.subscription.repository.PlanRepository;
import com.payment.subscription.repository.SubscriptionRepository;
import com.payment.subscription.repository.TokenLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final TokenLedgerRepository tokenLedgerRepository;
    private final TokenLedgerService tokenLedgerService;
    private final OutboxService outboxService;
    private final RateLimitService rateLimitService;
    private final RedisTemplate<String, String> redisTemplate;

    @Transactional
    public CreateSubscriptionResponse createSubscription(CreateSubscriptionRequest request) {
        rateLimitService.checkSubscriptionRateLimit(request.userId());

        subscriptionRepository.findByUserId(request.userId())
                .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE
                        || s.getStatus() == SubscriptionStatus.TRIAL)
                .ifPresent(s -> {
                    throw new SubscriptionAlreadyExistsException(request.userId());
                });

        Plan plan;
        try {
            UUID planUuid = UUID.fromString(request.planId());
            plan = planRepository.findById(planUuid)
                    .orElseThrow(() -> new PlanNotFoundException(request.planId()));
        } catch (IllegalArgumentException e) {
            plan = planRepository.findByCode(request.planId().toUpperCase())
                    .orElseThrow(() -> new PlanNotFoundException(request.planId()));
        }

        LocalDateTime now = LocalDateTime.now();
        boolean isUs = "US".equalsIgnoreCase(request.country());

        String cardLast4 = isUs
                ? request.cardNumber().substring(request.cardNumber().length() - 4)
                : null;
        String cardExpiry = isUs ? request.expirationDate() : null;

        Subscription subscription = Subscription.builder()
                .userId(request.userId())
                .plan(plan)
                .status(isUs ? SubscriptionStatus.TRIAL : SubscriptionStatus.PENDING)
                .billingInterval(request.billingInterval())
                .trialStartedAt(isUs ? now : null)
                .trialEndsAt(isUs ? now.plusDays(plan.getTrialDays()) : null)
                .currentPeriodStart(isUs ? now : null)
                .currentPeriodEnd(isUs ? now.plusDays(plan.getTrialDays()) : null)
                .country(request.country())
                .cardLast4(cardLast4)
                .cardExpiry(cardExpiry)
                .build();

        subscription = subscriptionRepository.save(subscription);

        // Build gateway payload, then synchronously create profile (US) or Paddle checkout (non-US).
        // The checkoutUrl is returned in the response so the frontend can redirect immediately.
        Map<String, Object> gatewayPayload = new HashMap<>();
        gatewayPayload.put("userId", request.userId().toString());
        gatewayPayload.put("country", request.country());
        gatewayPayload.put("email", request.email());
        gatewayPayload.put("cardNumber", isUs ? request.cardNumber() : null);
        gatewayPayload.put("expirationDate", isUs ? request.expirationDate() : null);
        gatewayPayload.put("cardCode", isUs ? request.cardCode() : null);
        gatewayPayload.put("priceId", isUs ? null : plan.getPaddlePriceId());

        String checkoutUrl = outboxService.createCheckoutSync(subscription.getId(), gatewayPayload);

        if (plan.getTokenAllowance() > 0) {
            tokenLedgerRepository.save(TokenLedger.builder()
                    .userId(request.userId())
                    .type(TokenLedgerType.CREDIT)
                    .amount(plan.getTokenAllowance())
                    .balanceAfter(plan.getTokenAllowance())
                    .description("Plan allocation - " + plan.getCode())
                    .build());
            redisTemplate.opsForValue().set(
                    RedisKeys.tokenBalance(request.userId().toString()),
                    String.valueOf(plan.getTokenAllowance()));
        }

        return new CreateSubscriptionResponse(
                subscription.getId(),
                subscription.getStatus(),
                subscription.getTrialEndsAt(),
                plan.getTokenAllowance(),
                checkoutUrl);
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> getActivePlans() {
        return planRepository.findByActiveTrue().stream()
                .map(p -> new PlanResponse(
                        p.getId(), p.getCode(), p.getName(),
                        p.getPriceMonthly(), p.getPriceAnnual(),
                        p.isAiEnabled(), p.getMaxSurveysPerMonth(),
                        p.getTokenAllowance(), p.getTrialDays(),
                        p.getPaddlePriceId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getSubscription(UUID subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionId));

        LocalDateTime now = LocalDateTime.now();
        long daysRemaining = 0;
        if (subscription.getStatus() == SubscriptionStatus.TRIAL && subscription.getTrialEndsAt() != null) {
            daysRemaining = ChronoUnit.DAYS.between(now, subscription.getTrialEndsAt());
        } else if (subscription.getCurrentPeriodEnd() != null) {
            daysRemaining = ChronoUnit.DAYS.between(now, subscription.getCurrentPeriodEnd());
        }

        long tokenBalance = getTokenBalance(subscription.getUserId());

        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getStatus(),
                subscription.getPlan().getCode(),
                subscription.getBillingInterval(),
                subscription.getTrialEndsAt(),
                subscription.getCurrentPeriodEnd(),
                daysRemaining,
                tokenBalance,
                subscription.getPaddleCheckoutUrl());
    }

    @Transactional
    public CancelSubscriptionResponse cancelSubscription(UUID subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionId));

        if (subscription.getStatus() == SubscriptionStatus.CANCELLED) {
            throw new AlreadyCancelledException(subscriptionId);
        }

        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setCancelledAt(LocalDateTime.now());
        subscriptionRepository.save(subscription);

        String profileId = subscription.getCustomerProfileId() != null
                ? subscription.getCustomerProfileId()
                : subscription.getPaddleSubscriptionId();

        if (profileId != null) {
            outboxService.saveEvent("SUBSCRIPTION", subscription.getId(),
                    "SUBSCRIPTION_CANCELLED",
                    new CancelProfileRequest(profileId, subscription.getCountry()));
        }

        return new CancelSubscriptionResponse(
                subscription.getId(),
                subscription.getStatus(),
                subscription.getCancelledAt());
    }

    public TokenDeductionResponse deductTokens(UUID userId, long amount, String description) {
        String tokenKey = RedisKeys.tokenBalance(userId.toString());
        Long newBalance;
        try {
            newBalance = redisTemplate.opsForValue().decrement(tokenKey, amount);
        } catch (Exception e) {
            log.error("Redis unavailable during token deduction for userId={}: {}", userId, e.getMessage(), e);
            throw new PaymentException("Token service temporarily unavailable");
        }

        if (newBalance == null || newBalance < 0) {
            redisTemplate.opsForValue().increment(tokenKey, amount);
            throw new InsufficientTokensException(userId.toString());
        }

        tokenLedgerService.logDebitAsync(userId, amount, newBalance, description);

        return new TokenDeductionResponse(userId, amount, newBalance);
    }

    @Transactional
    public void updateStatusByPaddleSubscriptionId(String paddleSubscriptionId, String status) {
        Subscription subscription = subscriptionRepository.findByPaddleSubscriptionId(paddleSubscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException(
                        UUID.fromString("00000000-0000-0000-0000-000000000000")));

        SubscriptionStatus newStatus;
        try {
            newStatus = SubscriptionStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown subscription status: " + status);
        }

        subscription.setStatus(newStatus);
        if (newStatus == SubscriptionStatus.ACTIVE) {
            LocalDateTime now = LocalDateTime.now();
            subscription.setCurrentPeriodStart(now);
            subscription.setCurrentPeriodEnd(now.plusDays(30));
        } else if (newStatus == SubscriptionStatus.CANCELLED) {
            subscription.setCancelledAt(LocalDateTime.now());
        }
        subscriptionRepository.save(subscription);
        log.info("Subscription {} (paddle={}) status updated to {}", subscription.getId(), paddleSubscriptionId, newStatus);
    }

    private long getTokenBalance(UUID userId) {
        String tokenKey = RedisKeys.tokenBalance(userId.toString());
        try {
            String value = redisTemplate.opsForValue().get(tokenKey);
            if (value != null) {
                return Long.parseLong(value);
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for token balance userId={}, falling back to DB", userId);
        }
        List<TokenLedger> entries = tokenLedgerRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return entries.isEmpty() ? 0L : entries.get(0).getBalanceAfter();
    }
}
