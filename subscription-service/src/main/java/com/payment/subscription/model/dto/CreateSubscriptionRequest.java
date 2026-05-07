package com.payment.subscription.model.dto;

import com.payment.subscription.model.BillingInterval;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateSubscriptionRequest(
        @NotNull UUID userId,
        @NotBlank String planId,
        @NotBlank String country,
        String cardNumber,
        String expirationDate,
        String cardCode,
        @NotBlank @Email String email,
        @NotNull BillingInterval billingInterval
) {
    @AssertTrue(message = "Card details required for US users")
    private boolean isCardDetailsValid() {
        if ("US".equalsIgnoreCase(country)) {
            return cardNumber != null && !cardNumber.isBlank()
                    && expirationDate != null && !expirationDate.isBlank()
                    && cardCode != null && !cardCode.isBlank();
        }
        return true;
    }
}