package com.payment.subscription.exception;

public class InsufficientTokensException extends PaymentException {
    public InsufficientTokensException() {
        super("Insufficient token balance");
    }

    public InsufficientTokensException(String userId) {
        super("Insufficient token balance for user: " + userId);
    }
}
