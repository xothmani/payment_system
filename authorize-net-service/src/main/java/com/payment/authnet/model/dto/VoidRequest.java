package com.payment.authnet.model.dto;

import jakarta.validation.constraints.NotBlank;

public record VoidRequest(@NotBlank String transactionId) {
}
