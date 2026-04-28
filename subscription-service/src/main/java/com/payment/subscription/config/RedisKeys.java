package com.payment.subscription.config;

public final class RedisKeys {

    private RedisKeys() {}

    /** Token balance. TTL: none. Deduct with atomic DECRBY only. */
    public static String tokenBalance(String userId) {
        return "tokens:" + userId;
    }

    /** Cached plan definition. TTL: 1h. Invalidate on admin update. */
    public static String planCache(String planId) {
        return "plan:" + planId;
    }

    /** Distributed lock for freemium auto-charge. TTL: 60s. SET NX EX pattern. */
    public static String chargeLock(String userId) {
        return "lock:charge:" + userId;
    }
}
