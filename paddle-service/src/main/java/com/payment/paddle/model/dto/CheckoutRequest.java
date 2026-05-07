package com.payment.paddle.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CheckoutRequest(
        @NotBlank String priceId,
        @NotBlank String userId,
        @NotBlank @Email String email
) {
}
