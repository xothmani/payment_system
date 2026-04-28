package com.payment.subscription.model.dto;

import com.payment.subscription.model.BillingInterval;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateSubscriptionRequest(
        @NotNull UUID userId,
        @NotBlank String planId,
        @NotBlank String country,
        @NotBlank String cardNumber,
        @NotBlank String expirationDate,
        @NotBlank String cardCode,
        @NotBlank @Email String email,
        @NotNull BillingInterval billingInterval
) {
}
