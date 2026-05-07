package com.payment.subscription.model.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PlanResponse(
        UUID id,
        String code,
        String name,
        BigDecimal priceMonthly,
        BigDecimal priceAnnual,
        boolean aiEnabled,
        int maxSurveysPerMonth,
        long tokenAllowance,
        int trialDays,
        String paddlePriceId
) {
}
