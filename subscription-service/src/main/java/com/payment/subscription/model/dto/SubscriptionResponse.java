package com.payment.subscription.model.dto;

import com.payment.subscription.model.BillingInterval;
import com.payment.subscription.model.SubscriptionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record SubscriptionResponse(
        UUID subscriptionId,
        SubscriptionStatus status,
        String planCode,
        BillingInterval billingInterval,
        LocalDateTime trialEndsAt,
        LocalDateTime currentPeriodEnd,
        long daysRemaining,
        long tokenBalance
) {
}
