package com.payment.subscription.exception;

import java.util.UUID;

public class AlreadyCancelledException extends RuntimeException {
    public AlreadyCancelledException(UUID subscriptionId) {
        super("Subscription is already cancelled: " + subscriptionId);
    }
}
