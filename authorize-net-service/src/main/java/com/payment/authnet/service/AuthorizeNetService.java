package com.payment.authnet.service;

import com.payment.authnet.exception.AuthorizeNetException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Service;

@Service
public class AuthorizeNetService {

    // TODO: implement 6 features:
    // 1. One-time credit card charge (authCaptureTransaction)
    // 2. Recurring / ARB subscriptions (create, update, cancel)
    // 3. Refund and void
    // 4. Customer profile + CIM tokenization
    // 5. eCheck / ACH payments
    // 6. Apple Pay and Google Pay (Accept.js)

    @CircuitBreaker(name = "gatewayCircuitBreaker", fallbackMethod = "fallback")
    @Retry(name = "gatewayRetry")
    public Object processPayment(Object request) {
        // TODO: implement
        return null;
    }

    public Object fallback(Object request, Throwable t) {
        throw new AuthorizeNetException(503, "Authorize.net gateway unavailable: " + t.getMessage());
    }
}
