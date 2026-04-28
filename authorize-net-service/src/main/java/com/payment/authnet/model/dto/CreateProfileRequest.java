package com.payment.authnet.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateProfileRequest(
        @NotBlank String userId,
        @NotBlank @Email String email,
        @NotBlank String cardNumber,
        @NotBlank String expirationDate,
        @NotBlank String cardCode
) {
}
