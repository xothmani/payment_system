package com.payment.subscription.model.dto;

import java.util.UUID;

public record TokenDeductionResponse(UUID userId, long tokensUsed, long remainingBalance) {
}
