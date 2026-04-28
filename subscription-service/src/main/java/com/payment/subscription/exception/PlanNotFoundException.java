package com.payment.subscription.exception;

import java.util.UUID;

public class PlanNotFoundException extends RuntimeException {
    public PlanNotFoundException(UUID planId) {
        super("Plan not found: " + planId);
    }

    public PlanNotFoundException(String planIdOrCode) {
        super("Plan not found: " + planIdOrCode);
    }
}
