package com.payment.router.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ProfileRouteRequest(
        @NotBlank String userId,
        @NotBlank String country,
        @NotBlank @Email String email,
        String cardNumber,
        String expirationDate,
        String cardCode,
        String priceId
) {
}
