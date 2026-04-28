package com.payment.subscription.exception;

import java.util.UUID;

public class SubscriptionAlreadyExistsException extends RuntimeException {
    public SubscriptionAlreadyExistsException(UUID userId) {
        super("Active or trial subscription already exists for user: " + userId);
    }
}
