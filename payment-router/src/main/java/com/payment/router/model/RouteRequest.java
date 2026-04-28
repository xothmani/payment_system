package com.payment.router.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RouteRequest {

    @NotBlank
    private String userId;

    @NotBlank
    private String idempotencyKey;

    @NotBlank
    private String country;

    private java.math.BigDecimal amount;
    private String currency;
}
