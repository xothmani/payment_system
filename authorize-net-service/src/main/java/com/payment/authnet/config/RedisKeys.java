package com.payment.authnet.config;

public final class RedisKeys {

    private RedisKeys() {}

    /** Idempotency key for charge requests. TTL: 24h. Check before every charge. */
    public static String idempotency(String transactionId) {
        return "idem:" + transactionId;
    }
}
