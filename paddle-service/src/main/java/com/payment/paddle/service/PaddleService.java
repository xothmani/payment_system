package com.payment.paddle.service;

import com.payment.paddle.exception.PaddleException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Service;

@Service
public class PaddleService {

    // TODO: implement Paddle Billing API operations:
    // - Subscription create, update, cancel
    // - Refunds and cancellations
    // - Tax/VAT handled by Paddle globally

    @CircuitBreaker(name = "gatewayCircuitBreaker", fallbackMethod = "fallback")
    @Retry(name = "gatewayRetry")
    public Object processPayment(Object request) {
        // TODO: implement
        return null;
    }

    public Object fallback(Object request, Throwable t) {
        throw new PaddleException("Paddle gateway unavailable: " + t.getMessage(), t);
    }
}
