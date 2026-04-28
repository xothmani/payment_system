package com.payment.subscription.model.dto;

import com.payment.subscription.model.SubscriptionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record CreateSubscriptionResponse(
        UUID subscriptionId,
        SubscriptionStatus status,
        LocalDateTime trialEndsAt,
        long tokenBalance
) {
}
