package com.payment.subscription.model.dto;

import com.payment.subscription.model.SubscriptionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record CancelSubscriptionResponse(
        UUID subscriptionId,
        SubscriptionStatus status,
        LocalDateTime cancelledAt
) {
}
