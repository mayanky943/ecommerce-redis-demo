package com.learn.ecom.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Pattern 8: IDEMPOTENCY KEY.
 *
 *   SET idem:{key} <orderId> NX EX 900   — "claim" this key for 15 min
 *
 *   - returns true on first call: caller proceeds, persists the order
 *   - returns false on retry: caller looks up the orderId stored for the key
 *     and returns the same order — no duplicate side-effect.
 *
 * Pairs naturally with @Indexed(unique = true) on Order.idempotencyKey in Mongo
 * as a defense-in-depth (Redis is fast path, Mongo unique index is correctness).
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final Duration TTL = Duration.ofMinutes(15);
    private final StringRedisTemplate redis;

    public boolean tryClaim(String key, String orderId) {
        Boolean ok = redis.opsForValue().setIfAbsent("idem:" + key, orderId, TTL);
        return Boolean.TRUE.equals(ok);
    }

    public String getExistingOrderId(String key) {
        return redis.opsForValue().get("idem:" + key);
    }
}
