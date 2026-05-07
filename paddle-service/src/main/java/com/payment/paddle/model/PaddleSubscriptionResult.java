package com.payment.paddle.model;

public record PaddleSubscriptionResult(
        String paddleSubscriptionId,
        String status,
        String nextBilledAt) {}
