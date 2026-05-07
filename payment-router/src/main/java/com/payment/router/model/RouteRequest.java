package com.payment.router.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RouteRequest {

    @NotBlank
    private String userId;

    @NotBlank
    private String idempotencyKey;

    @NotBlank
    private String country;

    private BigDecimal amount;
    private String currency;

    // Authorize.net CIM profile IDs (US users)
    private String customerProfileId;
    private String paymentProfileId;

    // Paddle subscription ID (non-US users)
    private String paddleSubscriptionId;
}
