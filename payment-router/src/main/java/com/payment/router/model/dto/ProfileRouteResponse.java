package com.payment.router.model.dto;

public record ProfileRouteResponse(
        String customerProfileId,
        String paymentProfileId,
        String paddleSubscriptionId
) {
}
