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
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final TokenLedgerRepository tokenLedgerRepository;
    private final TokenLedgerService tokenLedgerService;
    private final RedisTemplate<String, String> redisTemplate;
    private final RestClient paymentRouterRestClient;

    @Transactional
    public CreateSubscriptionResponse createSubscription(CreateSubscriptionRequest request) {
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

        String customerProfileId = null;
        String paymentProfileId = null;

        record ProfileRouteReq(String userId, String country, String email,
                               String cardNumber, String expirationDate, String cardCode) {}
        record ProfileRouteRes(String customerProfileId, String paymentProfileId,
                               String paddleSubscriptionId) {}

        try {
            ProfileRouteRes routerRes = paymentRouterRestClient
                    .post()
                    .uri("/profiles")
                    .body(new ProfileRouteReq(
                            request.userId().toString(), request.country(), request.email(),
                            request.cardNumber(), request.expirationDate(), request.cardCode()))
                    .retrieve()
                    .body(ProfileRouteRes.class);

            if (routerRes != null) {
                customerProfileId = routerRes.customerProfileId();
                paymentProfileId = routerRes.paymentProfileId();
            }
        } catch (Exception e) {
            log.error("Failed to route profile creation for userId={}: {}", request.userId(), e.getMessage(), e);
            throw new PaymentException("Failed to create payment profile: " + e.getMessage(), e);
        }

        LocalDateTime now = LocalDateTime.now();
        String cardLast4 = request.cardNumber().substring(request.cardNumber().length() - 4);

        Subscription subscription = Subscription.builder()
                .userId(request.userId())
                .plan(plan)
                .status(SubscriptionStatus.TRIAL)
                .billingInterval(request.billingInterval())
                .trialStartedAt(now)
                .trialEndsAt(now.plusDays(plan.getTrialDays()))
                .currentPeriodStart(now)
                .currentPeriodEnd(now.plusDays(plan.getTrialDays()))
                .customerProfileId(customerProfileId)
                .paymentProfileId(paymentProfileId)
                .country(request.country())
                .cardLast4(cardLast4)
                .cardExpiry(request.expirationDate())
                .build();

        subscription = subscriptionRepository.save(subscription);

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
                plan.getTokenAllowance());
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getSubscription(UUID subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionId));

        LocalDateTime now = LocalDateTime.now();
        long daysRemaining = subscription.getStatus() == SubscriptionStatus.TRIAL
                ? ChronoUnit.DAYS.between(now, subscription.getTrialEndsAt())
                : ChronoUnit.DAYS.between(now, subscription.getCurrentPeriodEnd());

        long tokenBalance = getTokenBalance(subscription.getUserId());

        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getStatus(),
                subscription.getPlan().getCode(),
                subscription.getBillingInterval(),
                subscription.getTrialEndsAt(),
                subscription.getCurrentPeriodEnd(),
                daysRemaining,
                tokenBalance);
    }

    @Transactional
    public CancelSubscriptionResponse cancelSubscription(UUID subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionId));

        if (subscription.getStatus() == SubscriptionStatus.CANCELLED) {
            throw new AlreadyCancelledException(subscriptionId);
        }

        if (subscription.getCustomerProfileId() != null) {
            try {
                paymentRouterRestClient
                        .delete()
                        .uri("/profiles/{id}?country={country}",
                                subscription.getCustomerProfileId(),
                                subscription.getCountry())
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception e) {
                log.error("Failed to route profile deletion for subscription={}: {}",
                        subscriptionId, e.getMessage(), e);
            }
        }

        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setCancelledAt(LocalDateTime.now());
        subscriptionRepository.save(subscription);

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
