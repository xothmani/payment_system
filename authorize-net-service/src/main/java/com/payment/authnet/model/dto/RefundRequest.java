package com.payment.authnet.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record RefundRequest(
        @NotBlank String transactionId,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String cardLast4,
        @NotBlank String cardExpiry
) {
}
