package com.payment.authnet.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ChargeProfileRequest(
        @NotBlank String customerProfileId,
        @NotBlank String paymentProfileId,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String idempotencyKey
) {
}
